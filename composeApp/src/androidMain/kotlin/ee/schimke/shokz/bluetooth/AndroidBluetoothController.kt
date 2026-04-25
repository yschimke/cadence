package ee.schimke.shokz.bluetooth

import android.Manifest
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
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@ContributesBinding(AppScope::class, binding = binding<BluetoothController>())
@SingleIn(AppScope::class)
@Inject
class AndroidBluetoothController(
    private val applicationContext: Context,
) : BluetoothController {

    private val audioManager =
        applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val bluetoothManager: BluetoothManager? =
        applicationContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    private val mediaSessionManager: MediaSessionManager? =
        applicationContext.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager

    private val refreshTrigger = MutableStateFlow(0L)

    override val state: Flow<BluetoothState> = combine(
        connectionFlow(),
        volumeFlow(),
        mediaInfoFlow(),
        refreshTrigger,
    ) { connection, volume, media, _ ->
        BluetoothState(
            connectedDevice = connection,
            volumePercent = volume.percent,
            maxVolume = volume.max,
            muted = volume.muted,
            mediaInfo = media,
            mediaAccessGranted = isNotificationAccessGranted(),
            permissionMissing = !hasBluetoothConnectPermission(),
        )
    }.distinctUntilChanged()

    override fun refresh() {
        refreshTrigger.value = SystemClock.elapsedRealtime()
    }

    override fun setVolume(percent: Int) {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = (percent.coerceIn(0, 100) * max / 100).coerceIn(0, max)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
        refresh()
    }

    override fun adjustVolume(delta: Int) {
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

    override fun play() = dispatchKey(KeyEvent.KEYCODE_MEDIA_PLAY)
    override fun pause() = dispatchKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
    override fun playPause() = dispatchKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
    override fun next() = dispatchKey(KeyEvent.KEYCODE_MEDIA_NEXT)
    override fun previous() = dispatchKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    override fun stop() = dispatchKey(KeyEvent.KEYCODE_MEDIA_STOP)
    override fun fastForward() = dispatchKey(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)
    override fun rewind() = dispatchKey(KeyEvent.KEYCODE_MEDIA_REWIND)

    override fun openSystemBluetoothSettings() {
        val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS).apply {
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

    override suspend fun dispatchAdvanced(command: AdvancedCommand): String = when (command) {
        AdvancedCommand.QueryBattery -> batteryFromConnected() ?: "Unknown — connect a device"
        AdvancedCommand.PowerOff,
        AdvancedCommand.Reboot,
        AdvancedCommand.FactoryReset -> "Pending RCSP transport (opcode 0x0E)"
        AdvancedCommand.EqFlat,
        AdvancedCommand.EqVocal,
        AdvancedCommand.EqBassBoost,
        AdvancedCommand.EqTreble -> "Pending RCSP transport (opcode 0x08 SYS_INFO_SET)"
        AdvancedCommand.ToggleSwimmingMode -> "Pending RCSP transport"
        AdvancedCommand.VoicePromptToggle,
        AdvancedCommand.LanguageEnglish,
        AdvancedCommand.LanguageMandarin -> "Pending RCSP transport"
        AdvancedCommand.EnterPairingMode,
        AdvancedCommand.ClearPairList,
        AdvancedCommand.ToggleMultipoint -> "Pending RCSP transport"
        AdvancedCommand.QueryFirmware,
        AdvancedCommand.QueryDeviceInfo,
        AdvancedCommand.DumpLogs -> "Pending RCSP transport (opcode 0x07 SYS_INFO_GET)"
        AdvancedCommand.StartOta -> "Pending RCSP transport (opcode 0xE1)"
    }

    private fun batteryFromConnected(): String? {
        if (!hasBluetoothConnectPermission()) return null
        val device = connectedAudioDevice() ?: return null
        return runCatching {
            val method = BluetoothDevice::class.java.getMethod("getBatteryLevel")
            val level = method.invoke(device) as? Int
            if (level == null || level < 0) null else "$level%"
        }.getOrNull()
    }

    @Volatile
    private var lastConnectedDevice: BluetoothDevice? = null

    private fun connectedAudioDevice(): BluetoothDevice? {
        if (!hasBluetoothConnectPermission()) return null
        return lastConnectedDevice
    }

    private fun dispatchKey(keyCode: Int) {
        val now = SystemClock.uptimeMillis()
        audioManager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
        audioManager.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
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
                trySend(null)
                return
            }
            lastConnectedDevice = device
            trySend(
                ConnectedDevice(
                    name = runCatching { device.name }.getOrNull() ?: "Unknown device",
                    address = device.address ?: "—",
                    profileNames = activeProfiles.toList().sorted(),
                )
            )
        }

        emitCurrent()

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

        awaitClose { runCatching { applicationContext.unregisterReceiver(receiver) } }
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
