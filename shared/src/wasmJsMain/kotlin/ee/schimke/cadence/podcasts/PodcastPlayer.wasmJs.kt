package ee.schimke.cadence.podcasts

import androidx.compose.runtime.Composable

@Composable
actual fun rememberPodcastPlayer(controller: PodcastController, mediaAccess: Boolean) =
  PodcastPlayerUi()
