package ee.schimke.rcsp

/**
 * Exact command support reported by a device profile or learned during feature discovery.
 *
 * This is command-level rather than a broad "RCSP supported" switch: a device may permit track
 * navigation while rejecting seeking, or expose a volume state while rejecting writes.
 */
data class RcspCapabilities(
  val supportedCommands: Set<RcspCommandId> = emptySet(),
  val firmwareVersion: String? = null,
) {
  fun isSupported(command: RcspCommand): Boolean = command.id in supportedCommands

  fun isSupported(command: RcspCommandId): Boolean = command in supportedCommands

  fun withSupported(vararg commands: RcspCommandId): RcspCapabilities =
    copy(supportedCommands = supportedCommands + commands)
}

/**
 * Stable device facts that an adapter needs before opening a vendor RFCOMM channel.
 *
 * [capabilities] defaults to no commands. This prevents a newly recognized device from receiving a
 * write merely because it advertises an RCSP-like service UUID.
 */
data class RcspDeviceProfile(
  val name: String,
  val serviceUuid: String,
  val rfcommChannel: Int? = null,
  val capabilities: RcspCapabilities = RcspCapabilities(),
) {
  /** Whether this exact device/firmware profile has opted into [command]. */
  fun isSupported(command: RcspCommand): Boolean = capabilities.isSupported(command)
}

/**
 * Observed OpenSwim Pro connection facts.
 *
 * Local Audio playback-state query, transport, working-mode switch, volume, EQ, and playback order
 * were captured on firmware R_24_202520250815 and have byte-exact vendor encoders. Local Audio
 * transport and playback order remain mode-dependent. Seek and playlist selection remain
 * unsupported until their response contracts are independently verified.
 */
val OpenSwimProProfile =
  RcspDeviceProfile(
    name = "OpenSwim Pro",
    serviceUuid = "0000fef0-0000-1000-8000-00805f9b34fb",
    rfcommChannel = 10,
    capabilities =
      RcspCapabilities()
        .withSupported(
          RcspCommandId.QueryMediaState,
          RcspCommandId.Play,
          RcspCommandId.Pause,
          RcspCommandId.NextTrack,
          RcspCommandId.PreviousTrack,
          RcspCommandId.SelectWorkingMode,
          RcspCommandId.SetVolume,
          RcspCommandId.SelectEqualizerPreset,
          RcspCommandId.SelectPlaybackOrder,
        ),
  )
