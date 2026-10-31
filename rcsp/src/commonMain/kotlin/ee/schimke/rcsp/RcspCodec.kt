package ee.schimke.rcsp

/** Public Jieli RCSP packet framing, independent of Bluetooth transport and authentication. */
data class RcspPacket(
  val type: Type,
  val opcode: UByte,
  val responseRequired: Boolean = false,
  val payload: ByteArray = byteArrayOf(),
) {
  enum class Type {
    Command,
    Response,
  }
}

/**
 * Codec for the public FE DC BA ... EF RCSP frame. OEM products may place RCSP inside another
 * authenticated envelope; use this codec only after selecting a matching [RcspDeviceProfile].
 */
object RcspCodec {
  private val start = byteArrayOf(0xFE.toByte(), 0xDC.toByte(), 0xBA.toByte())
  private const val end: Byte = 0xEF.toByte()
  private const val responseBit = 0x80
  private const val responseRequiredBit = 0x40
  private const val minimumFrameSize = 8

  fun encode(packet: RcspPacket): ByteArray {
    require(packet.payload.size <= UShort.MAX_VALUE.toInt()) { "RCSP payload is too large" }
    val head =
      packet.opcode.toInt() or
        (if (packet.type == RcspPacket.Type.Response) responseBit else 0) or
        (if (packet.responseRequired) responseRequiredBit else 0)
    val length = packet.payload.size
    return ByteArray(minimumFrameSize + length).also { frame ->
      start.copyInto(frame)
      frame[3] = head.toByte()
      frame[4] = 0 // The public framing reserves the high head byte.
      frame[5] = (length and 0xFF).toByte()
      frame[6] = (length ushr 8).toByte()
      packet.payload.copyInto(frame, destinationOffset = 7)
      frame[frame.lastIndex] = end
    }
  }

  /** Decodes one complete frame. Stream buffering belongs to the transport layer. */
  fun decode(frame: ByteArray): RcspPacket {
    require(frame.size >= minimumFrameSize) { "RCSP frame is too short" }
    require(frame.copyOfRange(0, start.size).contentEquals(start)) { "Missing RCSP start tag" }
    require(frame.last() == end) { "Missing RCSP end tag" }
    val length = frame[5].toInt().and(0xFF) or (frame[6].toInt().and(0xFF) shl 8)
    require(frame.size == minimumFrameSize + length) { "RCSP payload length does not match frame" }
    val head = frame[3].toInt().and(0xFF)
    return RcspPacket(
      type = if (head and responseBit != 0) RcspPacket.Type.Response else RcspPacket.Type.Command,
      opcode = (head and 0x3F).toUByte(),
      responseRequired = head and responseRequiredBit != 0,
      payload = frame.copyOfRange(7, frame.lastIndex),
    )
  }
}
