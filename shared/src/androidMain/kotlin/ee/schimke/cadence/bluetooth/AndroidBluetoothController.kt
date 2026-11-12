package ee.schimke.cadence.bluetooth

import android.Manifest
import android.app.Activity
import android.app.Application
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothLeAudio
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController as PlatformMediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import ee.schimke.cadence.platform.Analytics
import ee.schimke.cadence.podcasts.AndroidPodcastPlatform
import ee.schimke.cadence.podcasts.LocalPodcastState
import ee.schimke.cadence.podcasts.PodcastPlaybackService
import ee.schimke.cadence.podcasts.PodcastPlayerUi
import ee.schimke.cadence.podcasts.PodcastSourceKind
import ee.schimke.cadence.podcasts.allEpisodes
import ee.schimke.cadence.podcasts.isPocketCastsInstalled
import ee.schimke.cadence.podcasts.launchPodcastEpisode
import ee.schimke.cadence.podcasts.withLocalPodcastPlayer
import ee.schimke.cadence.sync.SyncRepo
import ee.schimke.rcsp.AndroidShokzOpenSwimTransport
import ee.schimke.rcsp.EqualizerPreset as RcspEqualizerPreset
import ee.schimke.rcsp.OpenSwimProProfile
import ee.schimke.rcsp.PlaybackOrder as RcspPlaybackOrder
import ee.schimke.rcsp.RcspCommand
import ee.schimke.rcsp.ShokzOpenSwimCodec
import ee.schimke.rcsp.ShokzOpenSwimController
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@ContributesBinding(AppScope::class, binding = binding<BluetoothController>())
@SingleIn(AppScope::class)
@Inject
@OptIn(ExperimentalCoroutinesApi::class)
class AndroidBluetoothController(
  private val applicationContext: Context,
  private val syncRepo: SyncRepo,
) : BluetoothController {

  private val audioManager =
    applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

  private val bluetoothManager: BluetoothManager? =
    applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

  private val mediaSessionManager: MediaSessionManager? =
    applicationContext.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager

  private val podcastController by lazy {
    AndroidPodcastPlatform.instance(applicationContext).controller()
  }

  private val prefs =
    applicationContext.getSharedPreferences(HeadsetModePrefsName, Context.MODE_PRIVATE)

  private val refreshTrigger = MutableStateFlow(0)
  private val deviceFilterFlow = MutableStateFlow(loadDeviceFilter())
  private val verifiedOpenSwimFlow = MutableStateFlow(verifiedOpenSwimAddresses(applicationContext))
  private val probedAddresses = ConcurrentHashMap.newKeySet<String>()

  /** Mode and settings the connected OpenSwim Pro acknowledged; none are read back. */
  private data class HeadsetSettings(
    val mode: WorkingMode = WorkingMode.Bluetooth,
    val equalizer: EqualizerPreset? = null,
    val playbackOrder: PlaybackOrder? = null,
    val volumePercent: Int? = null,
    val volumeError: Boolean = false,
    val playing: Boolean? = null,
  )

  private data class ConnectionSnapshot(
    val device: ConnectedDevice? = null,
    val hiddenHeadphoneName: String? = null,
  )

  private data class RawMediaSession(
    val candidate: MediaSessionCandidate,
    val controller: PlatformMediaController?,
    val localServiceFallback: Boolean = false,
  )

  private data class SourceContext(
    val podcastState: LocalPodcastState,
    val hasFolderSources: Boolean,
    val localPlayer: PodcastPlayerUi,
  )

  private val headsetFlow = MutableStateFlow(HeadsetSettings())
  private val commandScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

  @Volatile private var lastConnectedDevice: BluetoothDevice? = null
  @Volatile private var matchedMediaController: PlatformMediaController? = null
  @Volatile private var matchedLocalPodcastPlayer: Boolean = false

  override val supported = true

  private val sourceContextFlow: Flow<SourceContext> by lazy {
    combine(
      podcastController.state,
      syncRepo.sources.map { it.isNotEmpty() }.distinctUntilChanged(),
      PodcastPlaybackService.playerState,
    ) { podcasts, hasFolders, localPlayer ->
      SourceContext(podcasts, hasFolders, localPlayer)
    }
  }

  override val state: Flow<BluetoothState> =
    refreshTrigger
      .flatMapLatest {
        combine(
          connectionFlow(),
          volumeFlow(),
          rawMediaSessionsFlow(),
          headsetFlow,
          sourceContextFlow,
        ) { connection, volume, rawSessions, headset, sourceContext ->
          val filter = deviceFilterFlow.value
          val device = connection.device
          val localAudio = device?.isOpenSwimPro == true && headset.mode == WorkingMode.Mp3
          val btHeadphonesConnected = device != null && !localAudio

          val podcastState = sourceContext.podcastState
          val hasConnectedSource =
            podcastState.library.source != null || sourceContext.hasFolderSources
          val pocketCastsConnected =
            podcastState.library.source == PodcastSourceKind.PocketCasts ||
              podcastState.pocketCasts.let {
                it.server ||
                  it.accessToken.isNotBlank() ||
                  it.refreshToken.isNotBlank() ||
                  it.email.isNotBlank()
              }
          val allowedPackages =
            allowedMediaPackages(
              cadencePackage = applicationContext.packageName,
              podcastSource = podcastState.library.source,
              pocketCastsConnected = pocketCastsConnected,
            )

          val combinedSessions = buildList {
            addAll(rawSessions)
            val local = sourceContext.localPlayer
            if (
              local.title != null &&
                rawSessions.none { it.candidate.packageName == applicationContext.packageName }
            ) {
              add(
                RawMediaSession(
                  candidate =
                    MediaSessionCandidate(
                      packageName = applicationContext.packageName,
                      title = local.title,
                      artist = null,
                      album = null,
                      playing = local.playing,
                      durationMs = local.durationMs.takeIf { it > 0 },
                      positionMs = local.positionMs,
                    ),
                  controller = null,
                  localServiceFallback = true,
                ),
              )
            }
          }

          val pushedEpisodes = podcastState.library.allEpisodes
          val winningIndex =
            if (localAudio) null
            else
              selectControllableMediaSession(
                sessions = combinedSessions.map { it.candidate },
                cadencePackage = applicationContext.packageName,
                allowedPackages = allowedPackages,
                hasConnectedSource = hasConnectedSource,
                headphonesConnectedInBtMode = btHeadphonesConnected,
                pushedEpisodes = pushedEpisodes,
              )
          val winningSession = winningIndex?.let { combinedSessions[it] }
          matchedMediaController = winningSession?.controller
          matchedLocalPodcastPlayer = winningSession?.localServiceFallback == true

          val media =
            winningSession?.candidate?.let {
              MediaInfo(
                title = it.title,
                artist = it.artist,
                album = it.album,
                playing = it.playing,
                durationMs = it.durationMs,
                positionMs = it.positionMs,
                packageName = it.packageName,
              )
            }

          val pocketCastsInstalled = isPocketCastsInstalled(applicationContext)
          val playableEpisodes =
            podcastState.library.playlist
              ?.episodes
              .orEmpty()
              .mapNotNull { episode ->
                val pocketCastsPlayable = episode.backend == "pocket-casts" && pocketCastsInstalled
                val downloaded = episode.key in podcastState.downloadedEpisodeKeys
                when {
                  pocketCastsPlayable ->
                    PlayableEpisode(episode.key, episode.title, episode.podcastTitle, "Pocket Casts")
                  downloaded ->
                    PlayableEpisode(episode.key, episode.title, episode.podcastTitle, "On phone")
                  else -> null
                }
              }

          BluetoothState(
            connectedDevice = device,
            deviceFilter = filter,
            hiddenHeadphoneName = connection.hiddenHeadphoneName,
            volumePercent = volume.percent,
            maxVolume = volume.max,
            muted = volume.muted,
            mediaInfo = media,
            mediaAccessGranted = isNotificationAccessGranted(),
            permissionMissing = !hasBluetoothConnectPermission(),
            workingMode = headset.mode,
            equalizer = headset.equalizer,
            playbackOrder = headset.playbackOrder,
            headsetVolumePercent = headset.volumePercent,
            headsetVolumeError = headset.volumeError,
            headsetPlaying = headset.playing,
            hasConnectedSource = hasConnectedSource,
            playableEpisodes = playableEpisodes,
          )
        }
      }
      .distinctUntilChanged()

  init {
    (applicationContext as? Application)?.registerActivityLifecycleCallbacks(
      object : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) = refresh()

        override fun onActivityCreated(activity: Activity, state: android.os.Bundle?) = Unit

        override fun onActivityStarted(activity: Activity) = Unit

        override fun onActivityPaused(activity: Activity) = Unit

        override fun onActivityStopped(activity: Activity) = Unit

        override fun onActivitySaveInstanceState(activity: Activity, state: android.os.Bundle) =
          Unit

        override fun onActivityDestroyed(activity: Activity) = Unit
      },
    )
  }

  override fun refresh() {
    // Recreate the platform callback flows: notification-listener access, runtime Bluetooth
    // permissions, and RFCOMM availability for renamed headsets can change between resumes.
    probedAddresses.clear()
    refreshTrigger.update { it + 1 }
  }

  override fun setDeviceFilter(filter: BluetoothDeviceFilter) {
    if (deviceFilterFlow.value == filter) return
    prefs.edit().putString(DeviceFilterPrefKey, filter.name).apply()
    deviceFilterFlow.value = filter
  }

  override fun setVolume(percent: Int) {
    val target = percent.coerceIn(0, 100)
    localAudioDevice()?.let { device ->
      // MP3 playback is on the headset, so the phone's stream volume does not reach it.
      val previous = headsetFlow.value.volumePercent
      headsetFlow.update { it.copy(volumePercent = target, volumeError = false) }
      dispatchOpenSwim(
        device,
        RcspCommand.SetVolume(target.toUByte()),
        onFailure = {
          headsetFlow.update {
            if (it.volumePercent == target) it.copy(volumePercent = previous, volumeError = true)
            else it
          }
        },
      )
      return
    }
    val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    val streamLevel = (target * max / 100).coerceIn(0, max)
    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, streamLevel, AudioManager.FLAG_SHOW_UI)
    refresh()
  }

  override fun adjustVolume(delta: Int) {
    if (localAudioDevice() != null) {
      // Only relative to a level the headset acknowledged; there is no verified volume query.
      headsetFlow.value.volumePercent?.let { setVolume(it + delta * HeadsetVolumeStep) }
      return
    }
    val direction =
      when {
        delta > 0 -> AudioManager.ADJUST_RAISE
        delta < 0 -> AudioManager.ADJUST_LOWER
        else -> AudioManager.ADJUST_SAME
      }
    audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
    refresh()
  }

  override fun toggleMute() {
    audioManager.adjustStreamVolume(
      AudioManager.STREAM_MUSIC,
      AudioManager.ADJUST_TOGGLE_MUTE,
      AudioManager.FLAG_SHOW_UI,
    )
    refresh()
  }

  override fun play() {
    localAudioDevice()?.let { device ->
      dispatchOpenSwim(device, RcspCommand.Play) {
        headsetFlow.update { it.copy(playing = true) }
      }
      return
    }
    matchedMediaController?.let { controller ->
      controller.transportControls.play()
      return
    }
    if (matchedLocalPodcastPlayer) {
      withLocalPodcastPlayer(applicationContext) { it.play() }
      return
    }
    launchFirstPlayableEpisode()
  }

  override fun pause() {
    localAudioDevice()?.let { device ->
      dispatchOpenSwim(device, RcspCommand.Pause) {
        headsetFlow.update { it.copy(playing = false) }
      }
      return
    }
    matchedMediaController?.let { controller ->
      controller.transportControls.pause()
      return
    }
    if (matchedLocalPodcastPlayer) {
      withLocalPodcastPlayer(applicationContext) { it.pause() }
    }
  }

  /** MP3 mode has no captured toggle, so it follows the last acknowledged Play/Pause. */
  override fun playPause() {
    if (localAudioDevice() != null) {
      if (headsetFlow.value.playing == true) pause() else play()
      return
    }
    matchedMediaController?.let { controller ->
      if (controller.playbackState?.state == PlaybackState.STATE_PLAYING) {
        controller.transportControls.pause()
      } else {
        controller.transportControls.play()
      }
      return
    }
    if (matchedLocalPodcastPlayer) {
      withLocalPodcastPlayer(applicationContext) {
        if (it.isPlaying) it.pause() else it.play()
      }
      return
    }
    launchFirstPlayableEpisode()
  }

  override fun playEpisode(episodeKey: String) {
    val podcastState = podcastController.state.value
    val episode =
      podcastState.library.allEpisodes.firstOrNull { it.key == episodeKey } ?: return
    launchPodcastEpisode(applicationContext, episode, podcastController)
  }

  override fun next() {
    localAudioDevice()?.let { device ->
      dispatchOpenSwim(device, RcspCommand.NextTrack)
      return
    }
    matchedMediaController?.transportControls?.skipToNext()
  }

  override fun previous() {
    localAudioDevice()?.let { device ->
      dispatchOpenSwim(device, RcspCommand.PreviousTrack)
      return
    }
    matchedMediaController?.transportControls?.skipToPrevious()
  }

  override fun stop() {
    if (localAudioDevice() != null) return
    matchedMediaController?.transportControls?.stop()
    if (matchedLocalPodcastPlayer) {
      withLocalPodcastPlayer(applicationContext) { it.stop() }
    }
  }

  override fun fastForward() {
    if (localAudioDevice() != null) return
    matchedMediaController?.transportControls?.fastForward()
  }

  override fun rewind() {
    if (localAudioDevice() != null) return
    matchedMediaController?.transportControls?.rewind()
  }

  private fun launchFirstPlayableEpisode() {
    val podcastState = podcastController.state.value
    val pocketCastsInstalled = isPocketCastsInstalled(applicationContext)
    val episode =
      podcastState.library.playlist?.episodes.orEmpty().firstOrNull { ep ->
        (ep.backend == "pocket-casts" && pocketCastsInstalled) ||
          ep.key in podcastState.downloadedEpisodeKeys
      } ?: return
    launchPodcastEpisode(applicationContext, episode, podcastController)
  }

  override fun openSystemBluetoothSettings() {
    val intent =
      Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    runCatching { applicationContext.startActivity(intent) }
  }

  override fun openAppPermissionSettings() {
    val intent =
      Intent(
          Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
          Uri.fromParts("package", applicationContext.packageName, null),
        )
        .apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    runCatching { applicationContext.startActivity(intent) }
  }

  override fun requestMediaAccess() {
    val intent =
      Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
    runCatching { applicationContext.startActivity(intent) }
  }

  override suspend fun setWorkingMode(mode: WorkingMode): String {
    val device = openSwimDevice() ?: return "Connect an OpenSwim Pro first"
    val command =
      RcspCommand.SelectWorkingMode(
        when (mode) {
          WorkingMode.Mp3 -> ee.schimke.rcsp.WorkingMode.LocalAudio
          WorkingMode.Bluetooth -> ee.schimke.rcsp.WorkingMode.Bluetooth
        },
      )
    return withContext(Dispatchers.IO) {
      runCatching { exchangeOpenSwim(device, command) }
        .fold(
          onSuccess = {
            prefs
              .edit()
              .putString("device", device.address)
              .putString("mode", mode.name)
              .apply()
            headsetFlow.update { it.copy(mode = mode, playing = null) }
            Analytics.current.event("headphone_mode", mapOf("mode" to mode.name))
            refresh()
            when (mode) {
              WorkingMode.Mp3 -> "OpenSwim Pro switched to MP3 Mode"
              WorkingMode.Bluetooth -> "OpenSwim Pro switched to Bluetooth Mode"
            }
          },
          onFailure = { error ->
            Log.w("CadenceBluetooth", "OpenSwim mode switch failed", error)
            "OpenSwim Pro mode switch failed: ${error.message ?: "unknown error"}"
          },
        )
    }
  }

  override suspend fun dispatchAdvanced(command: AdvancedCommand): String =
    when (command) {
      AdvancedCommand.QueryBattery -> batteryFromConnected() ?: "Unknown — connect a device"
      AdvancedCommand.QueryLocalAudioState -> queryOpenSwimLocalAudioState()
      AdvancedCommand.EqFlat -> setEqualizer(EqualizerPreset.Standard)
      AdvancedCommand.EqVocal -> setEqualizer(EqualizerPreset.Vocal)
      AdvancedCommand.ToggleSwimmingMode -> setEqualizer(EqualizerPreset.Swimming)
      else -> "Preview only — firmware support not wired up yet"
    }

  override suspend fun setEqualizer(preset: EqualizerPreset): String {
    val device = openSwimDevice() ?: return "Connect an OpenSwim Pro first"
    val command =
      RcspCommand.SelectEqualizerPreset(
        when (preset) {
          EqualizerPreset.Standard -> RcspEqualizerPreset.Standard
          EqualizerPreset.Vocal -> RcspEqualizerPreset.Vocal
          EqualizerPreset.Swimming -> RcspEqualizerPreset.Swimming
        },
      )
    return executeOpenSwimCommand(device, command) {
      headsetFlow.update { it.copy(equalizer = preset) }
    }
  }

  override suspend fun setPlaybackOrder(order: PlaybackOrder): String {
    if (openSwimDevice() == null) return "Connect an OpenSwim Pro first"
    val device = localAudioDevice() ?: return "Switch to MP3 mode first"
    val command =
      RcspCommand.SelectPlaybackOrder(
        when (order) {
          PlaybackOrder.Normal -> RcspPlaybackOrder.Normal
          PlaybackOrder.Shuffle -> RcspPlaybackOrder.Shuffle
          PlaybackOrder.Repeat -> RcspPlaybackOrder.Repeat
        },
      )
    return executeOpenSwimCommand(device, command) {
      headsetFlow.update { it.copy(playbackOrder = order) }
    }
  }

  private suspend fun executeOpenSwimCommand(
    device: BluetoothDevice,
    command: RcspCommand,
    onAcknowledged: () -> Unit = {},
  ): String =
    withContext(Dispatchers.IO) {
      runCatching { exchangeOpenSwim(device, command) }
        .fold(
          onSuccess = {
            onAcknowledged()
            "Acknowledged"
          },
          onFailure = { error ->
            Log.w("CadenceBluetooth", "OpenSwim ${command.id} failed", error)
            "Failed: ${error.message ?: "unknown error"}"
          },
        )
    }

  private suspend fun queryOpenSwimLocalAudioState(): String {
    if (openSwimDevice() == null) return "Connect an OpenSwim Pro first"
    val device = localAudioDevice() ?: return "Select Local Audio first"
    return withContext(Dispatchers.IO) {
      runCatching {
          val socket =
            device.createRfcommSocketToServiceRecord(
              UUID.fromString(OpenSwimProProfile.serviceUuid),
            )
          socket.use {
            it.connect()
            ShokzOpenSwimController(AndroidShokzOpenSwimTransport(it))
              .execute(RcspCommand.QueryMediaState)
          }
        }
        .fold(
          onSuccess = { response ->
            Log.i(
              "CadenceBluetooth",
              "OpenSwim Local Audio state acknowledged with type ${response.type}",
            )
            "Local Audio state acknowledged"
          },
          onFailure = { error ->
            Log.w("CadenceBluetooth", "OpenSwim Local Audio state query failed", error)
            "Local Audio state query failed: ${error.message ?: "unknown error"}"
          },
        )
    }
  }

  private fun batteryFromConnected(): String? {
    val device = connectedAudioDevice() ?: return null
    val verified = device.address in verifiedOpenSwimFlow.value
    return batteryPercent(device)?.let {
      ConnectedDevice(
          name = runCatching { device.name }.getOrNull().orEmpty(),
          address = device.address.orEmpty(),
          profileNames = emptyList(),
          batteryPercent = it,
          verifiedOpenSwim = verified,
        )
        .batterySummary()
    }
  }

  private fun batteryPercent(device: BluetoothDevice): Int? {
    if (!hasBluetoothConnectPermission()) return null
    return runCatching {
      val method = BluetoothDevice::class.java.getMethod("getBatteryLevel")
      val level = method.invoke(device) as? Int
      level?.takeIf { it in 0..100 }
    }
      .getOrNull()
  }

  private fun connectedAudioDevice(): BluetoothDevice? {
    if (!hasBluetoothConnectPermission()) return null
    return lastConnectedDevice
  }

  private fun openSwimDevice(): BluetoothDevice? =
    connectedAudioDevice()?.takeIf { device ->
      device.address in verifiedOpenSwimFlow.value ||
        runCatching { device.name }.getOrNull()?.contains(OpenSwimProName, ignoreCase = true) ==
          true
    }

  /** The OpenSwim Pro, when Cadence has switched it to on-device (MP3) playback. */
  private fun localAudioDevice(): BluetoothDevice? =
    openSwimDevice()?.takeIf { headsetFlow.value.mode == WorkingMode.Mp3 }

  private fun dispatchOpenSwim(
    device: BluetoothDevice,
    command: RcspCommand,
    onFailure: () -> Unit = {},
    onAcknowledged: () -> Unit = {},
  ) {
    commandScope.launch {
      runCatching { exchangeOpenSwim(device, command) }
        .onSuccess { response ->
          onAcknowledged()
          Log.i(
            "CadenceBluetooth",
            "OpenSwim ${command.id} acknowledged with type ${response.type}",
          )
        }
        .onFailure { error ->
          onFailure()
          Log.w("CadenceBluetooth", "OpenSwim ${command.id} failed", error)
        }
      // The acknowledged settings flow and platform callbacks already publish changes.
      // Restarting the observers here briefly reports the headset as disconnected.
    }
  }

  private suspend fun exchangeOpenSwim(
    device: BluetoothDevice,
    command: RcspCommand,
  ) =
    device.createRfcommSocketToServiceRecord(UUID.fromString(OpenSwimProProfile.serviceUuid)).use {
      socket ->
      socket.connect()
      ShokzOpenSwimController(AndroidShokzOpenSwimTransport(socket)).execute(command)
    }

  /**
   * Read-only `QueryMediaState` check for a renamed `0xFEF0` audio sink, confirming it speaks the
   * OpenSwim Pro `A5 5A` envelope before any vendor write command is enabled.
   */
  private fun probeOpenSwimIfNeeded(device: BluetoothDevice) {
    val address = device.address?.takeIf { it.isNotBlank() } ?: return
    if (address in verifiedOpenSwimFlow.value) return
    if (!probedAddresses.add(address)) return
    commandScope.launch {
      runCatching {
          val socket =
            device.createRfcommSocketToServiceRecord(
              UUID.fromString(OpenSwimProProfile.serviceUuid),
            )
          val timeout = launch {
            delay(OpenSwimProbeTimeoutMs)
            runCatching { socket.close() }
          }
          try {
            socket.use {
              it.connect()
              ShokzOpenSwimController(AndroidShokzOpenSwimTransport(it))
                .execute(RcspCommand.QueryMediaState)
            }
          } finally {
            timeout.cancel()
          }
        }
        .onSuccess { response ->
          if (ShokzOpenSwimCodec.isResponse(response)) {
            recordVerifiedOpenSwim(address)
            Log.i("CadenceBluetooth", "Verified OpenSwim protocol for renamed device $address")
          }
        }
        .onFailure { error ->
          Log.d("CadenceBluetooth", "RCSP probe did not verify $address: ${error.message}")
        }
    }
  }

  private fun recordVerifiedOpenSwim(address: String) {
    val updated = verifiedOpenSwimFlow.value + address
    prefs.edit().putStringSet(VerifiedOpenSwimPrefKey, updated).apply()
    verifiedOpenSwimFlow.value = updated
  }

  private fun loadDeviceFilter(): BluetoothDeviceFilter {
    val stored = prefs.getString(DeviceFilterPrefKey, null)
    return BluetoothDeviceFilter.entries.firstOrNull { it.name == stored }
      ?: BluetoothDeviceFilter.ShokzOnly
  }

  private fun hasBluetoothConnectPermission(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    return ContextCompat.checkSelfPermission(
      applicationContext,
      Manifest.permission.BLUETOOTH_CONNECT,
    ) == PackageManager.PERMISSION_GRANTED
  }

  private fun isNotificationAccessGranted(): Boolean {
    val flat =
      Settings.Secure.getString(
        applicationContext.contentResolver,
        "enabled_notification_listeners",
      ) ?: return false
    val cn = ComponentName(applicationContext, NowPlayingNotificationListener::class.java)
    return flat.split(":").any { it.equals(cn.flattenToString(), ignoreCase = true) }
  }

  private fun connectionFlow(): Flow<ConnectionSnapshot> = callbackFlow {
    val adapter = bluetoothManager?.adapter
    val aclDevices = mutableMapOf<String, BluetoothDevice>()
    val profileBroadcastDevices = mutableMapOf<Pair<String, String>, BluetoothDevice>()
    var a2dpProxy: BluetoothProfile? = null
    var headsetProxy: BluetoothProfile? = null
    var leAudioProxy: BluetoothProfile? = null

    fun evaluate() {
      if (!hasBluetoothConnectPermission() || adapter?.state == BluetoothAdapter.STATE_OFF) {
        aclDevices.clear()
        profileBroadcastDevices.clear()
        lastConnectedDevice = null
        headsetFlow.update { HeadsetSettings(mode = it.mode) }
        trySend(ConnectionSnapshot())
        return
      }

      val devicesByAddress = linkedMapOf<String, BluetoothDevice>()
      val profilesByAddress = mutableMapOf<String, MutableSet<String>>()

      fun record(device: BluetoothDevice, profile: String?) {
        val address = device.address?.takeIf { it.isNotBlank() } ?: return
        devicesByAddress[address] = device
        val set = profilesByAddress.getOrPut(address) { mutableSetOf() }
        if (profile != null) set += profile
      }

      runCatching { a2dpProxy?.connectedDevices.orEmpty() }
        .getOrDefault(emptyList())
        .forEach { record(it, "A2DP") }
      runCatching { headsetProxy?.connectedDevices.orEmpty() }
        .getOrDefault(emptyList())
        .forEach { record(it, "HFP") }
      runCatching { leAudioProxy?.connectedDevices.orEmpty() }
        .getOrDefault(emptyList())
        .forEach { record(it, "LE Audio") }

      profileBroadcastDevices.forEach { (key, device) -> record(device, key.second) }

      val verifiedSet = verifiedOpenSwimFlow.value
      aclDevices.values.forEach { device ->
        val candidate = device.toBluetoothCandidate(verifiedOpenSwim = device.address in verifiedSet)
        if (candidate.isOpenSwimPro || candidate.hasRcspProtocol) {
          record(device, null)
        }
      }

      val candidatesByAddress =
        devicesByAddress.mapValues { (address, device) ->
          device.toBluetoothCandidate(
            connectedProfiles = profilesByAddress[address].orEmpty(),
            verifiedOpenSwim = address in verifiedSet,
          )
        }

      val selection =
        selectConnectedBluetoothDevice(candidatesByAddress.values, deviceFilterFlow.value)
      val winningCandidate = selection.selected
      val winningDevice = winningCandidate?.let { devicesByAddress[it.address] }

      if (winningDevice == null) {
        lastConnectedDevice = null
        // Acknowledged settings belong to that connection; the mode selection is kept.
        headsetFlow.update { HeadsetSettings(mode = it.mode) }
        trySend(
          ConnectionSnapshot(
            device = null,
            hiddenHeadphoneName = selection.hiddenHeadphone?.name?.ifBlank { null },
          ),
        )
        return
      }

      if (winningCandidate.needsOpenSwimProbe()) {
        probeOpenSwimIfNeeded(winningDevice)
      }

      if (lastConnectedDevice?.address != winningDevice.address) {
        val remembered =
          if (prefs.getString("device", null) == winningDevice.address)
            WorkingMode.entries.firstOrNull { it.name == prefs.getString("mode", null) }
          else null
        if (remembered != null) headsetFlow.update { it.copy(mode = remembered) }
      }
      lastConnectedDevice = winningDevice
      trySend(
        ConnectionSnapshot(
          device =
            ConnectedDevice(
              name = winningCandidate.name.ifBlank { "Unknown device" },
              address = winningCandidate.address.ifBlank { "—" },
              profileNames = winningCandidate.connectedProfiles.sorted(),
              batteryPercent = batteryPercent(winningDevice),
              verifiedOpenSwim = winningCandidate.verifiedOpenSwim,
            ),
          hiddenHeadphoneName = null,
        ),
      )
    }

    // Starting a fresh observer does not mean the device disconnected. Profile proxies seed
    // the connection asynchronously, and clearing settings here loses acknowledged playback
    // state every time refresh() recreates this flow.
    trySend(ConnectionSnapshot())

    val receiver =
      object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
          if (!hasBluetoothConnectPermission()) {
            evaluate()
            return
          }
          val device: BluetoothDevice? =
            intent.getParcelableExtra(
              BluetoothDevice.EXTRA_DEVICE,
              BluetoothDevice::class.java,
            )
          val address = device?.address?.takeIf { it.isNotBlank() }
          when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
              if (address != null) aclDevices[address] = device
            }
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
              if (address != null) {
                aclDevices.remove(address)
                profileBroadcastDevices.keys.removeAll { it.first == address }
                probedAddresses.remove(address)
              }
            }
            BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED -> {
              val connState = intent.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE, -1)
              if (address != null) {
                if (connState == BluetoothAdapter.STATE_CONNECTED) {
                  aclDevices[address] = device
                } else if (connState == BluetoothAdapter.STATE_DISCONNECTED) {
                  aclDevices.remove(address)
                  profileBroadcastDevices.keys.removeAll { it.first == address }
                  probedAddresses.remove(address)
                }
              }
            }
            BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED ->
              updateProfileBroadcast(profileBroadcastDevices, device, "A2DP", intent)
            BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED ->
              updateProfileBroadcast(profileBroadcastDevices, device, "HFP", intent)
            BluetoothLeAudio.ACTION_LE_AUDIO_CONNECTION_STATE_CHANGED ->
              updateProfileBroadcast(profileBroadcastDevices, device, "LE Audio", intent)
          }
          evaluate()
        }
      }

    val filter =
      IntentFilter().apply {
        addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        addAction(BluetoothDevice.ACTION_UUID)
        addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
        addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
        addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
        addAction(BluetoothLeAudio.ACTION_LE_AUDIO_CONNECTION_STATE_CHANGED)
      }
    ContextCompat.registerReceiver(
      applicationContext,
      receiver,
      filter,
      ContextCompat.RECEIVER_EXPORTED,
    )

    val proxyListener =
      object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
          when (profile) {
            BluetoothProfile.A2DP -> a2dpProxy = proxy
            BluetoothProfile.HEADSET -> headsetProxy = proxy
            BluetoothProfile.LE_AUDIO -> leAudioProxy = proxy
          }
          evaluate()
        }

        override fun onServiceDisconnected(profile: Int) {
          when (profile) {
            BluetoothProfile.A2DP -> a2dpProxy = null
            BluetoothProfile.HEADSET -> headsetProxy = null
            BluetoothProfile.LE_AUDIO -> leAudioProxy = null
          }
          evaluate()
        }
      }

    if (hasBluetoothConnectPermission() && adapter != null) {
      runCatching {
        adapter.getProfileProxy(applicationContext, proxyListener, BluetoothProfile.A2DP)
      }
      runCatching {
        adapter.getProfileProxy(applicationContext, proxyListener, BluetoothProfile.HEADSET)
      }
      runCatching {
        adapter.getProfileProxy(applicationContext, proxyListener, BluetoothProfile.LE_AUDIO)
      }
    }

    val filterObserver = launch {
      combine(deviceFilterFlow, verifiedOpenSwimFlow) { filter, verified -> filter to verified }
        .collect { evaluate() }
    }

    val batteryPoll = launch {
      while (true) {
        delay(10 * 60 * 1000L)
        if (lastConnectedDevice != null) evaluate()
      }
    }

    awaitClose {
      filterObserver.cancel()
      batteryPoll.cancel()
      runCatching { applicationContext.unregisterReceiver(receiver) }
      a2dpProxy?.let { runCatching { adapter?.closeProfileProxy(BluetoothProfile.A2DP, it) } }
      headsetProxy?.let { runCatching { adapter?.closeProfileProxy(BluetoothProfile.HEADSET, it) } }
      leAudioProxy?.let {
        runCatching { adapter?.closeProfileProxy(BluetoothProfile.LE_AUDIO, it) }
      }
    }
  }

  private fun updateProfileBroadcast(
    map: MutableMap<Pair<String, String>, BluetoothDevice>,
    device: BluetoothDevice?,
    profileLabel: String,
    intent: Intent,
  ) {
    val address = device?.address?.takeIf { it.isNotBlank() } ?: return
    when (intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)) {
      BluetoothProfile.STATE_CONNECTED -> map[address to profileLabel] = device
      BluetoothProfile.STATE_DISCONNECTED -> map.remove(address to profileLabel)
    }
  }

  private data class VolumeSnapshot(val percent: Int, val max: Int, val muted: Boolean)

  private fun volumeFlow(): Flow<VolumeSnapshot> = callbackFlow {
    fun emit() {
      val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
      val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
      trySend(VolumeSnapshot(percent = cur * 100 / max, max = max, muted = cur == 0))
    }
    emit()
    val receiver =
      object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
          emit()
        }
      }
    ContextCompat.registerReceiver(
      applicationContext,
      receiver,
      IntentFilter("android.media.VOLUME_CHANGED_ACTION"),
      ContextCompat.RECEIVER_EXPORTED,
    )
    awaitClose { runCatching { applicationContext.unregisterReceiver(receiver) } }
  }

  private fun rawMediaSessionsFlow(): Flow<List<RawMediaSession>> = callbackFlow {
    if (!isNotificationAccessGranted() || mediaSessionManager == null) {
      trySend(emptyList())
      awaitClose {}
      return@callbackFlow
    }

    val component = ComponentName(applicationContext, NowPlayingNotificationListener::class.java)

    fun snapshot() {
      val controllers = runCatching {
        mediaSessionManager.getActiveSessions(component)
      }
        .getOrNull()
        .orEmpty()
      val sessions =
        controllers.map { controller ->
          val md = controller.metadata
          val pb = controller.playbackState
          val stateCode = pb?.state ?: PlaybackState.STATE_NONE
          val playing = stateCode == PlaybackState.STATE_PLAYING
          val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
          val active =
            playing ||
              (!title.isNullOrBlank() &&
                stateCode != PlaybackState.STATE_NONE &&
                stateCode != PlaybackState.STATE_ERROR)
          RawMediaSession(
            candidate =
              MediaSessionCandidate(
                packageName = controller.packageName,
                title = title,
                artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST),
                album = md?.getString(MediaMetadata.METADATA_KEY_ALBUM),
                mediaId = md?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID),
                playing = playing,
                active = active,
                durationMs = md?.getLong(MediaMetadata.METADATA_KEY_DURATION),
                positionMs = pb?.position,
              ),
            controller = controller,
          )
        }
      trySend(sessions)
    }

    snapshot()

    val listener = MediaSessionManager.OnActiveSessionsChangedListener { snapshot() }
    runCatching {
      mediaSessionManager.addOnActiveSessionsChangedListener(listener, component)
    }

    val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    val ticker = scope.launch {
      while (true) {
        delay(2_000)
        snapshot()
      }
    }

    awaitClose {
      runCatching { mediaSessionManager.removeOnActiveSessionsChangedListener(listener) }
      ticker.cancel()
    }
  }
}

internal fun verifiedOpenSwimAddresses(context: Context): Set<String> =
  context
    .getSharedPreferences(HeadsetModePrefsName, Context.MODE_PRIVATE)
    .getStringSet(VerifiedOpenSwimPrefKey, emptySet())
    .orEmpty()
    .toSet()

internal fun BluetoothDevice.toBluetoothCandidate(
  connectedProfiles: Set<String> = emptySet(),
  verifiedOpenSwim: Boolean = false,
): BluetoothDeviceCandidate {
  val btClass = runCatching { bluetoothClass }.getOrNull()
  val uuids =
    runCatching { uuids?.mapNotNull { it?.uuid?.toString() }.orEmpty().toSet() }
      .getOrDefault(emptySet())
  return BluetoothDeviceCandidate(
    address = address.orEmpty(),
    name = runCatching { name }.getOrNull().orEmpty(),
    serviceUuids = uuids,
    majorDeviceClass = btClass?.majorDeviceClass,
    deviceClass = btClass?.deviceClass,
    connectedProfiles = connectedProfiles,
    verifiedOpenSwim = verifiedOpenSwim,
  )
}

private const val HeadsetModePrefsName = "headset-mode"
private const val DeviceFilterPrefKey = "device_filter"
private const val VerifiedOpenSwimPrefKey = "verified_openswim"
private const val OpenSwimProbeTimeoutMs = 2_500L

/** Percent per volume button press on the headset; it has 16 raw steps. */
private const val HeadsetVolumeStep = 6
