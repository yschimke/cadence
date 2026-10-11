package ee.schimke.cadence.browser

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ee.schimke.cadence.Browser
import java.awt.Desktop
import java.net.URI

@Composable
actual fun PlatformBrowser(route: Browser, modifier: Modifier) {
  Column(modifier) {
    Text(route.url ?: "No URL selected")
    Button(enabled = route.url != null, onClick = { Desktop.getDesktop().browse(URI(route.url)) }) {
      Text("Open in browser")
    }
  }
}
