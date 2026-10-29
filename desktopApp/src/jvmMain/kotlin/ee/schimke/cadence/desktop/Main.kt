package ee.schimke.cadence.desktop

import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
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
  val navController = rememberNavController()
  Window(
    onCloseRequest = ::exitApplication,
    title = "Cadence",
    onPreviewKeyEvent = { event ->
      if (event.type != KeyEventType.KeyDown || !event.isPrimary()) false
      else
        when (event.key) {
          Key.R -> {
            services.refreshAllProfiles()
            true
          }
          Key.Comma -> {
            navController.navigate(ManageSync)
            true
          }
          Key.One -> {
            navController.popBackStack(Home, inclusive = false)
            true
          }
          Key.Two -> {
            navController.navigate(Devices)
            true
          }
          Key.Three -> {
            navController.navigate(Bookmarks)
            true
          }
          Key.N -> {
            navController.navigate(AddPodcasts)
            true
          }
          Key.Q -> {
            exitApplication()
            true
          }
          else -> false
        }
    },
  ) {
    PortableApp(
      factory = services.factory,
      navController = navController,
      topBarOverflow = { dismiss ->
        DropdownMenuItem(
          text = { Text("Add podcasts") },
          onClick = {
            dismiss()
            navController.navigate(AddPodcasts)
          },
        )
        DropdownMenuItem(
          text = { Text("Manage sources") },
          onClick = {
            dismiss()
            navController.navigate(ManageSync)
          },
        )
        DropdownMenuItem(
          text = { Text("Refresh now") },
          onClick = {
            dismiss()
            services.refreshAllProfiles()
          },
        )
        DropdownMenuItem(
          text = { Text("Project on GitHub") },
          onClick = {
            dismiss()
            openUrl("https://github.com/yschimke/cadence")
          },
        )
        DropdownMenuItem(
          text = { Text("Report an issue") },
          onClick = {
            dismiss()
            openUrl("https://github.com/yschimke/cadence/issues")
          },
        )
        DropdownMenuItem(
          text = { Text("Quit Cadence") },
          onClick = {
            dismiss()
            exitApplication()
          },
        )
      },
    )
  }
}

private val isMac = System.getProperty("os.name").orEmpty().contains("mac", ignoreCase = true)

/** Cmd on macOS, Ctrl everywhere else. */
private fun KeyEvent.isPrimary(): Boolean = if (isMac) isMetaPressed else isCtrlPressed

private fun openUrl(url: String) {
  if (Desktop.isDesktopSupported()) runCatching { Desktop.getDesktop().browse(URI(url)) }
}
