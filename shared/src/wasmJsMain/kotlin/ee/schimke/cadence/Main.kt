package ee.schimke.cadence

import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import dev.zacsweers.metro.createGraphFactory
import ee.schimke.cadence.browser.removeBootMessage
import ee.schimke.cadence.metro.WasmAppGraph
import ee.schimke.cadence.usb.WebUsbManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
  val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  val usb = WebUsbManager()
  val graph = createGraphFactory<WasmAppGraph.Factory>().create(scope, usb)
  removeBootMessage()
  ComposeViewport {
    DisposableEffect(Unit) {
      onDispose {
        usb.close()
        scope.cancel()
      }
    }
    PortableApp(graph.metroViewModelFactory)
  }
}
