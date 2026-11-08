package ee.schimke.cadence.desktop

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import ee.schimke.cadence.DesktopNavigation
import ee.schimke.cadence.DesktopSection
import ee.schimke.cadence.DesktopServices
import ee.schimke.cadence.PortableApp
import ee.schimke.cadence.sync.google.OAuthConfig
import java.awt.Desktop
import java.awt.Dimension
import java.awt.Toolkit
import java.awt.desktop.AppReopenedListener

/** The X11 WM_CLASS, matched by `StartupWMClass` in the AUR package's `cadence-desktop.desktop`. */
private const val WM_CLASS = "cadence-desktop"

fun main() {
  val isMac = System.getProperty("os.name").contains("Mac")
  if (isMac) {
    System.setProperty("apple.laf.useScreenMenuBar", "true")
    System.setProperty("apple.awt.application.name", "Cadence")
  }
  nameX11WindowClass()
  val icon =
    BitmapPainter(
      checkNotNull(Thread.currentThread().contextClassLoader.getResourceAsStream("cadence.png"))
        .use(::loadImageBitmap),
    )
  application {
    val services = remember { DesktopServices(bundledOAuth = bundledGoogleOAuth()) }
    DisposableEffect(services) { onDispose { services.close() } }
    val navigation = remember { DesktopNavigation() }
    val state = rememberWindowState(width = 1200.dp, height = 850.dp)
    var visible by remember { mutableStateOf(true) }
    var beforeFullscreen by remember { mutableStateOf(WindowPlacement.Floating) }
    var beforeFullscreenSize by remember { mutableStateOf(state.size) }
    var beforeFullscreenPosition by remember { mutableStateOf(state.position) }
    fun toggleFullscreen() {
      if (state.placement == WindowPlacement.Fullscreen) {
        state.placement = beforeFullscreen
        state.size = beforeFullscreenSize
        state.position = beforeFullscreenPosition
      } else {
        beforeFullscreen = state.placement
        beforeFullscreenSize = state.size
        beforeFullscreenPosition = state.position
        state.placement = WindowPlacement.Fullscreen
      }
    }
    Window(
      onCloseRequest = { if (isMac) visible = false else exitApplication() },
      title = "Cadence",
      icon = icon,
      state = state,
      visible = visible,
    ) {
      DisposableEffect(window) {
        window.minimumSize = Dimension(800, 600)
        val desktop = if (isMac && Desktop.isDesktopSupported()) Desktop.getDesktop() else null
        val reopen = AppReopenedListener {
          visible = true
          state.isMinimized = false
          window.toFront()
        }
        desktop?.addAppEventListener(reopen)
        if (desktop?.isSupported(Desktop.Action.APP_QUIT_HANDLER) == true) {
          desktop.setQuitHandler { _, response ->
            response.cancelQuit()
            exitApplication()
          }
        }
        onDispose {
          desktop?.removeAppEventListener(reopen)
          if (desktop?.isSupported(Desktop.Action.APP_QUIT_HANDLER) == true)
            desktop.setQuitHandler(null)
        }
      }
      MenuBar {
        Menu("Go", mnemonic = 'G') {
          val keys = listOf(Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six)
          DesktopSection.entries.forEachIndexed { index, section ->
            Item(
              section.label,
              shortcut = KeyShortcut(keys[index], ctrl = !isMac, meta = isMac),
              onClick = { navigation.select(section) },
            )
          }
        }
        Menu("Window", mnemonic = 'W') {
          CheckboxItem(
            "Full Screen",
            checked = state.placement == WindowPlacement.Fullscreen,
            shortcut =
              if (isMac) KeyShortcut(Key.F, ctrl = true, meta = true) else KeyShortcut(Key.F11),
            onCheckedChange = { toggleFullscreen() },
          )
          Item(
            "Minimize",
            shortcut = if (isMac) KeyShortcut(Key.M, meta = true) else null,
            onClick = { state.isMinimized = true },
          )
          if (!isMac) {
            Separator()
            Item("Quit", shortcut = KeyShortcut(Key.Q, ctrl = true), onClick = ::exitApplication)
          }
        }
      }
      PortableApp(services.factory, navigation)
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
