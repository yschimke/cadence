package ee.schimke.cadence.sync.google

import com.sun.net.httpserver.HttpServer
import java.awt.Desktop
import java.net.InetSocketAddress
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Desktop installed-app OAuth 2.0 flow with PKCE. Pops the browser at Google's consent screen,
 * catches the redirect on a short-lived loopback server, and exchanges the code for tokens.
 *
 * Fails with [IllegalStateException] when a desktop browser cannot be launched (headless session),
 * so the UI can offer "copy the URL manually" as a follow-up.
 */
class DesktopGoogleOAuthFlow(
  private val config: OAuthConfig,
  private val http: OkHttpClient = sharedHttp,
  private val timeout: Duration = 5.minutes,
) : GoogleOAuthFlow {
  private val json = Json { ignoreUnknownKeys = true }

  override suspend fun authorize(): GoogleAccount =
    withContext(Dispatchers.IO) {
      val pkce = generatePkce()
      val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
      try {
        val port = server.address.port
        val redirectUri = "http://127.0.0.1:$port/callback"
        // Start the server before opening the browser; otherwise Google's redirect races the
        // local listener and lands on connection refused.
        val awaitCode = installCallbackHandler(server, redirectUri)
        server.start()
        openBrowser(authorizationUrl(redirectUri, pkce.challenge))
        val code = withTimeout(timeout) { awaitCode() }
        val tokens = exchangeCode(code, redirectUri, pkce.verifier)
        val email = fetchGoogleUserEmail(tokens.accessToken)
        GoogleAccount(
          email = email,
          refreshToken = tokens.refreshToken,
          accessToken = tokens.accessToken,
          accessTokenExpiresAtMillis = System.currentTimeMillis() + tokens.expiresIn * 1000L,
        )
      } finally {
        server.stop(0)
      }
    }

  override suspend fun refresh(account: GoogleAccount): GoogleAccount =
    refreshGoogleAccessToken(config, account)

  private fun authorizationUrl(redirectUri: String, challenge: String): String {
    val params =
      mapOf(
        "client_id" to config.clientId,
        "redirect_uri" to redirectUri,
        "response_type" to "code",
        "scope" to "$GOOGLE_DRIVE_APPDATA_SCOPE $GOOGLE_OPENID_EMAIL_SCOPE",
        "code_challenge" to challenge,
        "code_challenge_method" to "S256",
        "access_type" to "offline",
        "prompt" to "consent",
      )
    val query = params.entries.joinToString("&") { (k, v) -> "$k=${encode(v)}" }
    return "https://accounts.google.com/o/oauth2/v2/auth?$query"
  }

  /**
   * Installs the callback handler on [server] and returns a suspend lambda that resumes with the
   * ?code=... value once the browser redirects. The caller starts the server before opening the
   * browser so Google's redirect never races the listener.
   */
  private fun installCallbackHandler(
    server: HttpServer,
    redirectUri: String,
  ): suspend () -> String {
    val path = URI.create(redirectUri).path
    val slot = java.util.concurrent.CompletableFuture<String>()
    server.createContext(path) { exchange ->
      val query = exchange.requestURI.query.orEmpty()
      val params =
        query
          .split('&')
          .mapNotNull {
            val eq = it.indexOf('=')
            if (eq < 0) null else decode(it.substring(0, eq)) to decode(it.substring(eq + 1))
          }
          .toMap()
      val code = params["code"]
      val error = params["error"]
      val body =
        if (code != null) "<html><body>You can close this window.</body></html>"
        else "<html><body>Sign-in failed: ${error ?: "no code"}</body></html>"
      val bytes = body.toByteArray(StandardCharsets.UTF_8)
      exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
      exchange.sendResponseHeaders(200, bytes.size.toLong())
      exchange.responseBody.use { it.write(bytes) }
      if (code != null) slot.complete(code)
      else
        slot.completeExceptionally(
          IllegalStateException("Google sign-in failed: ${error ?: "no code"}")
        )
    }
    return {
      suspendCancellableCoroutine { continuation ->
        slot.whenComplete { value, err ->
          if (err != null) continuation.resumeWithException(err) else continuation.resume(value)
        }
        continuation.invokeOnCancellation { slot.cancel(true) }
      }
    }
  }

  private fun exchangeCode(code: String, redirectUri: String, verifier: String): TokenResponse {
    val body =
      formUrlEncoded(
        "code" to code,
        "client_id" to config.clientId,
        "client_secret" to config.clientSecret,
        "redirect_uri" to redirectUri,
        "grant_type" to "authorization_code",
        "code_verifier" to verifier,
      )
    val request =
      Request.Builder()
        .url("https://oauth2.googleapis.com/token")
        .post(body.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
        .build()
    val (status, text) = http.newCall(request).executeAsText()
    check(status == 200) { "Token exchange failed ($status): $text" }
    return json.decodeFromString(text)
  }

  private fun openBrowser(url: String) {
    if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
      throw IllegalStateException(
        "No desktop browser available. Open this URL manually to continue:\n$url"
      )
    }
    Desktop.getDesktop().browse(URI(url))
  }

  private fun formUrlEncoded(vararg pairs: Pair<String, String>): String =
    pairs.joinToString("&") { (k, v) -> "${encode(k)}=${encode(v)}" }

  private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

  private fun decode(value: String): String =
    java.net.URLDecoder.decode(value, StandardCharsets.UTF_8)

  @Serializable
  private data class TokenResponse(
    val access_token: String,
    val refresh_token: String? = null,
    val expires_in: Int,
    val scope: String? = null,
    val token_type: String? = null,
  ) {
    val accessToken
      get() = access_token

    val refreshToken
      get() = refresh_token ?: ""

    val expiresIn
      get() = expires_in
  }
}
