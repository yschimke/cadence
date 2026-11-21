package ee.schimke.cadence.home

import ee.schimke.cadence.podcasts.Episode
import ee.schimke.cadence.podcasts.LocalPodcastState
import ee.schimke.cadence.podcasts.PlaylistSnapshot
import ee.schimke.cadence.podcasts.PodcastLibrary
import ee.schimke.cadence.podcasts.PodcastSourceKind
import ee.schimke.cadence.podcasts.cloudSyncStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SyncHeadlineTest {
  private val episode =
    Episode(
      backend = "rss",
      id = "1",
      title = "Episode",
      audioUrl = "https://example.com/1.mp3",
      durationMs = 60_000,
      revision = "r1",
      audioVersion = "v1",
    )
  private val imported =
    LocalPodcastState(
      library =
        PodcastLibrary(
          playlist = PlaylistSnapshot(name = "Swim Series", episodes = listOf(episode)),
          source = PodcastSourceKind.PocketCasts,
        ),
      target = "content://drive",
    )

  @Test
  fun preparingSaysHowMuchIsLeftToDownloadFirst() {
    val step = HeadphoneSyncStep.Prepare(pluggedIn = false)
    assertEquals("1 to download", syncHeadline(step, cloudSyncStatus(imported, true, true)))
    // Where downloads aren't separate (the browser), processing is what's left.
    assertEquals("1 to process", syncHeadline(step, cloudSyncStatus(imported, true, false)))
    assertTrue(step.needsAttention)
  }

  @Test
  fun copyingAndInSyncReadAsOneLine() {
    val cloud = cloudSyncStatus(imported, true, true)
    assertEquals("Ready to sync", syncHeadline(HeadphoneSyncStep.ReadyToCopy(true), cloud))
    assertEquals(
      "Not in sync · plug in to copy",
      syncHeadline(HeadphoneSyncStep.ReadyToCopy(false), cloud),
    )
    assertEquals("In sync", syncHeadline(HeadphoneSyncStep.UpToDate(false), cloud))
    assertFalse(HeadphoneSyncStep.UpToDate(true).needsAttention)
  }

  @Test
  fun theSourceLineNamesTheServiceAndList() {
    val line = syncSourceLine(cloudSyncStatus(imported, true, true))!!
    assertTrue(line.startsWith("Pocket Casts · Swim Series · checked "), line)
    assertNull(syncSourceLine(cloudSyncStatus(LocalPodcastState(), true, true)))
  }

  @Test
  fun theCardOffersTheStepsButtonsMainActionFirst() {
    val cloud = cloudSyncStatus(imported, true, true)
    fun ids(step: HeadphoneSyncStep, ejectable: Boolean = true, folders: Boolean = false) =
      statusButtons(step, cloud, ejectable, folders).map { it.id }
    assertEquals(listOf("sync"), ids(HeadphoneSyncStep.ReadyToCopy(true)))
    // Not plugged in: nothing to press yet.
    assertEquals(emptyList(), ids(HeadphoneSyncStep.ReadyToCopy(false)))
    assertEquals(listOf("download"), ids(HeadphoneSyncStep.Prepare(false)))
    assertEquals(listOf("eject", "sync-again"), ids(HeadphoneSyncStep.UpToDate(true)))
    assertEquals(listOf("sync-again"), ids(HeadphoneSyncStep.UpToDate(true), ejectable = false))
    // Folders sit beside the episode sync, once.
    assertEquals(
      listOf("sync", "folders"),
      ids(HeadphoneSyncStep.ReadyToCopy(true), folders = true),
    )
    assertEquals(listOf("folders"), ids(HeadphoneSyncStep.Folders(true), folders = true))
    assertTrue(
      statusButtons(HeadphoneSyncStep.ReadyToCopy(true), cloud, true, true).first().primary,
    )
  }
}
