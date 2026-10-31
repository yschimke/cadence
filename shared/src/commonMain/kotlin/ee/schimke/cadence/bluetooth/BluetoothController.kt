package ee.schimke.cadence.bluetooth

import kotlinx.coroutines.flow.Flow

interface BluetoothController {
  /**
   * False where classic-audio control cannot run (desktop, browser): the UI hides every BT entry.
   */
  val supported: Boolean

  val state: Flow<BluetoothState>

  fun refresh()

  fun setVolume(percent: Int)

  fun adjustVolume(delta: Int)

  fun toggleMute()

  fun play()

  fun pause()

  fun playPause()

  fun next()

  fun previous()

  fun stop()

  fun fastForward()

  fun rewind()

  fun openSystemBluetoothSettings()

  fun requestMediaAccess()

  /**
   * Switches an OpenSwim Pro between Bluetooth and on-device MP3 playback. Other devices may reject
   * this request rather than silently changing local UI state.
   */
  suspend fun setWorkingMode(mode: WorkingMode): String

  /** Selects an OpenSwim Pro EQ preset; the acknowledged value is reflected in [BluetoothState]. */
  suspend fun setEqualizer(preset: EqualizerPreset): String

  /** Selects the OpenSwim Pro Local Audio playback order. Only meaningful in MP3 mode. */
  suspend fun setPlaybackOrder(order: PlaybackOrder): String

  /**
   * Best-effort dispatch for the catalogue of vendor (RCSP) commands documented in research/.
   * Returns a human-readable result string for the UI to display.
   */
  suspend fun dispatchAdvanced(command: AdvancedCommand): String
}

enum class WorkingMode {
  Bluetooth,
  Mp3,
}

/** OpenSwim Pro EQ presets with captured vendor values. */
enum class EqualizerPreset(val label: String) {
  Standard("Standard"),
  Vocal("Vocal"),
  Swimming("Swimming"),
}

/** OpenSwim Pro Local Audio playback order. */
enum class PlaybackOrder(val label: String) {
  Normal("In order"),
  Shuffle("Shuffle"),
  Repeat("Repeat"),
}

data class BluetoothState(
  val connectedDevice: ConnectedDevice? = null,
  val volumePercent: Int = 0,
  val maxVolume: Int = 15,
  val muted: Boolean = false,
  val mediaInfo: MediaInfo? = null,
  val mediaAccessGranted: Boolean = false,
  val permissionMissing: Boolean = false,
  val workingMode: WorkingMode = WorkingMode.Bluetooth,
  /**
   * Last values the connected OpenSwim Pro acknowledged to Cadence. They are not read back from the
   * headset, so they are null until Cadence sets them and are cleared when the headset disconnects.
   */
  val equalizer: EqualizerPreset? = null,
  val playbackOrder: PlaybackOrder? = null,
  val headsetVolumePercent: Int? = null,
  /**
   * MP3 mode: whether the last Play/Pause the headset acknowledged was Play. The headset does not
   * report playback state, so its own buttons can make this stale; null until Cadence sends one.
   */
  val headsetPlaying: Boolean? = null,
) {
  /** Vendor (RCSP) controls are only wired for OpenSwim Pro. */
  val openSwimConnected: Boolean
    get() = connectedDevice?.name?.contains(OpenSwimProName, ignoreCase = true) == true

  /** Local Audio transport, volume and playback order go to the headset itself. */
  val localAudioControls: Boolean
    get() = openSwimConnected && workingMode == WorkingMode.Mp3
}

const val OpenSwimProName = "OpenSwim Pro"

data class ConnectedDevice(
  val name: String,
  val address: String,
  val profileNames: List<String>,
  val batteryPercent: Int? = null,
  val codec: String? = null,
)

data class MediaInfo(
  val title: String?,
  val artist: String?,
  val album: String?,
  val playing: Boolean,
  val durationMs: Long?,
  val positionMs: Long?,
  val packageName: String?,
)

enum class AdvancedCommandCategory {
  Audio,
  Power,
  Voice,
  Pairing,
  Diagnostics,
  Firmware,
}

/**
 * Catalogue of vendor commands surfaced in the advanced menu. Most map to RCSP opcodes documented
 * in research/rcsp.md; until the RFCOMM transport ships, unsupported commands return a placeholder
 * result.
 */
enum class AdvancedCommand(val label: String, val category: AdvancedCommandCategory) {
  QueryBattery("Query battery", AdvancedCommandCategory.Power),
  QueryFirmware("Query firmware version", AdvancedCommandCategory.Firmware),
  QueryDeviceInfo("Query device info", AdvancedCommandCategory.Diagnostics),
  QueryLocalAudioState("Query Local Audio state", AdvancedCommandCategory.Audio),
  EqFlat("EQ: Standard", AdvancedCommandCategory.Audio),
  EqVocal("EQ: Vocal", AdvancedCommandCategory.Audio),
  EqBassBoost("EQ: Bass boost", AdvancedCommandCategory.Audio),
  EqTreble("EQ: Treble", AdvancedCommandCategory.Audio),
  ToggleSwimmingMode("EQ: Swimming", AdvancedCommandCategory.Audio),
  VoicePromptToggle("Toggle voice prompts", AdvancedCommandCategory.Voice),
  LanguageEnglish("Voice: English", AdvancedCommandCategory.Voice),
  LanguageMandarin("Voice: Mandarin", AdvancedCommandCategory.Voice),
  EnterPairingMode("Enter pairing mode", AdvancedCommandCategory.Pairing),
  ClearPairList("Clear paired list", AdvancedCommandCategory.Pairing),
  ToggleMultipoint("Toggle multipoint", AdvancedCommandCategory.Pairing),
  PowerOff("Power off", AdvancedCommandCategory.Power),
  Reboot("Reboot", AdvancedCommandCategory.Power),
  FactoryReset("Factory reset", AdvancedCommandCategory.Power),
  StartOta("Start OTA upload", AdvancedCommandCategory.Firmware),
  DumpLogs("Dump device logs", AdvancedCommandCategory.Diagnostics),
}
