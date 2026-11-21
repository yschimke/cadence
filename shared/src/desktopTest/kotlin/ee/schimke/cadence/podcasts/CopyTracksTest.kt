package ee.schimke.cadence.podcasts

import java.io.File
import java.nio.file.Files
import kotlin.test.*
import kotlinx.coroutines.runBlocking

class CopyTracksTest {
  private val root = Files.createTempDirectory("copy-tracks").toFile()

  @AfterTest fun cleanUp() = root.deleteRecursively().let {}

  private val platform =
    object : DesktopPodcastPlatform(root) {
      suspend fun copy(episode: Episode, sections: List<Section>) = copyTracks(episode, sections)
    }

  @Test
  fun onlySectionsInTheTracksFormatAreCopied() = runBlocking {
    val episode =
      Episode(
        LIBRO_FM_BACKEND,
        "9780000000002",
        "Book",
        "https://libro.fm/audiobooks/9780000000002",
        10_000,
        revision = "librofm:0",
        audioVersion = "v",
        extension = "mp3",
        trackMs = listOf(4_000, 6_000),
      )
    val tracks = File(root, "source/${episode.key}-${digest(episode.audioVersion)}.tracks")
    tracks.mkdirs()
    File(tracks, "0000.mp3").writeText("first")
    File(tracks, "0001.mp3").writeText("second")
    val byChapter = segment(episode, Segmentation(split = SplitMode.Chapters))
    // The same first range, asked for as M4A by another split, must still be encoded.
    val id = digest("as m4a")
    val asM4a =
      byChapter[0].copy(id = id, fileName = "Book-${episode.key.take(8)}-000000000000-$id.m4a")
    val left = platform.copy(episode, byChapter + asM4a)
    assertEquals(listOf(asM4a), left)
    assertTrue(byChapter.all { platform.available(it) })
    assertFalse(platform.available(asM4a))
  }
}
