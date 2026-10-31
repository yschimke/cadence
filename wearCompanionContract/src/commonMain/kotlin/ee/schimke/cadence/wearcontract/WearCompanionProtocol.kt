package ee.schimke.cadence.wearcontract

import ee.schimke.rcsp.EqualizerPreset
import ee.schimke.rcsp.WorkingMode

/**
 * Stable, transport-neutral contract for the paired Cadence phone and Wear OS companion.
 *
 * Data Layer serialisation deliberately lives at each Android endpoint. This module keeps path, key
 * and command values identical without coupling either app to the other's UI or Bluetooth
 * implementation.
 */
object WearCompanionProtocol {
  const val SnapshotPath = "/cadence/open-swim-pro/snapshot/v1"
  const val CommandPath = "/cadence/open-swim-pro/command/v1"

  const val SchemaVersionKey = "schema_version"
  const val RevisionKey = "revision"
  const val ConnectionKey = "connection"
  const val DeviceNameKey = "device_name"
  const val UnavailableReasonKey = "unavailable_reason"
  const val ModeKey = "mode"
  const val CapabilityIdsKey = "capability_ids"
  const val VolumePercentKey = "volume_percent"
  const val SyncStateKey = "sync_state"
  const val SyncDetailKey = "sync_detail"
  const val SyncFilesCompletedKey = "sync_files_completed"
  const val SyncFilesTotalKey = "sync_files_total"
  const val SyncFilesSkippedKey = "sync_files_skipped"

  const val CommandKey = "command"
  const val CommandValueKey = "command_value"

  const val SchemaVersion = 1

  fun encode(command: CompanionCommand): ByteArray = buildString {
    append(command.kind)
    command.value?.let { append('|').append(it) }
  }
    .encodeToByteArray()

  fun decodeCommand(bytes: ByteArray): CompanionCommand? {
    val parts = bytes.decodeToString().split('|', limit = 2)
    val value = parts.getOrNull(1)
    return when (parts.firstOrNull()) {
      "play" -> CompanionCommand.Play
      "pause" -> CompanionCommand.Pause
      "previous" -> CompanionCommand.PreviousTrack
      "next" -> CompanionCommand.NextTrack
      "set_mode" ->
        value
          ?.let { runCatching { WorkingMode.valueOf(it) }.getOrNull() }
          ?.let(CompanionCommand::SelectMode)
      "equalizer" ->
        value
          ?.let { runCatching { EqualizerPreset.valueOf(it) }.getOrNull() }
          ?.let(CompanionCommand::SelectEqualizerPreset)
      "play_local_audio" -> value?.toUIntOrNull()?.let(CompanionCommand::PlayLocalAudio)
      "set_volume" ->
        value?.toIntOrNull()?.takeIf { it in 0..100 }?.let(CompanionCommand::SetVolume)
      else -> null
    }
  }
}

sealed interface CompanionCommand {
  val kind: String
  val value: String?

  data object Play : CompanionCommand {
    override val kind = "play"
    override val value: String? = null
  }

  data object Pause : CompanionCommand {
    override val kind = "pause"
    override val value: String? = null
  }

  data object PreviousTrack : CompanionCommand {
    override val kind = "previous"
    override val value: String? = null
  }

  data object NextTrack : CompanionCommand {
    override val kind = "next"
    override val value: String? = null
  }

  data class SelectMode(val mode: WorkingMode) : CompanionCommand {
    override val kind = "set_mode"
    override val value = mode.name
  }

  data class SelectEqualizerPreset(val preset: EqualizerPreset) : CompanionCommand {
    override val kind = "equalizer"
    override val value = preset.name
  }

  data class PlayLocalAudio(val trackId: UInt) : CompanionCommand {
    override val kind = "play_local_audio"
    override val value = trackId.toString()
  }

  data class SetVolume(val percent: Int) : CompanionCommand {
    override val kind = "set_volume"
    override val value = percent.toString()
  }
}
