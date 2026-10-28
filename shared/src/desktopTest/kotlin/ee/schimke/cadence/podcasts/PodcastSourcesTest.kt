package ee.schimke.cadence.podcasts

import kotlin.test.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*

class PodcastSourcesTest {
  private val feedUrl = "https://feeds.example/show"
  private val feed =
    RssFeed(
      "Show",
      listOf(
        RssItem("g1", "One", "https://cdn.example/one.mp3?ad=2", "1:00:00", "10", "audio/mpeg"),
        RssItem("g2", "Two", "https://cdn.example/two.mp3", "1800", "20", "audio/mpeg"),
        RssItem("g3", "Three", "https://cdn.example/three.m4a", "600", "30", "audio/mp4"),
      ),
    )

  @Test
  fun overcastExtendedOpmlYieldsUnfinishedEpisodesNewestFirst() {
    val opml =
      """<?xml version="1.0"?><opml version="1.0"><body><outline text="feeds">
      <outline type="rss" title="Show &amp; Tell" xmlUrl="$feedUrl">
        <outline type="podcast-episode" title="One" enclosureUrl="https://track.example/x/cdn.example/one.mp3" progress="900" played="0" userUpdatedDate="2026-10-01T10:00:00-04:00"/>
        <outline type="podcast-episode" title="Two" enclosureUrl="https://cdn.example/two.mp3" progress="60" played="0" userUpdatedDate="2026-10-20T10:00:00-04:00"/>
        <outline type="podcast-episode" title="Done" enclosureUrl="https://cdn.example/done.mp3" progress="50" played="1" userUpdatedDate="2026-10-21T10:00:00-04:00"/>
        <outline type="podcast-episode" title="Fresh" enclosureUrl="https://cdn.example/fresh.mp3" progress="0" played="0"/>
      </outline></outline></body></opml>"""
    val progress = overcastProgress(opml, 5)
    assertEquals(listOf("Two", "One"), progress.map { it.title })
    assertEquals(900_000L, progress[1].positionMs)
    assertEquals(feedUrl, progress[0].feedUrl)
    assertFailsWith<PodcastProblem> {
      overcastProgress("""<opml><body><outline type="rss" xmlUrl="$feedUrl"/></body></opml>""", 5)
    }
  }

  @Test
  fun gpodderKeepsLatestUnfinishedPlayPerEpisode() {
    val body =
      """{"timestamp":12,"actions":[
      {"podcast":"$feedUrl","episode":"https://cdn.example/two.mp3","action":"play","timestamp":"2026-10-01T00:00:00","started":0,"position":100,"total":1800},
      {"podcast":"$feedUrl","episode":"https://cdn.example/two.mp3","action":"PLAY","timestamp":"2026-10-02T00:00:00","started":100,"position":300,"total":1800},
      {"podcast":"$feedUrl","episode":"https://cdn.example/three.m4a","action":"play","timestamp":"2026-10-03T00:00:00","position":595,"total":600},
      {"podcast":"$feedUrl","episode":"https://cdn.example/one.mp3","action":"download","timestamp":"2026-10-04T00:00:00"},
      {"podcast":"$feedUrl","episode":"https://cdn.example/one.mp3","action":"play","timestamp":"2026-09-04T00:00:00","position":-1,"total":-1}
      ]}"""
    val progress = gpodderProgress(gpodderPlays(body), 5)
    assertEquals(1, progress.size) // three.m4a is within 30 s of the end; one.mp3 has no position.
    assertEquals(300_000L, progress.single().positionMs)
    assertEquals(1_800_000L, progress.single().durationMs)
    assertEquals("gpodder:2026-10-02T00:00:00:300", progress.single().revision)
  }

  @Test
  fun resolvingMatchesByGuidUrlOrFileNameAndRefusesChangedAudio() = runBlocking {
    val platform = SourcesPlatform(feed)
    fun remote(audio: String, guid: String = "", duration: Long? = null) =
      RemoteProgress(feedUrl, audio, guid, 60_000, duration, "", "r")
    val resolved =
      resolveProgress(
        platform,
        "gpodder",
        "In progress",
        listOf(
          remote(
            "https://track.example/x/cdn.example/one.mp3"
          ), // file name after a tracking prefix
          remote("https://elsewhere.example/a", guid = "g3"),
          remote("https://cdn.example/two.mp3", duration = 2_400_000), // 40 min, feed says 30
          remote("https://cdn.example/missing.mp3"),
        ),
      )
    assertEquals(listOf("One", "Three"), resolved.snapshot.episodes.map { it.title })
    assertEquals("https://cdn.example/one.mp3?ad=2", resolved.snapshot.episodes[0].audioUrl)
    assertEquals(2, resolved.skipped)
    assertEquals(1, platform.feedReads) // One fetch per feed.
  }

  @Test
  fun gpodderImportSendsBackOnlyUnchangedPositionsWithoutStoringThePassword() = runBlocking {
    val platform = SourcesPlatform(feed)
    platform.actions =
      """{"actions":[{"podcast":"$feedUrl","episode":"https://cdn.example/two.mp3","action":"play","timestamp":"2026-10-02T00:00:00","position":600,"total":1800}]}"""
    val controller = PodcastController(platform)
    val account = GpodderAccount(GpodderFlavor.Nextcloud, "https://cloud.example", "listener")
    controller.importGpodder(account, "app-password", 3)
    val episode = controller.state.value.library.playlist!!.episodes.single()
    assertEquals(600_000L, episode.positionMs)
    assertTrue(platform.lastUrl.endsWith("/index.php/apps/gpoddersync/episode_action?since=0"))
    assertFalse(platform.saved!!.contains("app-password"))

    controller.prepare()
    val sections = controller.state.value.library.manifests.single().sections
    assertFailsWith<PodcastProblem> { controller.sendPosition(episode.key) } // nothing played yet
    controller.markPlayed(sections.last())
    controller.sendPosition(episode.key)
    val sent = Json.parseToJsonElement(platform.uploads.single()).jsonArray.single().jsonObject
    assertEquals(1800, sent.getValue("position").jsonPrimitive.int)
    assertEquals("https://cdn.example/two.mp3", sent.getValue("episode").jsonPrimitive.content)
    assertEquals(feedUrl, sent.getValue("podcast").jsonPrimitive.content)

    // Another app moved the episode: refuse rather than overwrite.
    platform.actions = platform.actions.replace("\"position\":600", "\"position\":900")
    assertFailsWith<PodcastProblem> { controller.sendPosition(episode.key) }
    assertEquals(1, platform.uploads.size)

    controller.forgetGpodderPassword()
    assertFailsWith<PodcastProblem> { controller.sendPosition(episode.key) }
    Unit
  }

  private class SourcesPlatform(private val feed: RssFeed) : PodcastPlatform {
    override val splitFormats = setOf("mp3", "m4a")
    override val description = "Fixture"
    var saved: String? = null
    var actions = """{"actions":[]}"""
    var lastUrl = ""
    var feedReads = 0
    val uploads = mutableListOf<String>()
    val files = mutableSetOf<String>()

    override suspend fun readState() = saved

    override suspend fun saveState(state: String) {
      saved = state
    }

    override suspend fun request(
      url: String,
      method: String,
      headers: Map<String, String>,
      body: String?,
    ): String {
      assertTrue(headers.getValue("Authorization").startsWith("Basic "))
      lastUrl = url
      if (method == "POST") uploads += body!!
      return if (method == "POST") """{"timestamp":1}""" else actions
    }

    override suspend fun rss(url: String): RssFeed {
      feedReads++
      return feed
    }

    override suspend fun prepare(episode: Episode, sections: List<Section>) {
      files += sections.map { it.id }
    }

    override suspend fun available(section: Section) = section.id in files

    override suspend fun export(sections: List<Section>, target: String, manifest: String) {}
  }
}
