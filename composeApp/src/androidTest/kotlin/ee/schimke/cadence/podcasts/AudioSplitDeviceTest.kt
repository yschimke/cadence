package ee.schimke.cadence.podcasts

import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/**
 * Splitting into files on the real Android stack (run on the CI emulators): Libro.fm-style zip
 * parts joined into one MP3 with their original tracks kept, tracks copied as they are when split
 * by chapter, and chapter marks read from the audio and cut by Media3.
 */
class AudioSplitDeviceTest {
  private val context = InstrumentationRegistry.getInstrumentation().targetContext

  /** A ~2 s 64 kbps mono MP3 (ID3v2, an Info frame, then audio), as podcast tools write. */
  private val tone =
    InstrumentationRegistry.getInstrumentation().context.assets.open("podcast-tone.mp3").use {
      it.readBytes()
    }

  private val root = File(context.filesDir, "podcasts")
  private val keys = mutableListOf<String>()

  @After
  fun cleanUp() {
    for (dir in listOf("source", "audio")) File(root, dir).listFiles()?.forEach { file ->
      if (keys.any { file.name.startsWith(it) || file.name.contains("-${it.take(8)}-") })
        file.deleteRecursively()
    }
  }

  private fun id3Length(bytes: ByteArray) =
    10 +
      ((bytes[6].toInt() and 0x7f shl 21) or
        (bytes[7].toInt() and 0x7f shl 14) or
        (bytes[8].toInt() and 0x7f shl 7) or
        (bytes[9].toInt() and 0x7f))

  private fun syncsafe(n: Int) =
    byteArrayOf(
      (n shr 21 and 0x7f).toByte(),
      (n shr 14 and 0x7f).toByte(),
      (n shr 7 and 0x7f).toByte(),
      (n and 0x7f).toByte(),
    )

  private fun int(n: Int) =
    byteArrayOf((n shr 24).toByte(), (n shr 16).toByte(), (n shr 8).toByte(), n.toByte())

  /** An ID3v2.4 tag with a `CHAP` frame starting at each of [starts] ms. */
  private fun chapterTag(starts: List<Int>): ByteArray {
    val frames =
      starts
        .mapIndexed { index, start ->
          val body =
            "ch$index".encodeToByteArray() + 0 + int(start) + int(start + 1) + int(-1) + int(-1)
          "CHAP".encodeToByteArray() + syncsafe(body.size) + byteArrayOf(0, 0) + body
        }
        .reduce { a, b -> a + b }
    return "ID3".encodeToByteArray() + byteArrayOf(4, 0, 0) + syncsafe(frames.size) + frames
  }

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

  /** The platform, serving zip parts from memory instead of Libro.fm's signed links. */
  private fun platform(parts: Map<String, ByteArray>) =
    object : AndroidPodcastPlatform(context) {
      override suspend fun fetchTo(url: String, target: File, onProgress: (Long, Long) -> Unit) {
        target.writeBytes(parts.getValue(url))
      }
    }

  private fun book(id: String, parts: List<String>) =
    Episode(
        LIBRO_FM_BACKEND,
        id,
        "Device book $id",
        parts.first(),
        6_000,
        revision = "librofm:0",
        audioVersion = "v1",
        extension = "mp3",
        audioParts = parts,
      )
      .also { keys += it.key }

  private fun durationUs(file: File, mime: String): Long {
    val extractor = MediaExtractor()
    try {
      extractor.setDataSource(file.path)
      assertEquals(1, extractor.trackCount)
      val format = extractor.getTrackFormat(0)
      assertEquals(mime, format.getString(MediaFormat.KEY_MIME))
      return format.getLong(MediaFormat.KEY_DURATION)
    } finally {
      extractor.release()
    }
  }

  @Test
  fun zipPartsJoinAndKeepTheirTracksForChapterSplitting() = runBlocking {
    val parts =
      mapOf(
        "https://parts.example/1.zip" to
          zip(
            "Book/Track 2.mp3" to tone,
            "Book/Track 1.mp3" to tone,
            "Book/cover.jpg" to byteArrayOf(1),
          ),
        "https://parts.example/2.zip" to zip("Book/Track 3.mp3" to tone),
      )
    val platform = platform(parts)
    val episode = book("9780000000091", parts.keys.toList())
    withTimeout(60_000) { platform.downloadOriginal(episode) }

    // Three tracks, each measured from its frames at about two seconds.
    val lengths = platform.trackLengths(episode)
    assertEquals(3, lengths.size)
    assertTrue(lengths.toString(), lengths.all { it in 1_800L..2_300L })

    // The joined file plays as one MP3 of all three: Android's extractor reads its length.
    val joined = platform.originalForPlayback(episode)!!
    val total = durationUs(joined, "audio/mpeg") / 1000
    assertTrue("joined $total ms", total in lengths.sum() - 300..lengths.sum() + 300)

    // Split by chapter, each track goes on the headphones as the original file, untouched.
    val measured =
      episode.copy(trackMs = lengths, durationMs = lengths.sum(), audioParts = emptyList())
    val sections = segment(measured, Segmentation(split = SplitMode.Chapters))
    assertEquals(3, sections.size)
    assertTrue(sections.all { it.fileName.endsWith(".mp3") })
    withTimeout(60_000) { platform.prepare(measured, sections) }
    for (section in sections) {
      assertTrue(platform.available(section))
      assertArrayEquals(tone, File(root, "audio/${section.fileName}").readBytes())
    }
  }

  @Test
  fun chapterMarksInTheAudioAreCutByMedia3() = runBlocking {
    // The first track carries the book's chapter marks; joining keeps its tags.
    val first = chapterTag(listOf(0, 1_000, 3_000)) + tone.copyOfRange(id3Length(tone), tone.size)
    val parts =
      mapOf(
        "https://parts.example/chapters.zip" to
          zip("01.mp3" to first, "02.mp3" to tone, "03.mp3" to tone),
      )
    val platform = platform(parts)
    val episode = book("9780000000092", parts.keys.toList())
    withTimeout(60_000) { platform.downloadOriginal(episode) }
    val length = platform.trackLengths(episode).sum()

    assertEquals(listOf(0L, 1_000L, 3_000L), platform.chapters(episode))
    val chaptered =
      episode.copy(
        durationMs = length,
        audioParts = emptyList(),
        chapterMs = chapterStarts(platform.chapters(episode), length),
      )
    val sections =
      segment(
        chaptered,
        Segmentation(split = SplitMode.Chapters, chunkFormat = "m4a", splitAtPosition = false),
      )
    assertEquals(
      listOf(0L to 1_000L, 1_000L to 3_000L, 3_000L to length),
      sections.map { it.startMs to it.endMs },
    )
    withTimeout(120_000) { platform.prepare(chaptered, sections) }
    for (section in sections) {
      assertTrue(platform.available(section))
      val cut = durationUs(File(root, "audio/${section.fileName}"), "audio/mp4a-latm") / 1000
      val expected = section.endMs - section.startMs
      assertTrue("${section.startMs}: $cut ms for $expected", cut in expected - 150..expected + 150)
    }
  }
}
