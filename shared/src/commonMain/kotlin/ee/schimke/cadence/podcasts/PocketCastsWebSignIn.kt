package ee.schimke.cadence.podcasts

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Whether this platform can show the Pocket Casts website inside the app. */
expect val pocketCastsWebSignIn: Boolean

/**
 * Shows the Pocket Casts sign-in page. Once signed in it reports the web player's `auth` cookie
 * (JSON with `accessToken` and `refreshToken`). If no token is readable it falls back to the API
 * responses the player itself loads, as a JSON array of `{path, body}`.
 */
@Composable
expect fun PocketCastsWebSignIn(
  onCookie: (String) -> Unit,
  onCapture: (String) -> Unit,
  modifier: Modifier,
)
