package ee.schimke.cadence.home

import ee.schimke.cadence.home.HeadphoneSyncChange.Kind
import ee.schimke.cadence.podcasts.Episode
import ee.schimke.cadence.podcasts.LocalPodcastState
import ee.schimke.cadence.podcasts.Manifest
import ee.schimke.cadence.podcasts.PlaylistSnapshot
import ee.schimke.cadence.podcasts.PodcastLibrary
import ee.schimke.cadence.podcasts.Section
import ee.schimke.cadence.podcasts.Segmentation
import ee.schimke.cadence.podcasts.deviceManifest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HeadphoneSyncChangesTest {
  private fun episode(id: String, version: String = "v1") =
    Episode(
      backend = "rss",
      id = id,
      title = "Episode $id",
      audioUrl = "https://example.com/$id.mp3",
      durationMs = 600_000,
      revision = "r1",
      audioVersion = version,
    )

  private fun manifest(episode: Episode, startMs: Long = 0) =
    Manifest(
      episode,
      Segmentation(),
      listOf(
        Section(
          "${episode.id}-${episode.audioVersion}-$startMs",
          episode.key,
          episode.audioVersion,
          startMs,
          600_000,
          "f.m4a",
        ),
      ),
    )

  private fun state(vararg manifests: Manifest) =
    LocalPodcastState(
      library =
        PodcastLibrary(
          playlist = PlaylistSnapshot(name = "Up Next", episodes = manifests.map { it.episode }),
          manifests = manifests.toList(),
        ),
    )

  @Test
  fun nothingChangesRightAfterACopy() {
    val current = state(manifest(episode("a")))
    assertTrue(
      headphoneSyncChanges(current.copy(lastCopied = deviceManifest(current.library))).isEmpty(),
    )
  }

  @Test
  fun everythingIsNewWithoutAPreviousCopy() {
    val changes = headphoneSyncChanges(state(manifest(episode("a")), manifest(episode("b"))))
    assertEquals(listOf(Kind.Add, Kind.Add), changes.map { it.kind })
    assertEquals("2 new", changes.summary())
  }

  @Test
  fun reportsNewUpdatedAndRemovedEpisodesInThatOrder() {
    val copied = state(manifest(episode("a")), manifest(episode("b")), manifest(episode("c")))
    val now =
      state(
        manifest(episode("a"), startMs = 120_000),
        manifest(episode("b", version = "v2")),
        manifest(episode("d")),
      )
    val changes = headphoneSyncChanges(now.copy(lastCopied = deviceManifest(copied.library)))
    assertEquals(
      listOf("Episode d" to Kind.Add, "Episode a" to Kind.Update, "Episode b" to Kind.Update) +
        ("Episode c" to Kind.Remove),
      changes.map { it.title to it.kind },
    )
    assertEquals("Picks up from 2:00", changes[1].detail)
    assertEquals("New version of the audio", changes[2].detail)
    assertEquals("1 new · 2 updated · 1 removed", changes.summary())
  }
}
