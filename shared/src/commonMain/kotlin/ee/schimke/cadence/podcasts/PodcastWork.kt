package ee.schimke.cadence.podcasts

import androidx.compose.runtime.Composable

data class PodcastWorkUi(
  val supportsDownload: Boolean = false,
  val active: Boolean = false,
  val status: String? = null,
  val download: () -> Unit = {},
  val process: () -> Unit = {},
  val refresh: () -> Unit = {},
)

@Composable expect fun rememberPodcastWork(controller: PodcastController): PodcastWorkUi
