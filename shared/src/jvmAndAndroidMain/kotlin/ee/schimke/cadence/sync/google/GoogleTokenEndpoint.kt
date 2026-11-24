package ee.schimke.cadence.sync.google

import ee.schimke.cadence.net.SharedHttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.parameters
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Shared refresh-token exchange. Desktop runs its full install-app flow in-process; Android lets
 * AppAuth handle the authorization leg but still needs a refresh call outside the activity
 * lifecycle, so both platforms share this Ktor POST backed by the shared OkHttp engine.
 */
suspend fun refreshGoogleAccessToken(
  config: OAuthConfig,
  account: GoogleAccount,
  nowMillis: Long = System.currentTimeMillis(),
): GoogleAccount {
  // Android OAuth clients have no secret (identity is package + SHA-1), so Google rejects a
  // client_secret field there — only include it when the config actually carries one.
  val response =
    SharedHttpClient.client.submitForm(
      url = "https://oauth2.googleapis.com/token",
      formParameters =
        parameters {
          append("client_id", config.clientId)
          if (config.clientSecret.isNotEmpty()) append("client_secret", config.clientSecret)
          append("refresh_token", account.refreshToken)
          append("grant_type", "refresh_token")
        },
    )
  val code = response.status.value
  val text = response.bodyAsText()
  check(code == 200) { "Token refresh failed ($code): $text" }
  val tokens = sharedJson.decodeFromString<RefreshResponse>(text)
  return account.copy(
    accessToken = tokens.access_token,
    accessTokenExpiresAtMillis = nowMillis + tokens.expires_in * 1000L,
  )
}

/** Fetch the signed-in user's primary email via Google's OpenID userinfo endpoint. */
suspend fun fetchGoogleUserEmail(accessToken: String): String {
  val response =
    SharedHttpClient.client.get("https://openidconnect.googleapis.com/v1/userinfo") {
      header(HttpHeaders.Authorization, "Bearer $accessToken")
    }
  val code = response.status.value
  val text = response.bodyAsText()
  check(code == 200) { "Fetching user info failed ($code): $text" }
  return sharedJson.decodeFromString<UserInfoResponse>(text).email
}

internal val sharedJson = Json { ignoreUnknownKeys = true }

@Serializable internal data class RefreshResponse(val access_token: String, val expires_in: Int)

@Serializable private data class UserInfoResponse(val email: String)
