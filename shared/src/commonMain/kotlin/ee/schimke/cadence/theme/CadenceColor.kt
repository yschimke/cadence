package ee.schimke.cadence.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Cadence palette, three seeds from the swim/headphones visual world, for surfaces that can't
 * follow wallpaper colours (desktop, the browser):
 * - Coastal Blue (deep ocean / device body finish) → primary
 * - Amber accent (warm action colour) → secondary
 * - Pool Aqua (sync-complete / safe states) → tertiary
 *
 * Tones follow Material 3's tonal steps (10 darkest, 99 lightest), hand-derived from each seed.
 */
internal object CadencePalette {
  // Primary — Coastal Blue
  val Blue10 = Color(0xFF001E33)
  val Blue20 = Color(0xFF003352)
  val Blue30 = Color(0xFF0D4566)
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
  val Aqua10 = Color(0xFF001E30)
  val Aqua20 = Color(0xFF003952)
  val Aqua30 = Color(0xFF0E5A85)
  val Aqua40 = Color(0xFF2D7FB8)
  val Aqua80 = Color(0xFF9CCBE8)
  val Aqua90 = Color(0xFFCFE6F5)

  // Error — Material default red
  val Red10 = Color(0xFF410002)
  val Red20 = Color(0xFF690005)
  val Red30 = Color(0xFF93000A)
  val Red40 = Color(0xFFBA1A1A)
  val Red80 = Color(0xFFFFB4AB)
  val Red90 = Color(0xFFFFDAD6)

  // Neutral surfaces — slightly cool grey so the Coastal Blue primary feels at home
  val Grey4 = Color(0xFF0A0F11)
  val Grey6 = Color(0xFF0F1416)
  val Grey10 = Color(0xFF171C1F)
  val Grey12 = Color(0xFF1B2024)
  val Grey17 = Color(0xFF252B2E)
  val Grey20 = Color(0xFF2C3134)
  val Grey22 = Color(0xFF30363A)
  val Grey24 = Color(0xFF353A3D)
  val Grey87 = Color(0xFFD8DADE)
  val Grey90 = Color(0xFFE2E7EB)
  val Grey92 = Color(0xFFE6E9EC)
  val Grey94 = Color(0xFFECEFF2)
  val Grey95 = Color(0xFFEEF1F4)
  val Grey96 = Color(0xFFF2F4F7)
  val Grey99 = Color(0xFFF8FAFD)
  val GreyVariant30 = Color(0xFF3F484F)
  val GreyVariant50 = Color(0xFF6F787F)
  val GreyVariant60 = Color(0xFF899299)
  val GreyVariant80 = Color(0xFFC0C8CF)
  val GreyVariant90 = Color(0xFFDDE3EA)
}

/** Light Cadence scheme: tone 40 accents on tone 99 surfaces. */
internal val CadenceLightColors: ColorScheme =
  with(CadencePalette) {
    lightColorScheme(
      primary = Blue40,
      onPrimary = Color.White,
      primaryContainer = Blue90,
      onPrimaryContainer = Blue10,
      inversePrimary = Blue80,
      secondary = Orange40,
      onSecondary = Color.White,
      secondaryContainer = Orange90,
      onSecondaryContainer = Orange10,
      tertiary = Aqua40,
      onTertiary = Color.White,
      tertiaryContainer = Aqua90,
      onTertiaryContainer = Aqua10,
      background = Grey99,
      onBackground = Grey10,
      surface = Grey99,
      onSurface = Grey10,
      surfaceVariant = GreyVariant90,
      onSurfaceVariant = GreyVariant30,
      surfaceTint = Blue40,
      inverseSurface = Grey20,
      inverseOnSurface = Grey95,
      error = Red40,
      onError = Color.White,
      errorContainer = Red90,
      onErrorContainer = Red10,
      outline = GreyVariant50,
      outlineVariant = GreyVariant80,
      surfaceBright = Grey99,
      surfaceDim = Grey87,
      surfaceContainerLowest = Color.White,
      surfaceContainerLow = Grey96,
      surfaceContainer = Grey94,
      surfaceContainerHigh = Grey92,
      surfaceContainerHighest = Grey90,
    )
  }

/** Dark Cadence scheme: tone 80 accents on tone 6 surfaces. */
internal val CadenceDarkColors: ColorScheme =
  with(CadencePalette) {
    darkColorScheme(
      primary = Blue80,
      onPrimary = Blue20,
      primaryContainer = Blue30,
      onPrimaryContainer = Blue90,
      inversePrimary = Blue40,
      secondary = Orange80,
      onSecondary = Orange20,
      secondaryContainer = Orange30,
      onSecondaryContainer = Orange90,
      tertiary = Aqua80,
      onTertiary = Aqua20,
      tertiaryContainer = Aqua30,
      onTertiaryContainer = Aqua90,
      background = Grey6,
      onBackground = Grey90,
      surface = Grey6,
      onSurface = Grey90,
      surfaceVariant = GreyVariant30,
      onSurfaceVariant = GreyVariant80,
      surfaceTint = Blue80,
      inverseSurface = Grey90,
      inverseOnSurface = Grey20,
      error = Red80,
      onError = Red20,
      errorContainer = Red30,
      onErrorContainer = Red90,
      outline = GreyVariant60,
      outlineVariant = GreyVariant30,
      surfaceBright = Grey24,
      surfaceDim = Grey6,
      surfaceContainerLowest = Grey4,
      surfaceContainerLow = Grey10,
      surfaceContainer = Grey12,
      surfaceContainerHigh = Grey17,
      surfaceContainerHighest = Grey22,
    )
  }
