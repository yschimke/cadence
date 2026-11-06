package ee.schimke.cadence.podcasts

import androidx.compose.runtime.Composable

data class PodcastWorkUi(
  val supportsDownload: Boolean = false,
  val active: Boolean = false,
  /** What the running task is doing; null when nothing runs. */
  val status: String? = null,
  /** How far the running task is, 0 to 1, when it can tell. */
  val fraction: Float? = null,
  /** Why the last task failed, until it is retried or dismissed. */
  val error: String? = null,
  /** Which task [error] came from: "download", "process" or "refresh". */
  val failedTask: String? = null,
  val dismissError: () -> Unit = {},
  val download: () -> Unit = {},
  val process: () -> Unit = {},
  val refresh: () -> Unit = {},
)

@Composable expect fun rememberPodcastWork(controller: PodcastController): PodcastWorkUi
