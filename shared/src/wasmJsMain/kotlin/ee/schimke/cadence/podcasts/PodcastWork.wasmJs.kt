package ee.schimke.cadence.podcasts

import androidx.compose.runtime.*
import kotlinx.coroutines.launch

@Composable
actual fun rememberPodcastWork(controller: PodcastController): PodcastWorkUi {
  val scope = rememberCoroutineScope()
  val status by controller.status.collectAsState()
  // Runs in the foreground here, so "active" is simply one of these tasks being in flight.
  var running by remember { mutableStateOf(false) }
  var failure by remember { mutableStateOf<Pair<String, String>?>(null) }
  fun start(task: String, block: suspend PodcastController.() -> Unit) {
    if (running) return
    running = true
    failure = null
    scope.launch {
      try {
        if (!controller.run(block)) failure = task to controller.status.value
      } finally {
        running = false
      }
    }
  }
  return PodcastWorkUi(
    active = running,
    status = status.takeIf { running },
    error = failure?.second,
    failedTask = failure?.first,
    dismissError = { failure = null },
    process = { start("process") { prepare() } },
    refresh = { start("refresh") { refreshSource() } },
  )
}
