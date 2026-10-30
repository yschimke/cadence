package ee.schimke.cadence.desktop

import androidx.compose.runtime.*
import androidx.compose.ui.window.*
import ee.schimke.cadence.DesktopServices
import ee.schimke.cadence.PortableApp
import ee.schimke.cadence.sync.google.LocalGoogleSyncController
import ee.schimke.cadence.sync.google.OAuthConfig

fun main() = application {
  val services = remember { DesktopServices(bundledOAuth = bundledGoogleOAuth()) }
  DisposableEffect(services) { onDispose { services.close() } }
  Window(onCloseRequest = ::exitApplication, title = "Cadence") {
    CompositionLocalProvider(LocalGoogleSyncController provides services.googleSync) {
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
