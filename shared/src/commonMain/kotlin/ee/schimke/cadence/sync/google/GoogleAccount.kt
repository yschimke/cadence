package ee.schimke.cadence.sync.google

import kotlinx.serialization.Serializable

/** Minimal signed-in-account snapshot (what the UI shows + what we need to call Drive). */
@Serializable
data class GoogleAccount(
  val email: String,
  val refreshToken: String,
  val accessToken: String,
  val accessTokenExpiresAtMillis: Long,
) {
  fun needsRefresh(nowMillis: Long, skewMillis: Long = 60_000): Boolean =
    nowMillis >= accessTokenExpiresAtMillis - skewMillis
}

/** Immutable OAuth client credentials, loaded from local config. Null means not configured. */
@Serializable data class OAuthConfig(val clientId: String, val clientSecret: String)

/** Scopes we request. drive.appdata keeps the manifest hidden from the user's Drive UI. */
const val GOOGLE_DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"

const val GOOGLE_OPENID_EMAIL_SCOPE = "email"
