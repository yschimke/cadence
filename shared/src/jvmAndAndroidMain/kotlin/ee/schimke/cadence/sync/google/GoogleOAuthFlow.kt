package ee.schimke.cadence.sync.google

import kotlinx.coroutines.CancellationException

/**
 * Platform-specific OAuth 2.0 flow for a signed-in Google account. Desktop runs the installed-app
 * loopback flow via a short-lived HTTP server; Android launches a Custom Tab and reads the result
 * through an activity-result contract.
 */
interface GoogleOAuthFlow {
  /** Run the browser consent flow and return a fresh account. Suspends until the user consents. */
  suspend fun authorize(): GoogleAccount

  /** Refresh a stale access token using the stored refresh token. */
  suspend fun refresh(account: GoogleAccount): GoogleAccount
}

/** Marker thrown when the user cancels the flow (closes browser without consenting). */
class GoogleOAuthCancelled : CancellationException("Google sign-in cancelled")
