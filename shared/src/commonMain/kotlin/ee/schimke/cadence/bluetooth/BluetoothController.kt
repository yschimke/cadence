package ee.schimke.cadence.bluetooth

import kotlinx.coroutines.flow.Flow

interface BluetoothController {
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
   * UI-selected working mode. The opcode that switches the headphones between Bluetooth and
   * on-device MP3 playback is unconfirmed (FUNCTION_CMD 0x0E sub-opcode), so the selection is
   * currently presentation-only.
   */
  suspend fun setWorkingMode(mode: WorkingMode): String

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

data class BluetoothState(
  val connectedDevice: ConnectedDevice? = null,
  val volumePercent: Int = 0,
  val maxVolume: Int = 15,
  val muted: Boolean = false,
  val mediaInfo: MediaInfo? = null,
  val mediaAccessGranted: Boolean = false,
  val permissionMissing: Boolean = false,
  val workingMode: WorkingMode = WorkingMode.Bluetooth,
)

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
  EqFlat("EQ: Flat", AdvancedCommandCategory.Audio),
  EqVocal("EQ: Vocal", AdvancedCommandCategory.Audio),
  EqBassBoost("EQ: Bass boost", AdvancedCommandCategory.Audio),
  EqTreble("EQ: Treble", AdvancedCommandCategory.Audio),
  ToggleSwimmingMode("Toggle swimming mode", AdvancedCommandCategory.Audio),
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
