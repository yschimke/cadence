package ee.schimke.cadence.bluetooth

import android.Manifest
import android.app.Activity
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
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
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import ee.schimke.rcsp.AndroidShokzOpenSwimTransport
import ee.schimke.rcsp.EqualizerPreset as RcspEqualizerPreset
import ee.schimke.rcsp.OpenSwimProProfile
import ee.schimke.rcsp.PlaybackOrder as RcspPlaybackOrder
import ee.schimke.rcsp.RcspCommand
import ee.schimke.rcsp.ShokzOpenSwimController
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.UUID

@ContributesBinding(AppScope::class, binding = binding<BluetoothController>())
@SingleIn(AppScope::class)
@Inject
@OptIn(ExperimentalCoroutinesApi::class)
class AndroidBluetoothController(
    private val applicationContext: Context,
) : BluetoothController {

    private val audioManager =
        applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val bluetoothManager: BluetoothManager? =
        applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    private val mediaSessionManager: MediaSessionManager? =
        applicationContext.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager

    private val refreshTrigger = MutableStateFlow(0)
    /** Mode and settings the connected OpenSwim Pro acknowledged; none are read back. */
    private data class HeadsetSettings(
        val mode: WorkingMode = WorkingMode.Bluetooth,
        val equalizer: EqualizerPreset? = null,
        val playbackOrder: PlaybackOrder? = null,
        val volumePercent: Int? = null,
        val volumeError: Boolean = false,
        val playing: Boolean? = null,
    )

    private val headsetFlow = MutableStateFlow(HeadsetSettings())
    private val commandScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override val supported = true

    override val state: Flow<BluetoothState> =
        refreshTrigger
            .flatMapLatest {
                combine(
                    connectionFlow(),
                    volumeFlow(),
                    mediaInfoFlow(),
                    headsetFlow,
                ) { connection, volume, media, headset ->
                    BluetoothState(
                        connectedDevice = connection,
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

                override fun onActivitySaveInstanceState(activity: Activity, state: android.os.Bundle) = Unit

                override fun onActivityDestroyed(activity: Activity) = Unit
            },
        )
    }

    override fun refresh() {
        // Recreate the platform callback flows: both notification-listener access and runtime
        // Bluetooth permissions can be changed while Cadence is in Android Settings.
        refreshTrigger.update { it + 1 }
    }

    override fun setVolume(percent: Int) {
        val target = percent.coerceIn(0, 100)
        localAudioDevice()?.let { device ->
            // MP3 playback is on the headset, so the phone's stream volume does not reach it.
            val previous = headsetFlow.value.volumePercent
            headsetFlow.update { it.copy(volumePercent = target, volumeError = false) }
            dispatchOpenSwim(device, RcspCommand.SetVolume(target.toUByte()), onFailure = {
                headsetFlow.update {
                    if (it.volumePercent == target) it.copy(volumePercent = previous, volumeError = true) else it
                }
            })
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
        val direction = when {
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

    override fun play() = dispatchMediaOrOpenSwim(RcspCommand.Play, KeyEvent.KEYCODE_MEDIA_PLAY)
    override fun pause() = dispatchMediaOrOpenSwim(RcspCommand.Pause, KeyEvent.KEYCODE_MEDIA_PAUSE)

    /** MP3 mode has no captured toggle, so it follows the last acknowledged Play/Pause. */
    override fun playPause() {
        if (localAudioDevice() == null) {
            dispatchKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        } else if (headsetFlow.value.playing == true) {
            pause()
        } else {
            play()
        }
    }
    override fun next() = dispatchMediaOrOpenSwim(RcspCommand.NextTrack, KeyEvent.KEYCODE_MEDIA_NEXT)
    override fun previous() = dispatchMediaOrOpenSwim(RcspCommand.PreviousTrack, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    override fun stop() = dispatchKey(KeyEvent.KEYCODE_MEDIA_STOP)
    override fun fastForward() = dispatchKey(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)
    override fun rewind() = dispatchKey(KeyEvent.KEYCODE_MEDIA_REWIND)

    override fun openSystemBluetoothSettings() {
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { applicationContext.startActivity(intent) }
    }

    override fun openAppPermissionSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", applicationContext.packageName, null),
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { applicationContext.startActivity(intent) }
    }

    override fun requestMediaAccess() {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
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
                        applicationContext.getSharedPreferences("headset-mode", Context.MODE_PRIVATE)
                            .edit().putString("device", device.address).putString("mode", mode.name).apply()
                        headsetFlow.update { it.copy(mode = mode, playing = null) }
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
                    device.createRfcommSocketToServiceRecord(UUID.fromString(OpenSwimProProfile.serviceUuid))
                socket.use {
                    it.connect()
                    ShokzOpenSwimController(AndroidShokzOpenSwimTransport(it))
                        .execute(RcspCommand.QueryMediaState)
                }
            }.fold(
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
        return batteryPercent(device)?.let {
            ConnectedDevice(runCatching { device.name }.getOrNull().orEmpty(), "", emptyList(), batteryPercent = it).batterySummary()
        }
    }

    private fun batteryPercent(device: BluetoothDevice): Int? {
        if (!hasBluetoothConnectPermission()) return null
        return runCatching {
            val method = BluetoothDevice::class.java.getMethod("getBatteryLevel")
            val level = method.invoke(device) as? Int
            level?.takeIf { it in 0..100 }
        }.getOrNull()
    }

    @Volatile
    private var lastConnectedDevice: BluetoothDevice? = null

    private fun connectedAudioDevice(): BluetoothDevice? {
        if (!hasBluetoothConnectPermission()) return null
        return lastConnectedDevice
    }

    private fun openSwimDevice(): BluetoothDevice? =
        connectedAudioDevice()?.takeIf {
            runCatching { it.name }.getOrNull()?.contains(OpenSwimProName, ignoreCase = true) == true
        }

    /** The OpenSwim Pro, when Cadence has switched it to on-device (MP3) playback. */
    private fun localAudioDevice(): BluetoothDevice? =
        openSwimDevice()?.takeIf { headsetFlow.value.mode == WorkingMode.Mp3 }

    private fun dispatchKey(keyCode: Int) {
        val now = SystemClock.uptimeMillis()
        audioManager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
        audioManager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
    }

    /**
     * Captured OpenSwim Local Audio media actions use a vendor RFCOMM envelope, not AVRCP.
     * The UI's MP3 selection is an explicit guard: it does not switch headset mode itself.
     */
    private fun dispatchMediaOrOpenSwim(command: RcspCommand, mediaKeyCode: Int) {
        val device = localAudioDevice()
        if (device == null) {
            dispatchKey(mediaKeyCode)
            return
        }
        dispatchOpenSwim(device, command) {
            when (command) {
                RcspCommand.Play -> headsetFlow.update { it.copy(playing = true) }
                RcspCommand.Pause -> headsetFlow.update { it.copy(playing = false) }
                else -> Unit
            }
        }
    }

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
        device.createRfcommSocketToServiceRecord(UUID.fromString(OpenSwimProProfile.serviceUuid)).use { socket ->
            socket.connect()
            ShokzOpenSwimController(AndroidShokzOpenSwimTransport(socket)).execute(command)
        }

    private fun hasBluetoothConnectPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ContextCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun isNotificationAccessGranted(): Boolean {
        val flat = Settings.Secure.getString(
            applicationContext.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        val cn = ComponentName(applicationContext, NowPlayingNotificationListener::class.java)
        return flat.split(":").any { it.equals(cn.flattenToString(), ignoreCase = true) }
    }

    private fun connectionFlow(): Flow<ConnectedDevice?> = callbackFlow {
        val activeProfiles = mutableSetOf<String>()
        var current: BluetoothDevice? = null

        fun emitCurrent() {
            val device = current
            if (device == null) {
                lastConnectedDevice = null
                // Acknowledged settings belong to that connection; the mode selection is kept.
                headsetFlow.update { HeadsetSettings(mode = it.mode) }
                trySend(null)
                return
            }
            if (lastConnectedDevice?.address != device.address) {
                val prefs = applicationContext.getSharedPreferences("headset-mode", Context.MODE_PRIVATE)
                val remembered = if (prefs.getString("device", null) == device.address)
                    WorkingMode.entries.firstOrNull { it.name == prefs.getString("mode", null) } else null
                if (remembered != null) headsetFlow.update { it.copy(mode = remembered) }
            }
            lastConnectedDevice = device
            trySend(
                ConnectedDevice(
                    name = runCatching { device.name }.getOrNull() ?: "Unknown device",
                    address = device.address ?: "—",
                    profileNames = activeProfiles.toList().sorted(),
                    batteryPercent = batteryPercent(device),
                )
            )
        }

        // Starting a fresh observer does not mean the device disconnected. Profile proxies seed
        // the connection asynchronously, and clearing settings here loses acknowledged playback
        // state every time refresh() recreates this flow.
        trySend(null)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (!hasBluetoothConnectPermission()) {
                    current = null
                    activeProfiles.clear()
                    emitCurrent()
                    return
                }
                val device: BluetoothDevice? = intent.getParcelableExtra(
                    BluetoothDevice.EXTRA_DEVICE,
                    BluetoothDevice::class.java,
                )
                when (intent.action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED -> {
                        current = device
                    }
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        if (device == current) current = null
                        activeProfiles.clear()
                    }
                    BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED -> {
                        val state = intent.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE, -1)
                        if (state == BluetoothAdapter.STATE_CONNECTED && device != null) {
                            current = device
                        } else if (state == BluetoothAdapter.STATE_DISCONNECTED && device == current) {
                            current = null
                            activeProfiles.clear()
                        }
                    }
                }
                emitCurrent()
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
        }
        ContextCompat.registerReceiver(
            applicationContext,
            receiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED,
        )

        // Best-effort initial seed via profile proxies (non-blocking).
        val adapter = bluetoothManager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
        val proxyListener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (!hasBluetoothConnectPermission()) return
                runCatching {
                    val device = proxy.connectedDevices.firstOrNull()
                    if (device != null) {
                        if (current == null) current = device
                        when (profile) {
                            BluetoothProfile.A2DP -> activeProfiles += "A2DP"
                            BluetoothProfile.HEADSET -> activeProfiles += "HFP"
                        }
                        emitCurrent()
                    }
                }
                runCatching { adapter?.closeProfileProxy(profile, proxy) }
            }

            override fun onServiceDisconnected(profile: Int) {}
        }
        if (hasBluetoothConnectPermission() && adapter != null) {
            runCatching { adapter.getProfileProxy(applicationContext, proxyListener, BluetoothProfile.A2DP) }
            runCatching { adapter.getProfileProxy(applicationContext, proxyListener, BluetoothProfile.HEADSET) }
        }

        val batteryPoll = launch {
            while (true) {
                delay(10 * 60 * 1000L)
                if (current != null) emitCurrent()
            }
        }

        awaitClose {
            batteryPoll.cancel()
            runCatching { applicationContext.unregisterReceiver(receiver) }
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
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) { emit() }
        }
        ContextCompat.registerReceiver(
            applicationContext,
            receiver,
            IntentFilter("android.media.VOLUME_CHANGED_ACTION"),
            ContextCompat.RECEIVER_EXPORTED,
        )
        awaitClose { runCatching { applicationContext.unregisterReceiver(receiver) } }
    }

    private fun mediaInfoFlow(): Flow<MediaInfo?> = callbackFlow {
        if (!isNotificationAccessGranted() || mediaSessionManager == null) {
            trySend(null)
            awaitClose {}
            return@callbackFlow
        }

        val component = ComponentName(applicationContext, NowPlayingNotificationListener::class.java)

        fun snapshot() {
            val controllers = runCatching {
                mediaSessionManager.getActiveSessions(component)
            }.getOrNull().orEmpty()
            val active = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                ?: controllers.firstOrNull()
            if (active == null) {
                trySend(null)
                return
            }
            val md = active.metadata
            val pb = active.playbackState
            trySend(
                MediaInfo(
                    title = md?.getString(MediaMetadata.METADATA_KEY_TITLE),
                    artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST),
                    album = md?.getString(MediaMetadata.METADATA_KEY_ALBUM),
                    playing = pb?.state == PlaybackState.STATE_PLAYING,
                    durationMs = md?.getLong(MediaMetadata.METADATA_KEY_DURATION),
                    positionMs = pb?.position,
                    packageName = active.packageName,
                )
            )
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

/** Percent per volume button press on the headset; it has 16 raw steps. */
private const val HeadsetVolumeStep = 6
