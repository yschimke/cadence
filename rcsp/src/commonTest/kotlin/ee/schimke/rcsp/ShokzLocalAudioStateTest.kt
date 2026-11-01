package ee.schimke.rcsp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ShokzLocalAudioStateTest {
  @Test
  fun `captured query and notification agree on file and folder`() {
    val response =
      "010000003400000002000000040000000200000003000000040000004080000004000000140000000100000026000000210000002400000005000000"
        .hex()
    val notification =
      response.copyOf().also {
        it[16] = 3
        it[28] = 0x11
        it[29] = 0
      }
    val expected = ShokzLocalAudioState(1u, 38u, 33u, 36u, 5u)
    assertEquals(expected, parseShokzLocalAudioState(response))
    assertEquals(expected, parseShokzLocalAudioState(notification))
    assertFailsWith<IllegalArgumentException> {
      parseShokzLocalAudioState(response.dropLast(1).toByteArray())
    }
    assertFailsWith<IllegalArgumentException> {
      parseShokzLocalAudioState(response.copyOf().also { it[28] = 0x41 })
    }
  }

  private fun String.hex(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
