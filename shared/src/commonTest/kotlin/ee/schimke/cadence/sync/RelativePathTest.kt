package ee.schimke.cadence.sync

import kotlin.test.*

class RelativePathTest {
  @Test
  fun acceptsNestedAudioPaths() {
    assertEquals("root/podcasts/episode.mp3", joinDirectory("root", "podcasts/episode.mp3"))
  }

  @Test
  fun rejectsTraversalAndPlatformAbsolutePaths() {
    listOf("", "../escape", "a/../../escape", "/etc/passwd", "C:/audio", "a\\b", "a//b", "a/./b")
      .forEach { assertFailsWith<IllegalArgumentException> { safeRelativePath(it) } }
  }
}
