package ee.schimke.cadence

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
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
 * System-dynamic Material 3 theme — Material You colours from the device wallpaper
 * paired with **Roboto Flex** as the type stack.
 *
 * This is the default app theme; the opt-in branded alternative is
 * [ee.schimke.cadence.theme.CadenceTheme].
 */
@Composable
fun AndroidMaterialTheme(content: @Composable () -> Unit) {
  val isDark = isSystemInDarkTheme()
  val colorScheme =
    if (isDark) dynamicDarkColorScheme(LocalContext.current)
    else dynamicLightColorScheme(LocalContext.current)
  MaterialTheme(colorScheme = colorScheme, typography = RobotoFlexTypography, content = content)
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
    Font(googleFont = RobotoFlexGoogleFont, fontProvider = googleFontsProvider, weight = FontWeight.Normal),
    Font(googleFont = RobotoFlexGoogleFont, fontProvider = googleFontsProvider, weight = FontWeight.Medium),
    Font(googleFont = RobotoFlexGoogleFont, fontProvider = googleFontsProvider, weight = FontWeight.SemiBold),
    Font(googleFont = RobotoFlexGoogleFont, fontProvider = googleFontsProvider, weight = FontWeight.Bold),
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
