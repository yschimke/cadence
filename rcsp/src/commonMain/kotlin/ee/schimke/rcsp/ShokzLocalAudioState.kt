package ee.schimke.rcsp

/**
 * Captured state tuple. The first word's meaning and the stability of indices remain unverified.
 */
internal data class ShokzLocalAudioState(
  val status: UInt,
  val fileId: UInt,
  val firstPosition: UInt,
  val lastPosition: UInt,
  val folderIndex: UInt,
)

/** Decode a captured 0x8040 query reply or 0x11 notification, after envelope verification. */
internal fun parseShokzLocalAudioState(payload: ByteArray): ShokzLocalAudioState {
  fun word(index: Int): UInt =
    (0..3).fold(0u) { value, i -> value or (payload[index * 4 + i].toUByte().toUInt() shl (8 * i)) }
  require(payload.size == 60)
  require(word(0) == 1u && word(1) == 52u && word(2) == 2u && word(3) == 4u)
  require(word(5) == 3u && word(6) == 4u && word(8) == 4u && word(9) == 20u)
  require((word(4) == 2u && word(7) == 0x8040u) || (word(4) == 3u && word(7) == 0x11u))
  return ShokzLocalAudioState(word(10), word(11), word(12), word(13), word(14))
}
