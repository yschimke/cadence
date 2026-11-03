package ee.schimke.cadence.home

import ee.schimke.cadence.podcasts.Episode
import ee.schimke.cadence.podcasts.LocalPodcastState
import ee.schimke.cadence.podcasts.Manifest
import ee.schimke.cadence.podcasts.PlaylistSnapshot
import ee.schimke.cadence.podcasts.PodcastLibrary
import ee.schimke.cadence.podcasts.Section
import ee.schimke.cadence.podcasts.Segmentation
import ee.schimke.cadence.podcasts.upToDateOnDevice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HeadphoneSyncStepTest {
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
  private val section = Section("s1", episode.key, "v1", 0, 60_000, "file.m4a")
  private val imported =
    LocalPodcastState(
      library =
        PodcastLibrary(playlist = PlaylistSnapshot(name = "Up Next", episodes = listOf(episode)))
    )
  private val prepared =
    imported.copy(
      library =
        imported.library.copy(
          manifests = listOf(Manifest(episode, Segmentation(), listOf(section)))
        ),
      readySectionIds = setOf("s1"),
    )

  @Test
  fun firstRunAsksForASource() {
    assertEquals(HeadphoneSyncStep.NoSource, headphoneSyncStep(LocalPodcastState(), false, false))
  }

  @Test
  fun setupStepsAppearUntilTheStorageIsChosen() {
    assertEquals(HeadphoneSyncStep.PlugInToSetUp, headphoneSyncStep(prepared, false, false))
    assertEquals(HeadphoneSyncStep.ChooseStorage, headphoneSyncStep(prepared, false, true))
  }

  @Test
  fun dailyRoutineMovesFromPrepareToCopyToUpToDate() {
    assertEquals(
      HeadphoneSyncStep.Prepare(pluggedIn = false),
      headphoneSyncStep(imported.copy(target = "t"), false, false),
    )
    val ready = prepared.copy(target = "t")
    assertEquals(
      HeadphoneSyncStep.ReadyToCopy(pluggedIn = true),
      headphoneSyncStep(ready, false, true),
    )
    val copied = ready.copy(copiedSectionIds = setOf("s1"))
    assertTrue(copied.upToDateOnDevice())
    assertEquals(
      HeadphoneSyncStep.UpToDate(pluggedIn = true),
      headphoneSyncStep(copied, false, true),
    )
  }

  @Test
  fun copiesOnConnectOnlyWhenReadyAndNotYetCopied() {
    val ready = prepared.copy(target = "t")
    assertTrue(shouldCopyOnConnect(headphoneSyncStep(ready, false, true)))
    assertFalse(shouldCopyOnConnect(headphoneSyncStep(ready, false, false)))
    assertFalse(
      shouldCopyOnConnect(
        headphoneSyncStep(ready.copy(copiedSectionIds = setOf("s1")), false, true)
      )
    )
    assertFalse(shouldCopyOnConnect(headphoneSyncStep(prepared, false, true)))
  }

  @Test
  fun foldersOnlyPointAtTheFolderCopy() {
    assertEquals(
      HeadphoneSyncStep.Folders(pluggedIn = false),
      headphoneSyncStep(LocalPodcastState(), true, false),
    )
  }
}
