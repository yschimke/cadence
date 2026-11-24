package ee.schimke.cadence

import dev.zacsweers.metro.createGraphFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import ee.schimke.cadence.metro.BundledDesktopOAuth
import ee.schimke.cadence.metro.DesktopAppGraph
import ee.schimke.cadence.sync.google.OAuthConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DesktopServices(
  bundledOAuth: OAuthConfig? = null,
) : AutoCloseable {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val graph: DesktopAppGraph =
    createGraphFactory<DesktopAppGraph.Factory>().create(scope, BundledDesktopOAuth(bundledOAuth))

  val metroViewModelFactory: MetroViewModelFactory
    get() = graph.metroViewModelFactory

  init {
    scope.launch {
      var previous = emptySet<String>()
      graph.syncRepo.profiles.collectLatest { profiles ->
        val current = profiles.map { it.id }.toSet()
        (previous - current).forEach(graph.refreshScheduler::cancel)
        profiles.forEach(graph.refreshScheduler::schedule)
        previous = current
      }
    }

    scope.launch {
      var previous = emptySet<Int>()
      graph.usbManager.listDevices().collect { devices ->
        val prefs = graph.syncRepo.preferences.first()
        val added = devices.filter { it.id !in previous }
        previous = devices.map { it.id }.toSet()
        if (
          prefs?.auto_sync_on_usb == true &&
            added.any { device ->
              val match =
                "${device.vendorId.toString(16).padStart(4, '0')}:${device.productId.toString(16).padStart(4, '0')}"
              prefs.usb_match.isBlank() || prefs.usb_match.equals(match, ignoreCase = true)
            }
        )
          graph.syncOrchestrator.startSync(prefs.target_device_id)
      }
    }
  }

  override fun close() {
    scope.cancel()
  }
}
