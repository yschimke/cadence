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
  fun entryNamesNeverBecomePaths() {
    val archive = zip("evil.zip", "../../escape.mp3" to "[x]".encodeToByteArray())
    val into = File(dir, "safe")
    val tracks = extractMp3Tracks(archive, into)
    assertTrue(tracks.single().parentFile == into)
    assertFalse(File(dir.parentFile, "escape.mp3").exists())
  }
}
