package ee.schimke.cadence.preview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import ee.schimke.cadence.AndroidMaterialTheme
import ee.schimke.cadence.podcasts.*

private object PreviewPodcastPlatform : PodcastPlatform {
  override val splitFormats = setOf("m4a")
  override val description = "Android: HTTPS downloads and USB copying through the document picker."

  override suspend fun readState(): String? = null

  override suspend fun saveState(state: String) {}

  override suspend fun request(
    url: String,
    method: String,
    headers: Map<String, String>,
    body: String?,
  ) = error("Preview")

  override suspend fun rss(url: String) = error("Preview")

  override suspend fun prepare(episode: Episode, sections: List<Section>) {}

  override suspend fun available(section: Section) = true

  override suspend fun export(sections: List<Section>, target: String, manifest: String) {}
}

private val feed = "https://feeds.example/tides"
private val inProgress =
  listOf(
    Episode(
      "gpodder",
      remoteEpisodeId(feed, "https://cdn.example/412.mp3"),
      "412 — Tides of the Pacific",
      "https://cdn.example/412.mp3",
      3_120_000,
      1_260_000,
      "gpodder:2026-10-26T07:40:00:1260",
      "v1",
    ),
    Episode(
      "gpodder",
      remoteEpisodeId(feed, "https://cdn.example/411.mp3"),
      "411 — Lighthouses",
      "https://cdn.example/411.mp3",
      2_700_000,
      300_000,
      "gpodder:2026-10-25T18:02:00:300",
      "v1",
    ),
  )

private fun controller(prepared: Boolean): PodcastController {
  val config =
    Segmentation(
      splitAtPosition = true,
      chunkMinutes = 15,
      chunkFormat = "m4a",
      skipListened = true,
    )
  val manifests =
    if (prepared) inProgress.map { Manifest(it, config, segment(it, config)) } else emptyList()
  val first = manifests.firstOrNull()?.sections?.first()
  return PodcastController(PreviewPodcastPlatform).apply {
    state.value =
      LocalPodcastState(
        library =
          PodcastLibrary(
            playlist = PlaylistSnapshot(name = "gpodder in progress", episodes = inProgress),
            config = config,
            manifests = manifests,
            completions =
              listOfNotNull(
                first?.let {
                  Completion(
                    it.id,
                    inProgress[0].revision,
                    it.episodeKey,
                    it.audioVersion,
                    it.startMs,
                    it.endMs,
                  )
                }
              ),
            lastBackendSync = "2026-10-27T08:15:00Z",
          ),
        readySectionIds = manifests.flatMap { it.sections }.map { it.id }.toSet(),
        gpodder = GpodderAccount(GpodderFlavor.Nextcloud, "https://cloud.example", "listener"),
      )
    status.value =
      if (prepared) "Ready: 2 episodes in 5 files"
      else "Imported 2 in-progress episodes from cloud.example"
  }
}

@Preview(name = "Podcast sync — imported", showBackground = true, heightDp = 1400)
@Composable
internal fun PodcastImportedPreview() {
  AndroidMaterialTheme { PodcastScreen(remember { controller(prepared = false) }) }
}

@Preview(name = "Podcast sync — prepared", showBackground = true, heightDp = 1800)
@Composable
internal fun PodcastPreparedPreview() {
  AndroidMaterialTheme { PodcastScreen(remember { controller(prepared = true) }) }
}
