package ee.schimke.cadence.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable

/**
 * The Cadence brand scheme, for desktop and the browser, which have no wallpaper colours. Uses
 * Material 3 Expressive with `MotionScheme.expressive()` to match Android.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CadenceTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
  MaterialExpressiveTheme(
    colorScheme = if (darkTheme) CadenceDarkColors else CadenceLightColors,
    motionScheme = MotionScheme.expressive(),
    content = content,
  )
}
