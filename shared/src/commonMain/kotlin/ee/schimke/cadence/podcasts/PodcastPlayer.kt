package ee.schimke.cadence.podcasts

import androidx.compose.runtime.Composable

data class PodcastPlayerUi(
  val supported: Boolean = false,
  val pocketCastsAvailable: Boolean = false,
  val suggestMediaAccess: Boolean = false,
  val requestMediaAccess: () -> Unit = {},
  val title: String? = null,
  val playing: Boolean = false,
  val positionMs: Long = 0,
  val durationMs: Long = 0,
  val playEpisode: (Episode) -> Unit = {},
  val playPause: () -> Unit = {},
  val seek: (Long) -> Unit = {},
)

@Composable
expect fun rememberPodcastPlayer(
  controller: PodcastController,
  mediaAccess: Boolean,
): PodcastPlayerUi
