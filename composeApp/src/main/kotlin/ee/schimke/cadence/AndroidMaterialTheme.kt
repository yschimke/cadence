package ee.schimke.cadence

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont

/**
 * Material 3 Expressive with system-dynamic colours — Material You from the device wallpaper —
 * expressive motion, and **Roboto Flex** as the type stack.
 *
 * This is the only Android app theme; desktop and the browser use the Cadence brand scheme.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AndroidMaterialTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val isDark = darkTheme
  val colorScheme =
    if (isDark) dynamicDarkColorScheme(LocalContext.current)
    else dynamicLightColorScheme(LocalContext.current)
  MaterialExpressiveTheme(
    colorScheme = colorScheme,
    motionScheme = MotionScheme.expressive(),
    typography = RobotoFlexTypography,
    content = content,
  )
}

private val googleFontsProvider =
  GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = ee.schimke.cadence.R.array.com_google_android_gms_fonts_certs,
  )

private val RobotoFlexGoogleFont = GoogleFont("Roboto Flex")

private val RobotoFlexFamily =
  FontFamily(
    Font(
      googleFont = RobotoFlexGoogleFont,
      fontProvider = googleFontsProvider,
      weight = FontWeight.Normal,
    ),
    Font(
      googleFont = RobotoFlexGoogleFont,
      fontProvider = googleFontsProvider,
      weight = FontWeight.Medium,
    ),
    Font(
      googleFont = RobotoFlexGoogleFont,
      fontProvider = googleFontsProvider,
      weight = FontWeight.SemiBold,
    ),
    Font(
      googleFont = RobotoFlexGoogleFont,
      fontProvider = googleFontsProvider,
      weight = FontWeight.Bold,
    ),
  )

/** Roboto Flex applied to every Material 3 type role; sizes and tracking come from M3 defaults. */
private val RobotoFlexTypography: Typography =
  Typography().run {
    copy(
      displayLarge = displayLarge.copy(fontFamily = RobotoFlexFamily),
      displayMedium = displayMedium.copy(fontFamily = RobotoFlexFamily),
      displaySmall = displaySmall.copy(fontFamily = RobotoFlexFamily),
      headlineLarge = headlineLarge.copy(fontFamily = RobotoFlexFamily),
      headlineMedium = headlineMedium.copy(fontFamily = RobotoFlexFamily),
      headlineSmall = headlineSmall.copy(fontFamily = RobotoFlexFamily),
      titleLarge = titleLarge.copy(fontFamily = RobotoFlexFamily),
      titleMedium = titleMedium.copy(fontFamily = RobotoFlexFamily),
      titleSmall = titleSmall.copy(fontFamily = RobotoFlexFamily),
      bodyLarge = bodyLarge.copy(fontFamily = RobotoFlexFamily),
      bodyMedium = bodyMedium.copy(fontFamily = RobotoFlexFamily),
      bodySmall = bodySmall.copy(fontFamily = RobotoFlexFamily),
      labelLarge = labelLarge.copy(fontFamily = RobotoFlexFamily),
      labelMedium = labelMedium.copy(fontFamily = RobotoFlexFamily),
      labelSmall = labelSmall.copy(fontFamily = RobotoFlexFamily),
    )
  }
