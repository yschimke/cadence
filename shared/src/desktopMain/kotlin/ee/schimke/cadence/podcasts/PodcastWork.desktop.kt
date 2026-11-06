package ee.schimke.cadence.podcasts

import androidx.compose.runtime.*
import kotlinx.coroutines.launch

@Composable
actual fun rememberPodcastWork(controller: PodcastController): PodcastWorkUi {
  val scope = rememberCoroutineScope()
  val fraction by controller.progress.collectAsState()
  var failure by remember { mutableStateOf<Pair<String, String>?>(null) }
  fun start(task: String, block: suspend PodcastController.() -> Unit) {
    failure = null
    scope.launch { if (!controller.run(block)) failure = task to controller.status.value }
  }
  return PodcastWorkUi(
    supportsDownload = controller.platform.supportsOriginalDownloads,
    fraction = fraction,
    error = failure?.second,
    failedTask = failure?.first,
    dismissError = { failure = null },
    download = { start("download") { downloadOriginals() } },
    process = { start("process") { prepare() } },
    refresh = { start("refresh") { refreshSource() } },
  )
}
