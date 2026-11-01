package ee.schimke.rcsp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ShokzLocalAudioNamesTest {
  // Captured response shape, with names replaced by short synthetic names.
  private val response =
    listOf(
        "01000000",
        "38000000",
        "02000000",
        "04000000",
        "03000000",
        "03000000",
        "04000000",
        "16000000",
        "04000000",
        "18000000",
        "0200",
        "01000000",
        "0500",
        "612e6d7033",
        "02000000",
        "0500",
        "622e6d7033",
      )
      .joinToString("")
      .chunked(2)
      .map { it.toInt(16).toByte() }
      .toByteArray()

  @Test
  fun parsesNamesAndIndices() {
    assertEquals(
      ShokzLocalAudioBatch(
        listOf(ShokzLocalAudioName(1u, "a.mp3"), ShokzLocalAudioName(2u, "b.mp3")),
        null,
      ),
      parseShokzLocalAudioNames(response),
    )
    val continued =
      (response + byteArrayOf(3, 0)).also {
        it[4] = 58
        it[36] = 26
      }
    assertEquals(3, parseShokzLocalAudioNames(continued).nextIndex)
  }

  @Test
  fun rejectsTruncatedOrDifferentResponses() {
    assertFailsWith<IllegalArgumentException> {
      parseShokzLocalAudioNames(response.dropLast(1).toByteArray())
    }
    assertFailsWith<IllegalArgumentException> {
      parseShokzLocalAudioNames(response.copyOf().also { it[28] = 0x15 })
    }
  }
}
