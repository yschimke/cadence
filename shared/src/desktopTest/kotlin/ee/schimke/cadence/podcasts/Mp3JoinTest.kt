package ee.schimke.cadence.podcasts

import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.*

class Mp3JoinTest {
  private val dir = Files.createTempDirectory("mp3-join").toFile()

  @AfterTest fun cleanUp() = dir.deleteRecursively().let {}

  private fun zip(name: String, vararg entries: Pair<String, ByteArray>): File =
    File(dir, name).also { file ->
      ZipOutputStream(file.outputStream()).use { out ->
        entries.forEach { (entry, bytes) ->
          out.putNextEntry(ZipEntry(entry))
          out.write(bytes)
          out.closeEntry()
        }
      }
    }

  /** An ID3v2 header with a 4-byte body, as taggers put before each track. */
  private fun id3(): ByteArray =
    byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 4, 0, 0, 0, 0, 0, 4) +
      "tags".encodeToByteArray()

  /** An ID3v1 trailer: "TAG" and 125 more bytes. */
  private fun id3v1(): ByteArray = "TAG".encodeToByteArray() + ByteArray(125)

  @Test
  fun tracksJoinInNumberOrderWithoutTheirTags() {
    val archive =
      zip(
        "part-0.zip",
        "Book/Track 10.mp3" to id3() + "[ten]".encodeToByteArray(),
        "Book/Track 2.mp3" to id3() + "[two]".encodeToByteArray() + id3v1(),
        "Book/cover.jpg" to byteArrayOf(1, 2, 3),
        "Book/Track 1.mp3" to id3() + "[one]".encodeToByteArray(),
      )
    val tracks = extractMp3Tracks(archive, File(dir, "part-0"))
    assertEquals(3, tracks.size)
    val out = File(dir, "book.mp3")
    joinMp3Tracks(tracks, out)
    // The first track keeps its tags (they describe the book); later tags are left out.
    assertEquals(String(id3()) + "[one][two][ten]", String(out.readBytes()))
  }

  @Test
  fun eachTracksInfoFrameIsLeftOut() {
    // A LAME-encoded fixture: ID3v2, then an Info frame counting only its own frames.
    val tone = File("../tests/fixtures/podcast-tone.mp3").readBytes()
    val tags = tone.decodeToString(0, 3).let { if (it == "ID3") id3v2LengthForTest(tone) else 0 }
    val info = infoFrameLength(tone, tags)
    assertTrue(info > 0, "fixture should start with an Info frame")
    val a = File(dir, "a.mp3").apply { writeBytes(tone) }
    val b = File(dir, "b.mp3").apply { writeBytes(tone) }
    val out = File(dir, "joined.mp3")
    joinMp3Tracks(listOf(a, b), out)
    val joined = out.readBytes()
    // One ID3 header, no Info frames, and both tracks' audio.
    assertEquals(tags + 2 * (tone.size - tags - info), joined.size)
    assertEquals(-1, String(joined, Charsets.ISO_8859_1).indexOf("Info"))
    // Ordinary audio frames aren't mistaken for one.
    assertEquals(0, infoFrameLength(joined, tags))
  }

  private fun id3v2LengthForTest(bytes: ByteArray): Int =
    10 +
      ((bytes[6].toInt() and 0x7f) shl
        21 or
        ((bytes[7].toInt() and 0x7f) shl 14) or
        ((bytes[8].toInt() and 0x7f) shl 7) or
        (bytes[9].toInt() and 0x7f))

  @Test
  fun trackLengthsComeFromTheirFrames() {
    val tone = File("../tests/fixtures/podcast-tone.mp3").readBytes()
    val single = mp3DurationMs(tone)
    assertTrue(single > 0)
    val out = File(dir, "twice.mp3")
    joinMp3Tracks(
      listOf(
        File(dir, "a.mp3").apply { writeBytes(tone) },
        File(dir, "b.mp3").apply { writeBytes(tone) },
      ),
      out,
    )
    // Within a frame or two of rounding.
    assertTrue(kotlin.math.abs(mp3DurationMs(out.readBytes()) - 2 * single) <= 60)
  }

  @Test
  fun largeFilesAreMeasuredWithoutLoadingThem() {
    // Twenty joined copies span several 64 KB windows; streaming must agree with reading whole.
    val tone = File("../tests/fixtures/podcast-tone.mp3").readBytes()
    val tracks = (0 until 20).map { File(dir, "t$it.mp3").apply { writeBytes(tone) } }
    val out = File(dir, "long.mp3")
    joinMp3Tracks(tracks, out)
    assertTrue(out.length() > 3 * 64 * 1024)
    assertEquals(mp3DurationMs(out.readBytes()), mp3DurationMs(out))
    assertTrue(kotlin.math.abs(mp3DurationMs(out) - 20 * mp3DurationMs(tone)) <= 600)
  }

  @Test
  fun eachTrackIsItsOwnSection() {
    val episode =
      Episode(
        LIBRO_FM_BACKEND,
        "9780000000002",
        "Book",
        "https://libro.fm/audiobooks/9780000000002",
        10_000,
        positionMs = 4_000,
        revision = "librofm:0",
        audioVersion = "v",
        extension = "mp3",
        trackMs = listOf(3_000, 3_000, 4_100),
      )
    val keep = Segmentation(keepTracks = true, chunkMinutes = 5, skipListened = false)
    // Measured lengths that overrun the listed duration end with the episode.
    assertEquals(
      listOf(0L to 3_000L, 3_000L to 6_000L, 6_000L to 10_000L),
      segment(episode, keep).map { it.startMs to it.endMs },
    )
    // Skipping what's heard leaves out finished tracks but keeps the one in progress whole.
    assertEquals(
      listOf(3_000L to 6_000L, 6_000L to 10_000L),
      segment(episode, keep.copy(skipListened = true)).map { it.startMs to it.endMs },
    )
    // Without the option the book is cut by time as usual: at the position, not the tracks.
    assertEquals(
      listOf(0L to 4_000L, 4_000L to 10_000L),
      segment(episode, keep.copy(keepTracks = false, chunkMinutes = 0)).map {
        it.startMs to it.endMs
      },
    )
  }

  @Test
  fun entryNamesNeverBecomePaths() {
    val archive = zip("evil.zip", "../../escape.mp3" to "[x]".encodeToByteArray())
    val into = File(dir, "safe")
    val tracks = extractMp3Tracks(archive, into)
    assertTrue(tracks.single().parentFile == into)
    assertFalse(File(dir.parentFile, "escape.mp3").exists())
  }
}
