package ee.schimke.cadence.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * The Cadence brand scheme, for desktop and the browser, which have no wallpaper colours. Android
 * uses Material 3 Expressive with dynamic colours instead; the Compose Multiplatform material3
 * release used here (1.9.0) keeps `MaterialExpressiveTheme` and `MotionScheme` internal, so this
 * stays on `MaterialTheme` until a release makes them public.
 */
@Composable
fun CadenceTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = if (darkTheme) CadenceDarkColors else CadenceLightColors,
    content = content,
  )
}
