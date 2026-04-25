package ee.schimke.cadence.browser

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.multiplatform.webview.web.PlatformWebViewParams
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.rememberWebViewNavigator
import com.multiplatform.webview.web.rememberWebViewState
import ee.schimke.cadence.Browser
import ee.schimke.cadence.metro.metroViewModel
import io.ktor.http.Url

@Composable
fun BrowserScreen(modifier: Modifier = Modifier) {
    val viewModel = metroViewModel<BrowserViewModel>()

    val route = viewModel.route
    BrowserScreen(route, modifier)
}

@Composable
private fun BrowserScreen(route: Browser, modifier: Modifier = Modifier) {
    val webViewState = rememberWebViewState(route.url ?: "about:blank").apply {
        webSettings.androidWebSettings.domStorageEnabled = true
        webSettings.androidWebSettings.safeBrowsingEnabled = false
    }

    val webViewNavigator = rememberWebViewNavigator()

    val platformWebViewParams = getPlatformWebViewParams(shouldOverrideUrlLoadingFn = { url ->
        false
    })

    WebView(
        modifier = modifier.fillMaxWidth()
            .safeContentPadding(),
        state = webViewState,
        navigator = webViewNavigator,
        platformWebViewParams = platformWebViewParams
    )
}

@Composable
expect fun getPlatformWebViewParams(shouldOverrideUrlLoadingFn: (Url) -> Boolean): PlatformWebViewParams?