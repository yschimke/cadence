package ee.schimke.cadence.podcasts

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CloudSyncStatusTest {
  @Test
  fun sourceAndWorkAvailabilityReflectCachedEpisodes() {
    val first =
      Episode(
        "test",
        "1",
        "First",
        "https://example.com/1.mp3",
        60000,
        revision = "1",
        audioVersion = "1",
      )
    val second = first.copy(id = "2", title = "Second")
    val sections = segment(first, Segmentation())
    val state =
      LocalPodcastState(
        library =
          PodcastLibrary(
            playlist = PlaylistSnapshot(name = "Swim", episodes = listOf(first, second)),
            source = PodcastSourceKind.PocketCasts,
            manifests = listOf(Manifest(first, Segmentation(), sections)),
          ),
        downloadedEpisodeKeys = setOf(first.key),
        readySectionIds = sections.map { it.id }.toSet(),
      )
    val status = cloudSyncStatus(state, canRefresh = true, supportsDownload = true)
    assertEquals("Pocket Casts · Swim", status.source)
    assertEquals("1 downloaded · 1 to download", status.downloads)
    assertEquals("1 processed · 1 to process", status.processing)
    assertEquals(1, status.downloadedCount)
    assertEquals(1, status.downloadNeeded)
    assertEquals(1, status.processingNeeded)
    assertTrue(status.canRefresh && status.canDownload && status.canProcess)
    val busy = cloudSyncStatus(state, true, true, busy = true, progress = "Processing")
    assertFalse(busy.canRefresh || busy.canDownload || busy.canProcess)
    assertEquals("Processing", busy.progress)
  }

  @Test
  fun missingSourceOffersConnectionInsteadOfWork() {
    val status = cloudSyncStatus(LocalPodcastState(), true, true)
    assertFalse(status.hasSource || status.canRefresh || status.canDownload || status.canProcess)
    assertEquals("Connect podcasts", status.source)
  }
}
