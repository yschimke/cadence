package ee.schimke.cadence.browser

import android.webkit.ClientCertRequest
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.compose.runtime.Composable
import com.multiplatform.webview.web.AccompanistWebChromeClient
import com.multiplatform.webview.web.AccompanistWebViewClient
import com.multiplatform.webview.web.PlatformWebViewParams
import io.ktor.http.Url

@Composable
actual fun getPlatformWebViewParams(shouldOverrideUrlLoadingFn: (Url) -> Boolean): PlatformWebViewParams? {
    return PlatformWebViewParams(
        client = object : AccompanistWebViewClient() {
            override fun onReceivedClientCertRequest(view: WebView, request: ClientCertRequest) {
                TODO()
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val targetUrl = request?.url?.let { Url(it.toString()) }

                return targetUrl != null && shouldOverrideUrlLoadingFn(targetUrl)
            }

        },
        chromeClient = object : AccompanistWebChromeClient() {

        })
}