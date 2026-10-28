package ee.schimke.cadence.podcasts

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

actual val pocketCastsWebSignIn = false

@Composable
actual fun PocketCastsWebSignIn(
  onCookie: (String) -> Unit,
  onCapture: (String) -> Unit,
  modifier: Modifier,
) {}
