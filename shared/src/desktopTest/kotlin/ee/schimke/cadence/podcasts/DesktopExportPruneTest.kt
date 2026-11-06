package ee.schimke.cadence.podcasts

import java.io.File
import java.nio.file.Files
import kotlin.test.*
import kotlinx.coroutines.runBlocking

class DesktopExportPruneTest {
  private fun section(n: Int): Section {
    val id = n.toString().padStart(64, '0')
    return Section(id, "ep$n", "v1", 0, 1_000, "ep$n-000000000000-$id.m4a")
  }

  private fun manifest(vararg shows: Pair<String, Section>) =
    podcastJson.encodeToString(
      DeviceManifest(
        episodes =
          shows.map { (folder, section) ->
            DeviceEpisode(
              "rss",
              section.episodeKey,
              "Episode",
              "v1",
              listOf(section),
              folder = folder,
            )
          }
      )
    )

  @Test
  fun syncDeletesEpisodesThatLeftAndLeavesOtherFilesAlone() = runBlocking {
    val root = Files.createTempDirectory("cadence-prune-test").toFile()
    try {
      val platform = object : DesktopPodcastPlatform(File(root, "app")) {}
      val (a, b, c) = listOf(section(1), section(2), section(3))
      File(root, "app/audio").mkdirs()
      listOf(a, b, c).forEach { File(root, "app/audio/${it.fileName}").writeText("audio") }
      val volume = File(root, "usb").apply { mkdirs() }
      val podcasts = File(volume, "Cadence/podcasts")

      platform.export(listOf(a, b), volume.path, manifest("ShowA" to a, "ShowB" to b))
      assertTrue(File(podcasts, "ShowA/${a.fileName}").isFile)
      assertTrue(File(podcasts, "ShowB/${b.fileName}").isFile)
      // Not Cadence's: a differently named file in an owned folder, and an unowned folder.
      File(podcasts, "ShowA/notes.txt").writeText("mine")
      File(podcasts, "Other").mkdirs()
      File(podcasts, "Other/${b.fileName}").writeText("mine")

      platform.export(listOf(a, c), volume.path, manifest("ShowA" to a, "ShowC" to c))

      assertTrue(File(podcasts, "ShowA/${a.fileName}").isFile)
      assertTrue(File(podcasts, "ShowC/${c.fileName}").isFile)
      assertFalse(File(podcasts, "ShowB").exists(), "an emptied show folder goes too")
      assertTrue(File(podcasts, "ShowA/notes.txt").isFile)
      assertTrue(File(podcasts, "Other/${b.fileName}").isFile)
    } finally {
      root.deleteRecursively()
    }
  }
}
