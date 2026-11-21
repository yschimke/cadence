package ee.schimke.cadence.podcasts

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.*
import kotlinx.coroutines.runBlocking

/**
 * Splitting into files through the desktop platform, end to end: Libro.fm-style zip parts joined
 * with their original tracks kept, tracks copied as they are when split by chapter, and chapter
 * marks read from the joined audio. None of this needs FFmpeg; cutting at chapters does, and is
 * covered with FFmpeg's other cuts in [DesktopPodcastTest].
 */
class DesktopAudioSplitTest {
  private val root = Files.createTempDirectory("desktop-split").toFile()
  private val tone = File("../tests/fixtures/podcast-tone.mp3").readBytes()

  @AfterTest fun cleanUp() = root.deleteRecursively().let {}

  private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray =
    ByteArrayOutputStream()
      .also { bytes ->
        ZipOutputStream(bytes).use { out ->
          entries.forEach { (name, data) ->
            out.putNextEntry(ZipEntry(name))
            out.write(data)
            out.closeEntry()
          }
        }
      }
      .toByteArray()

  private fun platform(parts: Map<String, ByteArray>) =
    object : DesktopPodcastPlatform(root) {
      override suspend fun fetchTo(url: String, target: File, onProgress: (Long, Long) -> Unit) {
        target.writeBytes(parts.getValue(url))
      }
    }

  private fun book(parts: List<String>) =
    Episode(
      LIBRO_FM_BACKEND,
      "9780000000093",
      "Desktop book",
      parts.first(),
      6_000,
      revision = "librofm:0",
      audioVersion = "v1",
      extension = "mp3",
      audioParts = parts,
    )

  @Test
  fun zipPartsJoinAndTheirTracksCopyAsTheyAre() = runBlocking {
    val parts =
      mapOf(
        "https://parts.example/1.zip" to
          zip("Book/Track 2.mp3" to tone, "Book/Track 1.mp3" to tone),
        "https://parts.example/2.zip" to zip("Book/Track 3.mp3" to tone),
      )
    val platform = platform(parts)
    val episode = book(parts.keys.toList())
    platform.downloadOriginal(episode)
    assertTrue(platform.originalAvailable(episode))

    val lengths = platform.trackLengths(episode)
    assertEquals(3, lengths.size)
    assertTrue(lengths.all { it in 1_800L..2_300L }, lengths.toString())
    // The joined file is all three tracks' audio, measured the same way.
    val joined = File(root, "source").listFiles()!!.single { it.isFile && it.name.endsWith(".mp3") }
    assertTrue(kotlin.math.abs(mp3DurationMs(joined) - lengths.sum()) <= 100)

    val measured =
      episode.copy(trackMs = lengths, durationMs = lengths.sum(), audioParts = emptyList())
    val sections = segment(measured, Segmentation(split = SplitMode.Chapters))
    platform.prepare(measured, sections)
    for (section in sections) assertContentEquals(
      tone,
      File(root, "audio/${section.fileName}").readBytes(),
    )

    // A book that leaves the library takes its kept tracks with it.
    platform.pruneAudio(emptyList())
    assertTrue(File(root, "source").listFiles()!!.isEmpty())
  }

  @Test
  fun chapterMarksAreReadFromTheJoinedAudio() = runBlocking {
    fun syncsafe(n: Int) =
      byteArrayOf(
        (n shr 21 and 0x7f).toByte(),
        (n shr 14 and 0x7f).toByte(),
        (n shr 7 and 0x7f).toByte(),
        (n and 0x7f).toByte(),
      )
    fun int(n: Int) =
      byteArrayOf((n shr 24).toByte(), (n shr 16).toByte(), (n shr 8).toByte(), n.toByte())
    val frames =
      listOf(0, 1_000, 3_000)
        .mapIndexed { index, start ->
          val body =
            "ch$index".encodeToByteArray() + 0 + int(start) + int(start + 1) + int(-1) + int(-1)
          "CHAP".encodeToByteArray() + syncsafe(body.size) + byteArrayOf(0, 0) + body
        }
        .reduce { a, b -> a + b }
    val tag = "ID3".encodeToByteArray() + byteArrayOf(4, 0, 0) + syncsafe(frames.size) + frames
    val toneAudio =
      tone.copyOfRange(
        10 + ((tone[8].toInt() and 0x7f) shl 7 or (tone[9].toInt() and 0x7f)),
        tone.size,
      )
    val parts =
      mapOf("https://parts.example/c.zip" to zip("01.mp3" to tag + toneAudio, "02.mp3" to tone))
    val platform = platform(parts)
    val episode = book(parts.keys.toList())
    platform.downloadOriginal(episode)
    assertEquals(listOf(0L, 1_000L, 3_000L), platform.chapters(episode))
  }
}
