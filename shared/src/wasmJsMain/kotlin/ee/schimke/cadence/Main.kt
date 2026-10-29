package ee.schimke.cadence

import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import ee.schimke.cadence.bluetooth.UnavailableBluetoothController
import ee.schimke.cadence.browser.removeBootMessage
import ee.schimke.cadence.data.*
import ee.schimke.cadence.datastore.proto.SyncProfile
import ee.schimke.cadence.metro.*
import ee.schimke.cadence.sync.*
import ee.schimke.cadence.usb.WebUsbManager
import kotlinx.coroutines.*
import okio.Path

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
  val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  val store = BrowserSettingsStore()
  val repo = SyncRepo(store)
  val backend = BrowserDirectoryBackend()
  val refresh = PortableRefresh(repo, backend)
  val sync = PortableSync(repo, DevicesRepo(store), backend, scope)
  val usb = WebUsbManager()
  val scheduler =
    object : RefreshScheduler {
      override fun schedule(profile: SyncProfile) {}

      override fun cancel(profileId: String) {}

      override fun runNow(profileId: String) {
        scope.launch {
          try {
            refresh.refresh(profileId)
          } catch (e: CancellationException) {
            throw e
          } catch (_: Exception) {}
        }
      }
    }
  val factory =
    PortableViewModelFactory(
      store,
      usb.manager,
      UnavailableBluetoothController(),
      null,
      object : StorageManager {
        override fun getVolume(path: Path) =
          Volume(
            description = "Browser directory handle (reselect if permission expires)",
            path = path,
          )
      },
      refresh,
      sync,
      scheduler,
      object : SourceSuggestionsProvider {
        override suspend fun list() = emptyList<SourceSuggestion>()

        override fun open(suggestion: SourceSuggestion) {}
      },
      PortableSourceBrowser(repo, backend),
    )
  removeBootMessage()
  ComposeViewport {
    DisposableEffect(Unit) {
      onDispose {
        usb.close()
        scope.cancel()
      }
    }
    PortableApp(factory)
  }
}
