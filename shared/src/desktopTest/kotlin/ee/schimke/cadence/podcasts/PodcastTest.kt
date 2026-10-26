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
    controller.importSnapshot(
      podcastJson.encodeToString(PlaylistSnapshot(name = "Wanted", episodes = listOf(episode)))
    )
    controller.prepare()
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
    controller.importSnapshot(
      podcastJson.encodeToString(PlaylistSnapshot(name = "Wanted", episodes = listOf(episode)))
    )
    controller.prepare()
    controller.setTarget("private-local-usb-path")
    controller.configureCloud("https://example.supabase.co", "publishable-test-key")
    controller.signIn("fixture@example.com", "fixture-password")
    controller.pushCloud()
    assertFalse(platform.lastPush.contains("private-local-usb-path"))
    assertFalse(platform.lastPush.contains("readySectionIds"))
    assertFalse(platform.lastPush.contains("fixture-password"))
    assertEquals(1L, controller.state.value.cloudRevision)
    platform.rejectPush = true
    controller.run { pushCloud() }
    assertEquals(1L, controller.state.value.cloudRevision)
    assertEquals("private-local-usb-path", controller.state.value.target)
    assertEquals(2, controller.state.value.readySectionIds.size)
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

  private class FakePlatform : PodcastPlatform {
    override val splitFormats = setOf("mp3", "m4a")
    override val description = "Fixture"
    var saved: String? = null
    var failPreparation = false
    var exported = false
    var rejectPush = false
    var lastPush = ""
    var remoteDocument: CloudDocument? = null
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

    override suspend fun rss(url: String): PlaylistSnapshot = error("not used")

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
