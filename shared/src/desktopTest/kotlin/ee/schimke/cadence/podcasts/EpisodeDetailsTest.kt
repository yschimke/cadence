package ee.schimke.cadence.podcasts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EpisodeDetailsTest {
  @Test
  fun rssMetadataIsPreservedInImportedEpisodes() {
    val item =
      RssItem(
        "id",
        "Episode",
        "https://example.com/audio.mp3",
        "60",
        "10",
        "audio/mpeg",
        "<p>Show notes</p>",
        "https://example.com/transcript.vtt",
        "Today",
      )
    val episode =
      rssSnapshot("https://example.com/feed", RssFeed("Show", listOf(item)), 1)
        .snapshot
        .episodes
        .single()
    assertEquals("<p>Show notes</p>", episode.summary)
    assertEquals("https://example.com/transcript.vtt", episode.transcriptUrl)
    assertEquals("Today", episode.published)
  }

  @Test
  fun extractsOnlyTheSelectedEpisodesNotesAndHttpsTranscript() {
    val details =
      pocketCastsDetails(
        """{"podcast":{"episodes":[{"uuid":"other","show_notes":"wrong"},{"uuid":"episode","show_notes":"<p>Hello &amp; welcome</p><p>More</p>","transcripts":[{"url":"javascript:bad"},{"url":"https://example.com/transcript.vtt"}]}]}}""",
        "episode",
      )
    assertEquals("Hello & welcome\nMore", details.summary)
    assertEquals("https://example.com/transcript.vtt", details.transcriptUrl)
  }

  @Test
  fun missingEpisodeDoesNotBorrowOtherMetadata() {
    val details =
      pocketCastsDetails(
        """{"podcast":{"episodes":[{"uuid":"other","transcripts":[{"url":"https://example.com"}]}]}}""",
        "missing",
      )
    assertEquals("", details.summary)
    assertNull(details.transcriptUrl)
  }
}
