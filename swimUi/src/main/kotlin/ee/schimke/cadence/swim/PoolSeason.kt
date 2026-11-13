package ee.schimke.cadence.swim

import androidx.compose.ui.graphics.Color

/**
 * A season for [SwimmingPool]: its own water, what the swimmer wears, and something going on in the
 * scene, all moving on the pool's clock (so a still pool stays still).
 *
 * The app picks one from the catalog's season, the same one the headphone widget draws.
 */
enum class PoolSeason(
  /** In place of the caller's water; null keeps it. */
  val water: WaterColors?,
  val swimmer: SwimmerColors?,
) {
  None(water = null, swimmer = null),
  Winter(
    water =
      WaterColors(
        air = Color(0xFF1E2E44),
        backWave = Color(0xFF7DB3D3),
        frontWave = Color(0xFF4C8DB5),
        deep = Color(0xFF2A5D85),
        foam = Color(0xFFFFFFFF),
      ),
    swimmer = SwimmerColors.Default.copy(cap = Color(0xFF2F6FB0), pompom = Color.White),
  ),
  Christmas(
    water = null,
    swimmer =
      SwimmerColors.Default.copy(cap = Color(0xFFD7263D), brim = Color.White, pompom = Color.White),
  ),
  NewYear(
    water =
      WaterColors(
        air = Color(0xFF0D0D22),
        backWave = Color(0xFF2B3A78),
        frontWave = Color(0xFF3B4FA0),
        deep = Color(0xFF141B45),
        foam = Color(0xFFFFE7A8),
      ),
    swimmer = SwimmerColors.Default.copy(cap = Color(0xFFF2C14E), brim = Color(0xFF2A1F00)),
  ),
  Halloween(
    water =
      WaterColors(
        air = Color(0xFF1A1024),
        backWave = Color(0xFF3A2A5C),
        frontWave = Color(0xFF4B3577),
        deep = Color(0xFF1F1438),
        foam = Color(0xFFFFC98A),
      ),
    swimmer = SwimmerColors.Default.copy(cap = Color(0xFFFF7A1A)),
  ),
}
