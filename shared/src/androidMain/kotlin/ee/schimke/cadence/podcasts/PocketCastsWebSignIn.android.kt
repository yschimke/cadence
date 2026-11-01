package ee.schimke.cadence.podcasts

import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.net.URLDecoder
import kotlin.coroutines.resume
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

actual val pocketCastsWebSignIn = true

private const val SITE = "https://pocketcasts.com"

// Records the account reads the player makes from here on, then opens Playlists so it makes them.
// It never sends requests of its own.
private const val CAPTURE =
  """
(() => {
  if (window.__cadenceCapture) return;
  const captured = window.__cadenceCapture = [];
  const paths = ['/user/playlists', '/user/episode', '/up_next/list', '/user/in_progress'];
  const pathOf = value => {
    try {
      const url = new URL(value, location.href);
      return url.origin === 'https://api.pocketcasts.com' && paths.includes(url.pathname) ? url.pathname : null;
    } catch (_) { return null; }
  };
  const fetch = window.fetch;
  window.fetch = async (...args) => {
    const response = await fetch(...args);
    const path = pathOf(args[0] instanceof Request ? args[0].url : String(args[0]));
    if (path && response.ok) response.clone().text().then(body => captured.push({ path, body }));
    return response;
  };
  const open = XMLHttpRequest.prototype.open;
  XMLHttpRequest.prototype.open = function (method, url, ...rest) {
    const path = pathOf(url);
    if (path) this.addEventListener('load', () => {
      if (this.status >= 200 && this.status < 300)
        captured.push({ path, body: typeof this.response === 'string' ? this.response : JSON.stringify(this.response) });
    });
    return open.call(this, method, url, ...rest);
  };
  document.querySelector('a[href*="/playlists"]')?.click();
})()
"""

private fun authCookie(): String? =
  CookieManager.getInstance()
    .getCookie(SITE)
    ?.split(';')
    ?.map { it.trim() }
    ?.firstOrNull { it.startsWith("auth=") }
    ?.let { URLDecoder.decode(it.removePrefix("auth="), "UTF-8") }
    ?.takeIf { "accessToken" in it }

private fun forgetAuthCookie() {
  val cookies = CookieManager.getInstance()
  listOf("", "; domain=pocketcasts.com", "; domain=.pocketcasts.com").forEach {
    cookies.setCookie(SITE, "auth=; Max-Age=0; path=/$it")
  }
  cookies.flush()
}

private suspend fun WebView.evaluate(script: String): String =
  suspendCancellableCoroutine { continuation ->
    evaluateJavascript(script) { result ->
      // The result is JSON: a quoted string, or null.
      continuation.resume(
        runCatching { Json.parseToJsonElement(result).jsonPrimitive.contentOrNull }.getOrNull()
          ?: ""
      )
    }
  }

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun PocketCastsWebSignIn(
  onCookie: (String) -> Unit,
  onCapture: (String) -> Unit,
  modifier: Modifier,
) {
  val cookie by rememberUpdatedState(onCookie)
  val capture by rememberUpdatedState(onCapture)
  var view by remember { mutableStateOf<WebView?>(null) }
  var loading by remember { mutableStateOf(true) }
  Box(modifier) {
  AndroidView(
    factory = { context ->
      if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)
        WebView.setWebContentsDebuggingEnabled(true)
      WebView(context).apply {
        // With the default wrap-content height the page's 100vh is 0 and its background is lost.
        layoutParams =
          ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
          )
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        // Keeps the sign-in flow inside this view.
        webViewClient = object : WebViewClient() {
          override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            loading = true
          }

          override fun onPageFinished(view: WebView?, url: String?) {
            loading = false
          }
        }
        // Cadence only opens this when it has no working session, so an old web sign-in is stale.
        // Without this an expired cookie would be picked up again on every attempt.
        forgetAuthCookie()
        // Signed-out visitors are sent on to /user/login and returned here afterwards.
        loadUrl("$SITE/podcasts")
        view = this
      }
    },
    modifier = Modifier.fillMaxSize(),
    onRelease = { it.destroy() },
  )
  if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
  }
  LaunchedEffect(Unit) {
    var signedInAt = 0L
    var installed = false
    var last = ""
    var unchanged = 0
    while (true) {
      delay(1000)
      authCookie()?.let {
        cookie(it)
        return@LaunchedEffect
      }
      val web = view ?: continue
      val url = web.url.orEmpty()
      if (!url.startsWith("$SITE/") || "/user/" in url) {
        signedInAt = 0
        continue
      }
      if (signedInAt == 0L) signedInAt = System.currentTimeMillis()
      // Signed in, yet no readable token: read what the player itself loads instead.
      if (System.currentTimeMillis() - signedInAt < 5000) continue
      if (!installed) {
        web.evaluateJavascript(CAPTURE, null)
        installed = true
        continue
      }
      val responses = web.evaluate("JSON.stringify(window.__cadenceCapture || [])")
      if (responses == last && responses.length > 2) {
        // Report once the player has stopped loading for a few seconds.
        if (++unchanged >= 3) {
          capture(responses)
          return@LaunchedEffect
        }
      } else {
        unchanged = 0
        last = responses
      }
    }
  }
}
