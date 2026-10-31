package ee.schimke.rcsp

/**
 * Encoder for the small, documented public Jieli system-information subset.
 *
 * Function-control commands (media transport, seeking, local-file control and working-mode changes)
 * deliberately return `null`: their function IDs vary between chipset families. An app must provide
 * a verified vendor encoder before [RcspCommandGateway] will send one.
 */
object PublicJieliCommandEncoder : RcspCommandEncoder {
  private const val systemInfoGetOpcode: UByte = 0x07u
  private const val systemInfoSetOpcode: UByte = 0x08u

  private const val batteryAttribute: Byte = 0x00
  private const val volumeAttribute: Byte = 0x01
  private const val equalizerAttribute: Byte = 0x04
  private const val workingModeAttribute: Byte = 0x06

  override fun encode(command: RcspCommand): RcspPacket? =
    when (command) {
      RcspCommand.QueryBattery -> get(batteryAttribute)
      RcspCommand.QueryVolume -> get(volumeAttribute)
      is RcspCommand.SetVolume -> set(volumeAttribute, command.level.toByte())
      RcspCommand.QueryEqualizer -> get(equalizerAttribute)
      RcspCommand.QueryWorkingMode -> get(workingModeAttribute)
      else -> null
    }

  private fun get(attribute: Byte) =
    RcspPacket(
      type = RcspPacket.Type.Command,
      opcode = systemInfoGetOpcode,
      responseRequired = true,
      payload = byteArrayOf(attribute),
    )

  private fun set(attribute: Byte, value: Byte) =
    RcspPacket(
      type = RcspPacket.Type.Command,
      opcode = systemInfoSetOpcode,
      responseRequired = true,
      payload = byteArrayOf(attribute, value),
    )
}
