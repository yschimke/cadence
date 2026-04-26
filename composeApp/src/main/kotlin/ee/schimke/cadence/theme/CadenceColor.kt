package ee.schimke.cadence.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Cadence palette, three seeds from the swim/headphones visual world:
 *  - Coastal Blue (deep ocean / device body finish) → primary
 *  - Amber accent (warm action colour)              → secondary
 *  - Pool Aqua (sync-complete / safe states)        → tertiary
 *
 * Tonal roles below are derived from those seeds via Material 3 tonal palettes.
 * Light and dark schemes are tuned for the same hue family so switching modes
 * keeps the Cadence identity consistent.
 */
internal object CadencePalette {
  // Primary — Coastal Blue
  val Blue10 = Color(0xFF001E33)
  val Blue20 = Color(0xFF003352)
  val Blue40 = Color(0xFF1B5174)
  val Blue80 = Color(0xFF9CCBF0)
  val Blue90 = Color(0xFFCFE5FA)

  // Secondary — Amber accent
  val Orange10 = Color(0xFF3A0E00)
  val Orange20 = Color(0xFF5C1A00)
  val Orange30 = Color(0xFF822A04)
  val Orange40 = Color(0xFFA23A0F)
  val Orange80 = Color(0xFFFFB59A)
  val Orange90 = Color(0xFFFFDBCC)

  // Tertiary — Pool Aqua
  val Aqua20 = Color(0xFF003952)
  val Aqua40 = Color(0xFF2D7FB8)
  val Aqua80 = Color(0xFF9CCBE8)
  val Aqua90 = Color(0xFFCFE6F5)

  // Error — Material default red, tuned slightly cooler for the Cadence palette
  val Red40 = Color(0xFFBA1A1A)
  val Red80 = Color(0xFFFFB4AB)
  val Red90 = Color(0xFFFFDAD6)

  // Neutral surfaces — slightly cool grey so the Coastal Blue primary feels at home
  val Grey10 = Color(0xFF0F1416)
  val Grey20 = Color(0xFF1B2024)
  val Grey90 = Color(0xFFE2E7EB)
  val Grey95 = Color(0xFFF1F4F7)
  val Grey99 = Color(0xFFF8FAFD)
  val GreyVariant30 = Color(0xFF3F484F)
  val GreyVariant80 = Color(0xFFC0C8CF)
  val GreyVariant90 = Color(0xFFDDE3EA)
}

internal val CadenceLightColors =
  lightColorScheme(
    primary = CadencePalette.Blue40,
    onPrimary = Color.White,
    primaryContainer = CadencePalette.Blue90,
    onPrimaryContainer = CadencePalette.Blue10,
    secondary = CadencePalette.Orange40,
    onSecondary = Color.White,
    secondaryContainer = CadencePalette.Orange90,
    onSecondaryContainer = CadencePalette.Orange10,
    tertiary = CadencePalette.Aqua40,
    onTertiary = Color.White,
    tertiaryContainer = CadencePalette.Aqua90,
    onTertiaryContainer = CadencePalette.Aqua20,
    error = CadencePalette.Red40,
    onError = Color.White,
    errorContainer = CadencePalette.Red90,
    background = CadencePalette.Grey99,
    onBackground = CadencePalette.Grey10,
    surface = CadencePalette.Grey99,
    onSurface = CadencePalette.Grey10,
    surfaceVariant = CadencePalette.GreyVariant90,
    onSurfaceVariant = CadencePalette.GreyVariant30,
  )

internal val CadenceDarkColors =
  darkColorScheme(
    primary = CadencePalette.Blue80,
    onPrimary = CadencePalette.Blue20,
    primaryContainer = CadencePalette.Blue20,
    onPrimaryContainer = CadencePalette.Blue90,
    secondary = CadencePalette.Orange80,
    onSecondary = CadencePalette.Orange20,
    secondaryContainer = CadencePalette.Orange30,
    onSecondaryContainer = CadencePalette.Orange90,
    tertiary = CadencePalette.Aqua80,
    onTertiary = CadencePalette.Aqua20,
    tertiaryContainer = CadencePalette.Aqua20,
    onTertiaryContainer = CadencePalette.Aqua90,
    error = CadencePalette.Red80,
    errorContainer = Color(0xFF93000A),
    background = CadencePalette.Grey10,
    onBackground = CadencePalette.Grey90,
    surface = CadencePalette.Grey10,
    onSurface = CadencePalette.Grey90,
    surfaceVariant = CadencePalette.GreyVariant30,
    onSurfaceVariant = CadencePalette.GreyVariant80,
  )
