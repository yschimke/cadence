package ee.schimke.cadence.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import cadence.shared.generated.resources.Res
import cadence.shared.generated.resources.inter
import cadence.shared.generated.resources.manrope
import org.jetbrains.compose.resources.Font

/**
 * Cadence typography stack.
 * - Manrope (geometric sans, humanist warmth) drives display/headline/title roles — the "athletic
 *   device, deliberate" voice that goes with the Coastal Blue palette.
 * - Inter (workhorse UI sans) drives body/label roles — best-in-class legibility at small sizes,
 *   with tabular figures for sync progress numerals.
 *
 * Both fonts are bundled Compose resources so every target can render the brand offline.
 */
private fun displayStyle(
  family: FontFamily,
  size: Int,
  weight: FontWeight,
  lineHeight: Int,
  tracking: Double = 0.0,
): TextStyle =
  TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontStyle = FontStyle.Normal,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.sp,
  )

private fun bodyStyle(
  family: FontFamily,
  size: Int,
  weight: FontWeight,
  lineHeight: Int,
  tracking: Double = 0.0,
): TextStyle =
  TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontStyle = FontStyle.Normal,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.sp,
  )

@Composable
internal fun cadenceTypography(): Typography {
  val manrope = FontFamily(Font(Res.font.manrope))
  val inter = FontFamily(Font(Res.font.inter))
  return Typography(
    displayLarge = displayStyle(manrope, 57, FontWeight.SemiBold, 64, -0.25),
    displayMedium = displayStyle(manrope, 45, FontWeight.SemiBold, 52),
    displaySmall = displayStyle(manrope, 36, FontWeight.SemiBold, 44),
    headlineLarge = displayStyle(manrope, 32, FontWeight.SemiBold, 40),
    headlineMedium = displayStyle(manrope, 28, FontWeight.SemiBold, 36),
    headlineSmall = displayStyle(manrope, 24, FontWeight.SemiBold, 32),
    titleLarge = displayStyle(manrope, 22, FontWeight.SemiBold, 28),
    titleMedium = displayStyle(manrope, 16, FontWeight.SemiBold, 24, 0.15),
    titleSmall = displayStyle(manrope, 14, FontWeight.SemiBold, 20, 0.1),
    bodyLarge = bodyStyle(inter, 16, FontWeight.Normal, 24, 0.5),
    bodyMedium = bodyStyle(inter, 14, FontWeight.Normal, 20, 0.25),
    bodySmall = bodyStyle(inter, 12, FontWeight.Normal, 16, 0.4),
    labelLarge = bodyStyle(inter, 14, FontWeight.Medium, 20, 0.1),
    labelMedium = bodyStyle(inter, 12, FontWeight.Medium, 16, 0.5),
    labelSmall = bodyStyle(inter, 11, FontWeight.Medium, 16, 0.5),
  )
}
