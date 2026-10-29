package ee.schimke.cadence.desktop

import androidx.compose.runtime.*
import androidx.compose.ui.window.*
import ee.schimke.cadence.DesktopServices
import ee.schimke.cadence.PortableApp

fun main() = application {
  val services = remember { DesktopServices() }
  DisposableEffect(services) { onDispose { services.close() } }
  Window(onCloseRequest = ::exitApplication, title = "Cadence") { PortableApp(services.factory) }
}
