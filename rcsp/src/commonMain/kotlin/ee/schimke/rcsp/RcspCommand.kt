package ee.schimke.rcsp

/**
 * Operations an RCSP-capable audio device may provide.
 *
 * The catalogue intentionally contains both confirmed and candidate operations. Call
 * [RcspCapabilities.isSupported] before encoding or sending an operation: an operation being in
 * this type does not mean every headset, or every firmware version, implements it.
 */
sealed interface RcspCommand {
  val id: RcspCommandId

  data object DiscoverCapabilities : RcspCommand {
    override val id = RcspCommandId.DiscoverCapabilities
  }

  data object QueryDeviceInfo : RcspCommand {
    override val id = RcspCommandId.QueryDeviceInfo
  }

  data object QueryBattery : RcspCommand {
    override val id = RcspCommandId.QueryBattery
  }

  data object QueryFirmware : RcspCommand {
    override val id = RcspCommandId.QueryFirmware
  }

  data object QueryWorkingMode : RcspCommand {
    override val id = RcspCommandId.QueryWorkingMode
  }

  data object QueryMediaState : RcspCommand {
    override val id = RcspCommandId.QueryMediaState
  }

  data object QueryVolume : RcspCommand {
    override val id = RcspCommandId.QueryVolume
  }

  data class SetVolume(val level: UByte) : RcspCommand {
    init {
      require(level <= 100u) { "Volume must be a percentage from 0 to 100" }
    }

    override val id = RcspCommandId.SetVolume
  }

  data class AdjustVolume(val delta: Int) : RcspCommand {
    init {
      require(delta != 0) { "Volume adjustment must not be zero" }
    }

    override val id = RcspCommandId.AdjustVolume
  }

  data object Play : RcspCommand {
    override val id = RcspCommandId.Play
  }

  data object Pause : RcspCommand {
    override val id = RcspCommandId.Pause
  }

  data object TogglePlayPause : RcspCommand {
    override val id = RcspCommandId.TogglePlayPause
  }

  data object Stop : RcspCommand {
    override val id = RcspCommandId.Stop
  }

  data object NextTrack : RcspCommand {
    override val id = RcspCommandId.NextTrack
  }

  data object PreviousTrack : RcspCommand {
    override val id = RcspCommandId.PreviousTrack
  }

  /** A relative jump. Jieli's public API describes this as forward or reverse progress. */
  data class SeekRelative(val direction: SeekDirection, val durationMs: UInt) : RcspCommand {
    init {
      require(durationMs > 0u) { "Seek duration must be positive" }
    }

    override val id = RcspCommandId.SeekRelative
  }

  data class SelectWorkingMode(val mode: WorkingMode) : RcspCommand {
    override val id = RcspCommandId.SelectWorkingMode
  }

  data object QueryEqualizer : RcspCommand {
    override val id = RcspCommandId.QueryEqualizer
  }

  data class SelectEqualizerPreset(val preset: EqualizerPreset) : RcspCommand {
    override val id = RcspCommandId.SelectEqualizerPreset
  }

  data object SyncLocalAudioLibrary : RcspCommand {
    override val id = RcspCommandId.SyncLocalAudioLibrary
  }

  data object ListLocalAudio : RcspCommand {
    override val id = RcspCommandId.ListLocalAudio
  }

  data class PlayLocalAudio(val trackId: UInt) : RcspCommand {
    override val id = RcspCommandId.PlayLocalAudio
  }

  data object EnterPairingMode : RcspCommand {
    override val id = RcspCommandId.EnterPairingMode
  }

  data object ToggleMultipoint : RcspCommand {
    override val id = RcspCommandId.ToggleMultipoint
  }

  data object ToggleVoicePrompts : RcspCommand {
    override val id = RcspCommandId.ToggleVoicePrompts
  }

  data object Reboot : RcspCommand {
    override val id = RcspCommandId.Reboot
  }

  data object PowerOff : RcspCommand {
    override val id = RcspCommandId.PowerOff
  }
}

enum class RcspCommandId {
  DiscoverCapabilities,
  QueryDeviceInfo,
  QueryBattery,
  QueryFirmware,
  QueryWorkingMode,
  QueryMediaState,
  QueryVolume,
  SetVolume,
  AdjustVolume,
  Play,
  Pause,
  TogglePlayPause,
  Stop,
  NextTrack,
  PreviousTrack,
  SeekRelative,
  SelectWorkingMode,
  QueryEqualizer,
  SelectEqualizerPreset,
  SyncLocalAudioLibrary,
  ListLocalAudio,
  PlayLocalAudio,
  EnterPairingMode,
  ToggleMultipoint,
  ToggleVoicePrompts,
  Reboot,
  PowerOff,
}

enum class SeekDirection {
  Forward,
  Backward,
}

enum class WorkingMode {
  Bluetooth,
  LocalAudio,
  LineIn,
  Usb,
  Unknown,
}

enum class EqualizerPreset {
  Standard,
  Vocal,
  BassBoost,
  TrebleBoost,
  Swimming,
  Custom,
}
