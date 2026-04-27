package ee.schimke.cadence.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font as GFFont
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import ee.schimke.cadence.R

/**
 * Cadence typography stack.
 *  - Manrope (geometric sans, humanist warmth) drives display/headline/title roles —
 *    the "athletic device, deliberate" voice that goes with the Coastal Blue palette.
 *  - Inter (workhorse UI sans) drives body/label roles — best-in-class legibility
 *    at small sizes, with tabular figures for sync progress numerals.
 *
 * Both load via Google Fonts downloadable provider, so previews and production
 * resolve the same ttfs without bundling them into the APK.
 */
private val googleFontsProvider =
  GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
  )

private val ManropeGoogleFont = GoogleFont("Manrope")
private val InterGoogleFont = GoogleFont("Inter")

internal val ManropeFamily =
  FontFamily(
    GFFont(googleFont = ManropeGoogleFont, fontProvider = googleFontsProvider, weight = FontWeight.Normal),
    GFFont(googleFont = ManropeGoogleFont, fontProvider = googleFontsProvider, weight = FontWeight.Medium),
    GFFont(googleFont = ManropeGoogleFont, fontProvider = googleFontsProvider, weight = FontWeight.SemiBold),
    GFFont(googleFont = ManropeGoogleFont, fontProvider = googleFontsProvider, weight = FontWeight.Bold),
  )

internal val InterFamily =
  FontFamily(
    GFFont(googleFont = InterGoogleFont, fontProvider = googleFontsProvider, weight = FontWeight.Normal),
    GFFont(googleFont = InterGoogleFont, fontProvider = googleFontsProvider, weight = FontWeight.Medium),
    GFFont(googleFont = InterGoogleFont, fontProvider = googleFontsProvider, weight = FontWeight.SemiBold),
    GFFont(googleFont = InterGoogleFont, fontProvider = googleFontsProvider, weight = FontWeight.Bold),
  )

private fun displayStyle(size: Int, weight: FontWeight, lineHeight: Int, tracking: Double = 0.0): TextStyle =
  TextStyle(
    fontFamily = ManropeFamily,
    fontWeight = weight,
    fontStyle = FontStyle.Normal,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.sp,
  )

private fun bodyStyle(size: Int, weight: FontWeight, lineHeight: Int, tracking: Double = 0.0): TextStyle =
  TextStyle(
    fontFamily = InterFamily,
    fontWeight = weight,
    fontStyle = FontStyle.Normal,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.sp,
  )

internal val CadenceTypography: Typography =
  Typography(
    displayLarge = displayStyle(57, FontWeight.SemiBold, 64, -0.25),
    displayMedium = displayStyle(45, FontWeight.SemiBold, 52),
    displaySmall = displayStyle(36, FontWeight.SemiBold, 44),
    headlineLarge = displayStyle(32, FontWeight.SemiBold, 40),
    headlineMedium = displayStyle(28, FontWeight.SemiBold, 36),
    headlineSmall = displayStyle(24, FontWeight.SemiBold, 32),
    titleLarge = displayStyle(22, FontWeight.SemiBold, 28),
    titleMedium = displayStyle(16, FontWeight.SemiBold, 24, 0.15),
    titleSmall = displayStyle(14, FontWeight.SemiBold, 20, 0.1),
    bodyLarge = bodyStyle(16, FontWeight.Normal, 24, 0.5),
    bodyMedium = bodyStyle(14, FontWeight.Normal, 20, 0.25),
    bodySmall = bodyStyle(12, FontWeight.Normal, 16, 0.4),
    labelLarge = bodyStyle(14, FontWeight.Medium, 20, 0.1),
    labelMedium = bodyStyle(12, FontWeight.Medium, 16, 0.5),
    labelSmall = bodyStyle(11, FontWeight.Medium, 16, 0.5),
  )
