package ee.schimke.cadence.browser

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ee.schimke.cadence.Browser
import ee.schimke.cadence.metro.metroViewModel

@Composable
fun BrowserScreen(modifier: Modifier = Modifier) {
  PlatformBrowser(metroViewModel<BrowserViewModel>().route, modifier)
}

@Composable expect fun PlatformBrowser(route: Browser, modifier: Modifier)
