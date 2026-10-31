package ee.schimke.rcsp

/**
 * Transport boundary for platform adapters. Android RFCOMM, BLE GATT and test doubles can share the
 * same command/capability API without coupling this library to Android Bluetooth classes.
 */
interface RcspTransport {
  val profile: RcspDeviceProfile

  suspend fun exchange(packet: RcspPacket): RcspPacket
}

class UnsupportedRcspCommandException(command: RcspCommand, profile: RcspDeviceProfile) :
  IllegalStateException("${command.id} is not supported by ${profile.name}")

class UnencodableRcspCommandException(command: RcspCommand) :
  IllegalArgumentException("No encoder is available for ${command.id}")

/** Turns a semantic command into its device-specific RCSP packet. */
fun interface RcspCommandEncoder {
  fun encode(command: RcspCommand): RcspPacket?
}

/**
 * Guarded command gateway. A platform adapter supplies encoding for device-specific commands; this
 * core prevents sending a command that is not explicitly supported by the selected profile.
 */
class RcspCommandGateway(private val transport: RcspTransport) {
  fun isSupported(command: RcspCommand): Boolean =
    transport.profile.capabilities.isSupported(command)

  suspend fun execute(command: RcspCommand, encode: (RcspCommand) -> RcspPacket): RcspPacket {
    if (!isSupported(command)) throw UnsupportedRcspCommandException(command, transport.profile)
    return transport.exchange(encode(command))
  }

  suspend fun execute(command: RcspCommand, encoder: RcspCommandEncoder): RcspPacket {
    if (!isSupported(command)) throw UnsupportedRcspCommandException(command, transport.profile)
    return transport.exchange(
      encoder.encode(command) ?: throw UnencodableRcspCommandException(command)
    )
  }
}
