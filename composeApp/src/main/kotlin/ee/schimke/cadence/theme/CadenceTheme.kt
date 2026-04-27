package ee.schimke.cadence.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * Opt-in Cadence theme — Coastal Blue palette + Manrope/Inter typography.
 *
 * Used as an alternative to the system-dynamic [ee.schimke.cadence.AndroidMaterialTheme].
 * Switching is wired through the appearance preference in `ManageSyncScreen`;
 * default for the app remains the system theme.
 */
@Composable
fun CadenceTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = if (darkTheme) CadenceDarkColors else CadenceLightColors,
    typography = CadenceTypography,
    shapes = CadenceShapes,
    content = content,
  )
}
