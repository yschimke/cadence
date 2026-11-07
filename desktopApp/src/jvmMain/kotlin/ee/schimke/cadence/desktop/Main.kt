package ee.schimke.cadence.desktop

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.window.*
import ee.schimke.cadence.DesktopServices
import ee.schimke.cadence.PortableApp
import ee.schimke.cadence.sync.google.OAuthConfig
import java.awt.Toolkit

/** The X11 WM_CLASS, matched by `StartupWMClass` in the AUR package's `cadence-desktop.desktop`. */
private const val WM_CLASS = "cadence-desktop"

fun main() {
  nameX11WindowClass()
  val icon =
    BitmapPainter(
      checkNotNull(Thread.currentThread().contextClassLoader.getResourceAsStream("cadence.png"))
        .use(::loadImageBitmap),
    )
  application {
    val services = remember { DesktopServices(bundledOAuth = bundledGoogleOAuth()) }
    DisposableEffect(services) { onDispose { services.close() } }
    Window(onCloseRequest = ::exitApplication, title = "Cadence", icon = icon) {
      PortableApp(services.factory)
    }
  }
}

/**
 * Returns the credentials baked in at build time, or null when the gradle properties were empty.
 */
private fun bundledGoogleOAuth(): OAuthConfig? {
  val id = BuildConfig.GOOGLE_CLIENT_ID
  val secret = BuildConfig.GOOGLE_CLIENT_SECRET
  if (id.isBlank() || secret.isBlank()) return null
  return OAuthConfig(clientId = id, clientSecret = secret)
}

/**
 * AWT names every window's class after the main class (`ee-schimke-cadence-desktop-MainKt`), so
 * docks and taskbars can't match the window to its launcher. Must run before the first window.
 * Needs `--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED`, which the Linux build passes.
 */
private fun nameX11WindowClass() {
  val toolkit = Toolkit.getDefaultToolkit()
  if (toolkit.javaClass.name != "sun.awt.X11.XToolkit") return
  toolkit.javaClass.getDeclaredField("awtAppClassName").apply {
    isAccessible = true
    set(toolkit, WM_CLASS)
  }
}
