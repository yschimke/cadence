package ee.schimke.cadence.sync.google

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Shared refresh-token exchange. Desktop runs its full install-app flow in-process; Android lets
 * AppAuth handle the authorization leg but still needs a refresh call outside the activity
 * lifecycle, so both platforms share this one OkHttp POST — java.net.http.HttpClient ships only on
 * the JVM, not Android, which crashes R8 at minify time.
 */
suspend fun refreshGoogleAccessToken(
  config: OAuthConfig,
  account: GoogleAccount,
  nowMillis: Long = System.currentTimeMillis(),
): GoogleAccount =
  withContext(Dispatchers.IO) {
    // Android OAuth clients have no secret (identity is package + SHA-1), so Google rejects a
    // client_secret field there — only include it when the config actually carries one.
    val fields = buildList {
      add("client_id" to config.clientId)
      if (config.clientSecret.isNotEmpty()) add("client_secret" to config.clientSecret)
      add("refresh_token" to account.refreshToken)
      add("grant_type" to "refresh_token")
    }
    val request =
      Request.Builder()
        .url("https://oauth2.googleapis.com/token")
        .post(formUrlEncoded(*fields.toTypedArray()).toRequestBody(FORM_URLENCODED))
        .build()
    val (code, text) = sharedHttp.newCall(request).executeAsText()
    check(code == 200) { "Token refresh failed ($code): $text" }
    val tokens = sharedJson.decodeFromString<RefreshResponse>(text)
    account.copy(
      accessToken = tokens.access_token,
      accessTokenExpiresAtMillis = nowMillis + tokens.expires_in * 1000L,
    )
  }

/** Fetch the signed-in user's primary email via Google's OpenID userinfo endpoint. */
suspend fun fetchGoogleUserEmail(accessToken: String): String =
  withContext(Dispatchers.IO) {
    val request =
      Request.Builder()
        .url("https://openidconnect.googleapis.com/v1/userinfo")
        .header("Authorization", "Bearer $accessToken")
        .get()
        .build()
    val (code, text) = sharedHttp.newCall(request).executeAsText()
    check(code == 200) { "Fetching user info failed ($code): $text" }
    sharedJson.decodeFromString<UserInfoResponse>(text).email
  }

/** One-liner used by the Drive client too. OkHttp's call isn't suspend-aware; wrap it on IO. */
internal fun okhttp3.Call.executeAsText(): Pair<Int, String> =
  execute().use { response ->
    val body = response.body?.string().orEmpty()
    response.code to body
  }

internal val sharedHttp: OkHttpClient by lazy { OkHttpClient() }

internal val sharedJson = Json { ignoreUnknownKeys = true }

internal val FORM_URLENCODED = "application/x-www-form-urlencoded".toMediaType()

internal val JSON_UTF8 = "application/json; charset=UTF-8".toMediaType()

internal fun formUrlEncoded(vararg pairs: Pair<String, String>): String =
  pairs.joinToString("&") { (k, v) ->
    "${URLEncoder.encode(k, StandardCharsets.UTF_8)}=${URLEncoder.encode(v, StandardCharsets.UTF_8)}"
  }

@Serializable internal data class RefreshResponse(val access_token: String, val expires_in: Int)

@Serializable private data class UserInfoResponse(val email: String)
