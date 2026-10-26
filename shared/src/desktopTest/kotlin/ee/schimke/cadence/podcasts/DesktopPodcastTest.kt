package ee.schimke.cadence.podcasts

import java.io.File
import java.nio.file.Files
import kotlin.test.*
import kotlinx.coroutines.runBlocking

class DesktopPodcastTest {
  private val episode =
    Episode(
      "fixture",
      "one",
      "Test tone",
      "https://example.com/tone.wav",
      2_000,
      1_000,
      "1",
      "tone-v1",
      "wav",
    )

  @Test
  fun realAudioClippingAndOwnedFolderExport() = runBlocking {
    val root = Files.createTempDirectory("cadence-podcast-test").toFile()
    try {
      val fixture = File(root, "tone.wav")
      command(
        "ffmpeg",
        "-nostdin",
        "-v",
        "error",
        "-f",
        "lavfi",
        "-i",
        "sine=frequency=440:sample_rate=44100",
        "-t",
        "2",
        fixture.path,
      )
      val platform =
        object : DesktopPodcastPlatform(File(root, "app")) {
          override suspend fun download(episode: Episode, destination: File) {
            destination.parentFile!!.mkdirs()
            fixture.copyTo(destination, overwrite = true)
          }
        }
      val sections = segment(episode, Segmentation(chunkFormat = "m4a"))
      platform.prepare(episode, sections)
      assertTrue(sections.all { platform.available(it) })
      for (section in sections) {
        val output = File(root, "app/audio/${section.fileName}")
        val inspection = command("ffmpeg", "-nostdin", "-i", output.path, "-f", "null", "-")
        assertTrue(inspection.contains("Audio: aac"), inspection)
        assertTrue(Regex("Duration: 00:00:01\\.[0-9]{2}").containsMatchIn(inspection), inspection)
      }
      val target = File(root, "usb").apply { mkdirs() }
      File(target, "personal.mp3").writeText("keep me")
      platform.export(sections, target.path, "{\"schema\":1}")
      assertEquals("keep me", File(target, "personal.mp3").readText())
      assertEquals(
        "cadence-podcasts-v1",
        File(target, "Cadence/podcasts/.cadence-owner").readText(),
      )
      assertTrue(sections.all { File(target, "Cadence/podcasts/${it.fileName}").length() > 0 })
      assertTrue(
        sections[0].fileName < sections[1].fileName
      ) // Headphone filename order follows the timeline.
      File(target, "Cadence/podcasts/.cadence-owner").delete()
      assertFailsWith<IllegalArgumentException> { platform.export(sections, target.path, "{}") }
      assertEquals("keep me", File(target, "personal.mp3").readText())
      // MP3 is a genuine alternate encoding, never a renamed M4A.
      val mp3 = segment(episode, Segmentation(chunkFormat = "mp3"))
      platform.prepare(episode, mp3)
      assertTrue(
        command(
            "ffmpeg",
            "-nostdin",
            "-i",
            File(root, "app/audio/${mp3[0].fileName}").path,
            "-f",
            "null",
            "-",
          )
          .contains("Audio: mp3")
      )
    } finally {
      root.deleteRecursively()
    }
  }

  @Test
  fun exportRejectsSymlinkOutsideOwnedDirectory() = runBlocking {
    val root = Files.createTempDirectory("cadence-symlink-test").toFile()
    try {
      val outside = File(root, "outside").apply { mkdirs() }
      val target = File(root, "usb").apply { mkdirs() }
      Files.createSymbolicLink(File(target, "Cadence").toPath(), outside.toPath())
      assertFailsWith<IllegalArgumentException> {
        DesktopPodcastPlatform(File(root, "app")).export(emptyList(), target.path, "{}")
      }
      assertTrue(outside.listFiles()!!.isEmpty())
    } finally {
      root.deleteRecursively()
    }
  }

  private fun command(vararg args: String): String {
    val process = ProcessBuilder(*args).redirectErrorStream(true).start()
    val text = process.inputStream.bufferedReader().readText()
    check(process.waitFor() == 0) { text }
    return text
  }
}
