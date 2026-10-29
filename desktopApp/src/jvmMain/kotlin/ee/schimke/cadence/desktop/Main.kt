package ee.schimke.cadence.desktop

import androidx.compose.runtime.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.window.*
import androidx.navigation.compose.rememberNavController
import ee.schimke.cadence.AddPodcasts
import ee.schimke.cadence.Bookmarks
import ee.schimke.cadence.DesktopServices
import ee.schimke.cadence.Devices
import ee.schimke.cadence.Home
import ee.schimke.cadence.ManageSync
import ee.schimke.cadence.PortableApp
import java.awt.Desktop
import java.net.URI

fun main() = application {
  val services = remember { DesktopServices() }
  DisposableEffect(services) { onDispose { services.close() } }
  Window(onCloseRequest = ::exitApplication, title = "Cadence") {
    val navController = rememberNavController()
    MenuBar {
      Menu("File", mnemonic = 'F') {
        Item(
          "Add folder…",
          shortcut = primary(Key.O),
          onClick = { navController.navigate(ManageSync) },
        )
        Item(
          "Add podcasts…",
          shortcut = primary(Key.N),
          onClick = { navController.navigate(AddPodcasts) },
        )
        Separator()
        Item("Quit Cadence", shortcut = primary(Key.Q), onClick = ::exitApplication)
      }
      Menu("View", mnemonic = 'V') {
        Item(
          "Library",
          shortcut = primary(Key.One),
          onClick = { navController.popBackStack(Home, inclusive = false) },
        )
        Item(
          "File explorer",
          shortcut = primary(Key.Two),
          onClick = { navController.navigate(Devices) },
        )
        Item(
          "Bookmarks",
          shortcut = primary(Key.Three),
          onClick = { navController.navigate(Bookmarks) },
        )
        Separator()
        Item(
          "Manage sources",
          shortcut = primary(Key.Comma),
          onClick = { navController.navigate(ManageSync) },
        )
        Item("Refresh now", shortcut = primary(Key.R), onClick = services::refreshAllProfiles)
      }
      Menu("Help", mnemonic = 'H') {
        Item("Project on GitHub", onClick = { openUrl("https://github.com/yschimke/cadence") })
        Item("Report an issue", onClick = { openUrl("https://github.com/yschimke/cadence/issues") })
      }
    }
    PortableApp(services.factory, navController)
  }
}

private val isMac = System.getProperty("os.name").orEmpty().contains("mac", ignoreCase = true)

private fun primary(key: Key): KeyShortcut =
  if (isMac) KeyShortcut(key, meta = true) else KeyShortcut(key, ctrl = true)

private fun openUrl(url: String) {
  if (Desktop.isDesktopSupported()) runCatching { Desktop.getDesktop().browse(URI(url)) }
}
