package ee.schimke.cadence.podcasts

import kotlin.test.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*

class PodcastTest {
  private val episode =
    Episode(
      "fixture",
      "episode-1",
      "An hour",
      "https://example.com/audio.mp3",
      3_600_000,
      1_800_000,
      "rev1",
      "audio-v1",
    )

  @Test
  fun segmentationPreservesExactOriginalTimeline() {
    val halves = segment(episode, Segmentation())
    assertEquals(
      listOf(0L to 1_800_000L, 1_800_000L to 3_600_000L),
      halves.map { it.startMs to it.endMs },
    )
    val chunks = segment(episode, Segmentation(chunkMinutes = 5))
    assertEquals(12, chunks.size)
    assertEquals(0L, chunks.first().startMs)
    assertEquals(episode.durationMs, chunks.last().endMs)
    assertTrue(chunks.zipWithNext().all { (a, b) -> a.endMs == b.startMs })
    assertEquals(chunks, segment(episode, Segmentation(chunkMinutes = 5)))
    assertEquals(1, segment(episode, Segmentation(false)).size)
    assertEquals(1, segment(episode.copy(positionMs = 0), Segmentation()).size)
    assertEquals(1, segment(episode.copy(positionMs = episode.durationMs), Segmentation()).size)
    val m4a = segment(episode, Segmentation(chunkFormat = "m4a"))
    assertTrue(m4a.all { it.fileName.endsWith(".m4a") })
    assertEquals(halves.map { it.id }, m4a.map { it.id }) // Completion is independent of encoding.
    assertNotEquals(
      halves.first().id,
      segment(episode.copy(audioVersion = "v2"), Segmentation()).first().id,
    )
  }

  @Test
  fun skippingListenedAudioStartsAtThePositionAndKeepsChunkBoundaries() {
    val rest = segment(episode, Segmentation(skipListened = true))
    assertEquals(listOf(1_800_000L to 3_600_000L), rest.map { it.startMs to it.endMs })
    // Same section identity as the second half of a split, so earlier acknowledgements still apply.
    assertEquals(segment(episode, Segmentation()).last().id, rest.single().id)
    val chunks = segment(episode, Segmentation(chunkMinutes = 10, skipListened = true))
    assertEquals(3, chunks.size)
    assertEquals(1_800_000L, chunks.first().startMs)
    assertEquals(1, segment(episode.copy(positionMs = 0), Segmentation(skipListened = true)).size)
    assertEquals(
      0L,
      segment(episode.copy(positionMs = 0), Segmentation(skipListened = true)).single().startMs,
    )
  }

  @Test
  fun irregularPositionChunksRemainContiguousAndShortTailIsKept() {
    val sections =
      segment(episode.copy(durationMs = 3_601_000, positionMs = 1_821_000), Segmentation(true, 5))
    assertTrue(sections.any { it.endMs == 1_821_000L })
    assertEquals(3_601_000L, sections.last().endMs)
    assertTrue(sections.all { it.endMs > it.startMs && it.endMs - it.startMs <= 300_000L })
    assertFailsWith<IllegalArgumentException> { segment(episode, Segmentation(chunkMinutes = -1)) }
  }

  @Test
  fun outOfOrderCompletionDoesNotSkipGapsOrReplayOldMedia() {
    val chunks = segment(episode, Segmentation(chunkMinutes = 5))
    fun completed(index: Int) =
      chunks[index].let {
        Completion(it.id, episode.revision, it.episodeKey, it.audioVersion, it.startMs, it.endMs)
      }
    assertEquals(1_800_000, advancedPosition(episode, listOf(completed(7))))
    assertEquals(
      episode.positionMs,
      advancedPosition(
        episode.copy(revision = "explicit-rewind"),
        listOf(completed(6), completed(7)),
      ),
    )
    assertEquals(
      2_400_000,
      advancedPosition(episode, listOf(completed(7), completed(6), completed(6))),
    )
    assertEquals(
      1_800_000,
      advancedPosition(episode.copy(audioVersion = "changed"), listOf(completed(6))),
    )
    assertEquals(
      2_500_000,
      advancedPosition(episode.copy(positionMs = 2_500_000), listOf(completed(6))),
    )
  }

  @Test
  fun failedPreparationNeverPublishesNewManifestOrReadiness() = runBlocking {
    val platform = FakePlatform()
    val controller = PodcastController(platform)
    controller.importSnapshot(
      podcastJson.encodeToString(PlaylistSnapshot(name = "Wanted", episodes = listOf(episode)))
    )
    platform.failPreparation = true
    controller.run { prepare() }
    assertTrue(controller.state.value.library.manifests.isEmpty())
    assertTrue(controller.state.value.readySectionIds.isEmpty())
    assertTrue(controller.status.value.contains("failed"))
    assertFalse(PocketCastsBackend.capabilities.positionWrites)
  }

  @Test
  fun roundTripRestoresMappingAndRechecksLocalFiles() = runBlocking {
    val platform = FakePlatform()
    val controller = PodcastController(platform)
    controller.configure(Segmentation(skipListened = false)) // Two sections: before and after.
    controller.importSnapshot(
      podcastJson.encodeToString(PlaylistSnapshot(name = "Wanted", episodes = listOf(episode)))
    )
    controller.prepare()
    assertTrue(controller.state.value.readyForDevice())
    val partial =
      controller.state.value.copy(
        readySectionIds = setOf(controller.state.value.readySectionIds.first())
      )
    assertFalse(partial.readyForDevice())
    val section = controller.state.value.library.manifests.single().sections.last()
    controller.markPlayed(section)
    controller.markPlayed(section)
    assertEquals(1, controller.state.value.library.completions.size)
    assertEquals(3_600_000, advancedPosition(episode, controller.state.value.library.completions))
    platform.files.clear()
    val restored = PodcastController(platform)
    restored.load()
    assertTrue(restored.state.value.readySectionIds.isEmpty())
    assertEquals(section, restored.state.value.library.manifests.single().sections.last())
    restored.setTarget("fixture-device")
    assertFailsWith<PodcastProblem> { restored.copyToDevice() }
    assertFalse(platform.exported)
  }

  @Test
  fun rssDurationsAndMalformedSnapshots() {
    assertEquals(3_661_500, rssDurationMs("1:01:01.5"))
    assertEquals(90_000, rssDurationMs("90"))
    assertFailsWith<IllegalArgumentException> { rssDurationMs("NaN") }
    assertFailsWith<IllegalArgumentException> { rssDurationMs("") }
    assertFailsWith<IllegalArgumentException> {
      PlaylistSnapshot(name = "A", episodes = listOf(episode, episode)).validate()
    }
    assertFailsWith<IllegalArgumentException> { episode.copy(positionMs = -1).validate() }
    assertFailsWith<IllegalArgumentException> { episode.copy(extension = "../mp3").validate() }
    assertFailsWith<IllegalArgumentException> {
      episode.copy(audioUrl = "http://example.com/audio").validate()
    }
  }

  @Test
  fun cloudSendsOnlyMetadataAndRequiresRevisionMatch() = runBlocking {
    val platform = FakePlatform()
    val controller = PodcastController(platform)
    platform.feed =
      RssFeed(
        "Wanted",
        listOf(RssItem("episode-1", "An hour", episode.audioUrl, "3600", "10", "audio/mpeg")),
      )
    controller.importRss("https://example.com/feed.xml")
    controller.prepare()
    controller.setTarget("private-local-usb-path")
    controller.configureCloud("https://example.supabase.co", "publishable-test-key")
    controller.signIn("fixture@example.com", "fixture-password")
    controller.pushCloud()
    assertTrue(platform.lastPush.contains("https://example.com/feed.xml"))
    assertFalse(platform.lastPush.contains("private-local-usb-path"))
    assertFalse(platform.lastPush.contains("readySectionIds"))
    assertFalse(platform.lastPush.contains("fixture-password"))
    assertEquals(1L, controller.state.value.cloudRevision)
    assertTrue(controller.signedIn.value)
    platform.rejectPush = true
    controller.run { pushCloud() }
    assertEquals(1L, controller.state.value.cloudRevision)
    assertEquals("private-local-usb-path", controller.state.value.target)
    // RSS has no account position, so the episode is one unsplit section.
    assertEquals(1, controller.state.value.readySectionIds.size)
    controller.signOut()
    assertFalse(controller.signedIn.value)
    assertEquals("private-local-usb-path", controller.state.value.target)
    assertFailsWith<IllegalArgumentException> { controller.pushCloud() }
    Unit
  }

  @Test
  fun cloudRestoreMergesAcknowledgementsAndRechecksNewManifests() = runBlocking {
    val platform = FakePlatform()
    val controller = PodcastController(platform)
    val playlist = PlaylistSnapshot(name = "Wanted", episodes = listOf(episode))
    controller.importSnapshot(podcastJson.encodeToString(playlist))
    controller.prepare()
    controller.markPlayed(controller.state.value.library.manifests.single().sections.last())
    controller.setTarget("local-usb")
    controller.configureCloud("https://example.supabase.co", "publishable-test-key")
    controller.signIn("fixture@example.com", "fixture-password")
    val remoteConfig = Segmentation(chunkMinutes = 5)
    val remoteSections = segment(episode, remoteConfig)
    val remotePlayed =
      remoteSections.first().let {
        Completion(it.id, episode.revision, it.episodeKey, it.audioVersion, it.startMs, it.endMs)
      }
    platform.remoteDocument =
      CloudDocument(
        4,
        PodcastLibrary(
          playlist = playlist,
          config = remoteConfig,
          manifests = listOf(Manifest(episode, remoteConfig, remoteSections)),
          completions = listOf(remotePlayed),
        ),
      )
    controller.pullCloud()
    assertEquals(4L, controller.state.value.cloudRevision)
    assertEquals(remoteConfig, controller.state.value.library.config)
    assertEquals(2, controller.state.value.library.completions.size)
    assertTrue(controller.state.value.readySectionIds.isEmpty())
    assertEquals("local-usb", controller.state.value.target)
    assertFalse(platform.saved!!.contains("fixture-token"))
    assertFalse(platform.saved!!.contains("fixture-password"))
    assertFailsWith<PodcastProblem> {
      controller.configureCloud("https://example.supabase.co", "sb_secret_do-not-use")
    }
    Unit
  }

  @Test
  fun rssKeepsNewestUsableItemsAndSkipsBrokenOnes() = runBlocking {
    fun item(n: Int, duration: String = "60", url: String = "https://example.com/$n.mp3") =
      RssItem("guid-$n", "Episode $n", url, duration, "100", "audio/mp4")
    val feed =
      RssFeed(
        "Show",
        listOf(
          item(1, duration = ""),
          item(2, url = "http://example.com/2.mp3"),
          item(3, url = ""),
          item(4),
          item(4),
          item(5, duration = "1:02:03"),
          item(6),
          item(7),
        ),
      )
    val imported = rssSnapshot("https://example.com/feed", feed, latest = 3)
    assertEquals(
      listOf("Episode 4", "Episode 5", "Episode 6"),
      imported.snapshot.episodes.map { it.title },
    )
    assertEquals(4, imported.skipped)
    assertEquals(3_723_000L, imported.snapshot.episodes[1].durationMs)
    assertEquals("m4a", imported.snapshot.episodes[0].extension)
    assertFailsWith<PodcastProblem> {
      rssSnapshot("https://example.com/feed", RssFeed("", listOf(item(1, ""))), 3)
    }
    assertFailsWith<IllegalArgumentException> { rssSnapshot("https://example.com/feed", feed, 0) }

    val platform = FakePlatform().apply { this.feed = feed }
    val controller = PodcastController(platform)
    controller.run { importRss("https://example.com/feed", 2) }
    assertEquals(2, controller.state.value.library.playlist!!.episodes.size)
    assertEquals(2, controller.state.value.library.feedLatest)
    assertTrue(controller.status.value.contains("skipped"), controller.status.value)
  }

  @Test
  fun deviceManifestOmitsUrlsEvenWhenProviderIdsContainThem() {
    val source = episode.copy(id = "https://publisher.example/private-id?access=example-only")
    val config = Segmentation(false)
    val manifest =
      deviceManifest(
        PodcastLibrary(manifests = listOf(Manifest(source, config, segment(source, config))))
      )
    val encoded = podcastJson.encodeToString(manifest)
    assertFalse(encoded.contains("https://"))
    assertFalse(encoded.contains("access="))
    assertEquals(source.key, manifest.episodes.single().episodeKey)
  }

  private class FakePlatform : PodcastPlatform {
    override val splitFormats = setOf("mp3", "m4a")
    override val description = "Fixture"
    var saved: String? = null
    var failPreparation = false
    var exported = false
    var rejectPush = false
    var lastPush = ""
    var remoteDocument: CloudDocument? = null
    var feed: RssFeed? = null
    val files = mutableSetOf<String>()

    override suspend fun readState() = saved

    override suspend fun saveState(state: String) {
      saved = state
    }

    override suspend fun prepare(episode: Episode, sections: List<Section>) {
      if (failPreparation) error("download interrupted")
      files += sections.map { it.id }
    }

    override suspend fun available(section: Section) = section.id in files

    override suspend fun export(sections: List<Section>, target: String, manifest: String) {
      exported = true
    }

    override suspend fun rss(url: String): RssFeed = requireNotNull(feed)

    override suspend fun request(
      url: String,
      method: String,
      headers: Map<String, String>,
      body: String?,
    ): String =
      when {
        url.contains("/auth/") ->
          """{"access_token":"fixture-token","user":{"id":"fixture-user"}}"""
        url.endsWith("cadence_put") -> {
          if (rejectPush) error("conflict")
          lastPush = body!!
          "1"
        }
        else -> remoteDocument?.let { podcastJson.encodeToString(it) } ?: "null"
      }
  }
}
