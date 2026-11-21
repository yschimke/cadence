package ee.schimke.cadence.podcasts

import kotlin.test.*

class ChapterSplitTest {
  private val episode =
    Episode(
      "rss",
      "1",
      "Show",
      "https://example.com/1.mp3",
      600_000,
      revision = "r",
      audioVersion = "v",
      chapterMs = listOf(120_000, 300_000),
    )

  private fun ranges(config: Segmentation, from: Episode = episode) =
    segment(from, config).map { it.startMs to it.endMs }

  @Test
  fun byChapterCutsAtEachChapterStart() {
    val chapters = Segmentation(split = SplitMode.Chapters, splitAtPosition = false)
    assertEquals(
      listOf(0L to 120_000L, 120_000L to 300_000L, 300_000L to 600_000L),
      ranges(chapters),
    )
    // Skipping what's heard starts at the position; earlier chapter starts drop out.
    assertEquals(
      listOf(200_000L to 300_000L, 300_000L to 600_000L),
      ranges(chapters.copy(skipListened = true), episode.copy(positionMs = 200_000)),
    )
    // No chapters: left whole, in its own format.
    val whole = segment(episode.copy(chapterMs = emptyList()), chapters)
    assertEquals(listOf(0L to 600_000L), whole.map { it.startMs to it.endMs })
    assertTrue(whole.single().fileName.endsWith(".mp3"))
  }

  @Test
  fun asIsAndByTimeIgnoreChapters() {
    val base = Segmentation(splitAtPosition = false, chunkMinutes = 4)
    assertEquals(listOf(0L to 600_000L), ranges(base.copy(split = SplitMode.Whole)))
    assertEquals(
      listOf(0L to 240_000L, 240_000L to 480_000L, 480_000L to 600_000L),
      ranges(base.copy(split = SplitMode.Time)),
    )
    // Settings saved before the choice existed split by time when a part length was set.
    assertEquals(SplitMode.Time, base.splitMode)
    assertEquals(SplitMode.Whole, base.copy(chunkMinutes = 0).splitMode)
  }

  @Test
  fun aPodcastCanOverrideTheLibrary() {
    val library =
      PodcastLibrary(
        config = Segmentation(split = SplitMode.Time, chunkMinutes = 4),
        podcastTrims = mapOf("feed:a" to PodcastTrim(split = SplitMode.Chapters)),
      )
    assertEquals(
      SplitMode.Chapters,
      library.configFor(episode.copy(podcastId = "feed:a")).splitMode,
    )
    assertEquals(SplitMode.Time, library.configFor(episode.copy(podcastId = "feed:b")).splitMode)
  }

  @Test
  fun feedChapterIndexesReadAsStarts() {
    val json =
      """{"version":"1.2.0","chapters":[
        {"startTime":0,"title":"Intro"},
        {"startTime":95.5,"title":"News"},
        {"startTime":200,"title":"Ad","toc":false},
        {"startTime":420,"title":"Interview"},
        {"startTime":9999,"title":"Past the end"}]}"""
    assertEquals(listOf(95_500L, 420_000L), podcastChapters(json, 600_000))
    assertEquals(emptyList(), podcastChapters("not json", 600_000))
  }

  @Test
  fun mp3ChapFramesReadAsStarts() {
    fun syncsafe(n: Int) =
      byteArrayOf(
        (n shr 21 and 0x7f).toByte(),
        (n shr 14 and 0x7f).toByte(),
        (n shr 7 and 0x7f).toByte(),
        (n and 0x7f).toByte(),
      )
    fun int(n: Int) =
      byteArrayOf((n shr 24).toByte(), (n shr 16).toByte(), (n shr 8).toByte(), n.toByte())
    fun chap(id: String, start: Int, end: Int): ByteArray {
      val body = id.encodeToByteArray() + 0 + int(start) + int(end) + int(-1) + int(-1)
      return "CHAP".encodeToByteArray() + syncsafe(body.size) + byteArrayOf(0, 0) + body
    }
    val frames =
      chap("ch0", 0, 95_500) + chap("ch1", 95_500, 420_000) + chap("ch2", 420_000, 600_000)
    val tag =
      "ID3".encodeToByteArray() +
        byteArrayOf(4, 0, 0) +
        syncsafe(frames.size + 20) +
        frames +
        ByteArray(20)
    assertEquals(listOf(0L, 95_500L, 420_000L), id3ChaptersOf(tag + ByteArray(100)))
    assertEquals(listOf(95_500L, 420_000L), chapterStarts(id3ChaptersOf(tag), 600_000))
  }

  @Test
  fun m4bChplReadsAsStarts() {
    fun int(n: Long, bytes: Int) = ByteArray(bytes) { (n shr (8 * (bytes - 1 - it))).toByte() }
    fun box(type: String, body: ByteArray) =
      int(8L + body.size, 4) + type.encodeToByteArray() + body
    fun chapter(ms: Long, title: String) =
      int(ms * 10_000, 8) + byteArrayOf(title.length.toByte()) + title.encodeToByteArray()
    val chpl =
      box(
        "chpl",
        byteArrayOf(1, 0, 0, 0) +
          int(0, 4) +
          byteArrayOf(3) +
          chapter(0, "One") +
          chapter(1_200_000, "Two") +
          chapter(2_400_000, "Three"),
      )
    val file =
      box("ftyp", "M4B ".encodeToByteArray()) +
        box("moov", box("mvhd", ByteArray(4)) + box("udta", chpl))
    assertEquals(listOf(0L, 1_200_000L, 2_400_000L), mp4ChaptersOf(file))
  }
}
