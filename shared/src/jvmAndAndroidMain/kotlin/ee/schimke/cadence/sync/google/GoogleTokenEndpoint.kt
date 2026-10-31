package ee.schimke.cadence.sync.google

import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Shared refresh-token exchange. Desktop runs its full install-app flow in-process; Android lets
 * AppAuth handle the authorization leg but still needs a refresh call outside the activity
 * lifecycle, so both platforms share this one JDK-HttpClient POST.
 */
suspend fun refreshGoogleAccessToken(
  config: OAuthConfig,
  account: GoogleAccount,
  http: HttpClient = sharedHttpClient,
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
    val body = formUrlEncoded(*fields.toTypedArray())
    val response =
      http.send(
        HttpRequest.newBuilder(URI.create("https://oauth2.googleapis.com/token"))
          .header("Content-Type", "application/x-www-form-urlencoded")
          .POST(HttpRequest.BodyPublishers.ofString(body))
          .build(),
        HttpResponse.BodyHandlers.ofString(),
      )
    check(response.statusCode() == 200) {
      "Token refresh failed (${response.statusCode()}): ${response.body()}"
    }
    val tokens = sharedJson.decodeFromString<RefreshResponse>(response.body())
    account.copy(
      accessToken = tokens.access_token,
      accessTokenExpiresAtMillis = nowMillis + tokens.expires_in * 1000L,
    )
  }

internal val sharedHttpClient: HttpClient by lazy { HttpClient.newHttpClient() }

internal val sharedJson = Json { ignoreUnknownKeys = true }

internal fun formUrlEncoded(vararg pairs: Pair<String, String>): String =
  pairs.joinToString("&") { (k, v) ->
    "${URLEncoder.encode(k, StandardCharsets.UTF_8)}=${URLEncoder.encode(v, StandardCharsets.UTF_8)}"
  }

@Serializable internal data class RefreshResponse(val access_token: String, val expires_in: Int)

/** Fetch the signed-in user's primary email via Google's OpenID userinfo endpoint. */
suspend fun fetchGoogleUserEmail(
  accessToken: String,
  http: HttpClient = sharedHttpClient,
): String =
  withContext(Dispatchers.IO) {
    val response =
      http.send(
        HttpRequest.newBuilder(URI.create("https://openidconnect.googleapis.com/v1/userinfo"))
          .header("Authorization", "Bearer $accessToken")
          .GET()
          .build(),
        HttpResponse.BodyHandlers.ofString(),
      )
    check(response.statusCode() == 200) {
      "Fetching user info failed (${response.statusCode()}): ${response.body()}"
    }
    sharedJson.decodeFromString<UserInfoResponse>(response.body()).email
  }

@Serializable private data class UserInfoResponse(val email: String)
