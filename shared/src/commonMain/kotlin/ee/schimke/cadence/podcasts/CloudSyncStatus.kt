package ee.schimke.cadence.podcasts

import ee.schimke.cadence.sync.formatLastRefreshed

/** The same source and readiness information for the app and launcher. */
data class CloudSyncStatus(
  val source: String,
  val downloads: String,
  val processing: String,
  val checked: String,
  val hasSource: Boolean,
  val canRefresh: Boolean,
  val canDownload: Boolean,
  val canProcess: Boolean,
  val busy: Boolean = false,
  val progress: String? = null,
  val downloadedCount: Int? = null,
  val downloadNeeded: Int? = null,
  val processingNeeded: Int? = null,
)

fun cloudSyncStatus(
  state: LocalPodcastState,
  canRefresh: Boolean,
  supportsDownload: Boolean,
  busy: Boolean = false,
  progress: String? = null,
): CloudSyncStatus {
  val library = state.library
  val playlist = library.playlist
  if (playlist == null)
    return CloudSyncStatus(
      "Connect podcasts",
      "",
      "",
      "",
      false,
      false,
      false,
      false,
      busy,
      progress,
    )
  val source =
    when (library.source) {
      PodcastSourceKind.PocketCasts -> "Pocket Casts"
      PodcastSourceKind.Rss -> "Podcast feed"
      PodcastSourceKind.Gpodder -> "gpodder"
      PodcastSourceKind.Overcast -> "Overcast"
      PodcastSourceKind.Snapshot,
      null -> "Podcast import"
    }
  val downloaded = playlist.episodes.count { it.key in state.downloadedEpisodeKeys }
  val ready =
    playlist.episodes.count { episode ->
      library.manifests.any {
        it.episode.key == episode.key &&
          it.sections.isNotEmpty() &&
          it.sections.all { section -> section.id in state.readySectionIds }
      }
    }
  val missing = playlist.episodes.size - downloaded
  val pending = playlist.episodes.size - ready
  return CloudSyncStatus(
    "$source · ${playlist.name}",
    if (supportsDownload) "$downloaded downloaded · $missing to download"
    else "${playlist.episodes.size} episodes",
    "$ready processed · $pending to process",
    "Last checked: ${formatLastRefreshed(library.lastBackendSync, fallback = "never")}",
    true,
    canRefresh && !busy,
    supportsDownload && missing > 0 && !busy,
    pending > 0 && !busy,
    busy,
    progress,
    downloaded,
    missing,
    pending,
  )
}
