package ee.schimke.cadence.platform

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class WithoutMessagesTest {
  @Test
  fun keepsTypesAndFramesButDropsMessages() {
    val secret = "https://cdn.example/episode.mp3?token=abc123"
    val original = IllegalStateException("Copy failed for $secret", IOException("ENOSPC $secret"))
    val reported = withoutMessages(original)

    assertEquals(IllegalStateException::class.java.name, reported.message)
    assertEquals(IOException::class.java.name, reported.cause?.message)
    assertEquals(original.stackTrace.toList(), reported.stackTrace.toList())
    assertFalse(reported.stackTraceToString().contains("token"))
  }
}
