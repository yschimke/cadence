package ee.schimke.cadence.podcasts

import androidx.compose.runtime.*
import kotlinx.coroutines.launch

@Composable
actual fun rememberPodcastWork(controller: PodcastController): PodcastWorkUi {
  val scope = rememberCoroutineScope()
  return PodcastWorkUi(
    supportsDownload = controller.platform.supportsOriginalDownloads,
    download = { scope.launch { controller.run { downloadOriginals() } } },
    process = { scope.launch { controller.run { prepare() } } },
    refresh = { scope.launch { controller.run { refreshSource() } } },
  )
}
