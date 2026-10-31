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
 * Local Audio play/pause/previous/next were captured on firmware R_24_202520250815 and have a
 * byte-exact vendor encoder. They are still mode-dependent: do not present them while the device is
 * in Bluetooth, Line In or USB mode. Volume, seek, mode changes and playlist selection remain
 * unsupported until independently captured and verified.
 */
val OpenSwimProProfile =
  RcspDeviceProfile(
    name = "OpenSwim Pro",
    serviceUuid = "0000fef0-0000-1000-8000-00805f9b34fb",
    rfcommChannel = 10,
    capabilities =
      RcspCapabilities()
        .withSupported(
          RcspCommandId.Play,
          RcspCommandId.Pause,
          RcspCommandId.NextTrack,
          RcspCommandId.PreviousTrack,
        ),
  )
