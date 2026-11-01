package ee.schimke.rcsp

/** One name observed in a captured OpenSwim local-audio sync response. */
internal data class ShokzLocalAudioName(val index: UInt, val name: String)

internal data class ShokzLocalAudioBatch(val names: List<ShokzLocalAudioName>, val nextIndex: Int?)

/**
 * Decodes the observed 0x16 sync response, not a complete listing or a verified query API. Reject
 * malformed packets; a truncated or partial response must never imply an empty device.
 */
internal fun parseShokzLocalAudioNames(payload: ByteArray): ShokzLocalAudioBatch {
  fun uint(offset: Int): UInt {
    require(offset >= 0 && offset <= payload.size - 4)
    return (0..3).fold(0u) { value, i ->
      value or ((payload[offset + i].toUByte().toUInt()) shl (8 * i))
    }
  }
  fun ushort(offset: Int): Int {
    require(offset >= 0 && offset <= payload.size - 2)
    return payload[offset].toUByte().toInt() or (payload[offset + 1].toUByte().toInt() shl 8)
  }
  require(payload.size >= 42)
  require(uint(0) == 1u && uint(4) == (payload.size - 8).toUInt())
  require(uint(8) == 2u && uint(12) == 4u && uint(16) == 3u)
  require(uint(20) == 3u && uint(24) == 4u && uint(28) == 0x16u && uint(32) == 4u)
  require(uint(36) == (payload.size - 40).toUInt())
  val count = ushort(40)
  var offset = 42
  val names = ArrayList<ShokzLocalAudioName>(count)
  repeat(count) {
    val index = uint(offset)
    val length = ushort(offset + 4)
    offset += 6
    require(length > 0 && length <= payload.size - offset)
    val name = payload.decodeToString(offset, offset + length, throwOnInvalidSequence = true)
    names += ShokzLocalAudioName(index, name)
    offset += length
  }
  val nextIndex =
    when (payload.size - offset) {
      0 -> null
      2 ->
        ushort(offset).also { next ->
          require(names.isEmpty() || next.toUInt() > names.last().index)
        }
      else -> throw IllegalArgumentException("Unexpected local audio list trailer")
    }
  return ShokzLocalAudioBatch(names, nextIndex)
}
