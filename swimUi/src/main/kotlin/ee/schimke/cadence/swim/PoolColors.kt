package ee.schimke.cadence.swim

import androidx.compose.ui.graphics.Color

/** Colours for [WaterBackground]: [air] above the surface, then two wave layers and the deep. */
data class WaterColors(
  val air: Color,
  val backWave: Color,
  val frontWave: Color,
  val deep: Color,
  val foam: Color,
) {
  companion object {
    /** Bright outdoor pool, for a primary action. */
    val Pool =
      WaterColors(
        air = Color(0xFF8FDDF5),
        backWave = Color(0xFF3FB4E6),
        frontWave = Color(0xFF1C8FD0),
        deep = Color(0xFF0D5FA6),
        foam = Color(0xFFF2FDFF),
      )

    /** Dusk-lit water that sits quietly on a dark Wear surface. */
    val Night =
      WaterColors(
        air = Color(0xFF1C2B3A),
        backWave = Color(0xFF1E4F73),
        frontWave = Color(0xFF17699A),
        deep = Color(0xFF0B3556),
        foam = Color(0xFFBDEBFF),
      )

    /** Warm lagoon, for something that just finished well. */
    val Lagoon =
      WaterColors(
        air = Color(0xFFA8F0DC),
        backWave = Color(0xFF4FD1B5),
        frontWave = Color(0xFF1FAE9A),
        deep = Color(0xFF0B7C74),
        foam = Color(0xFFF4FFFB),
      )
  }
}

/** Colours for [SwimLaneProgress]: the lane water, its ropes and floor line, and the swimmer. */
data class LaneColors(
  val water: Color,
  val floorLine: Color,
  val ropeA: Color,
  val ropeB: Color,
  /** Wake left behind on odd laps (swimming away from the start wall). */
  val oddWake: Color,
  /** Wake left behind on even laps (swimming back). */
  val evenWake: Color,
  val swimmer: SwimmerColors,
) {
  companion object {
    val Pool =
      LaneColors(
        water = Color(0xFF1C8FD0),
        floorLine = Color(0xFF0D4F86),
        ropeA = Color(0xFFFF6B5E),
        ropeB = Color(0xFFF7F4EA),
        oddWake = Color(0xFF9BE7FF),
        evenWake = Color(0xFFFFB45C),
        swimmer = SwimmerColors.Default,
      )

    val Night =
      LaneColors(
        water = Color(0xFF17496E),
        floorLine = Color(0xFF0B2C47),
        ropeA = Color(0xFFFF8A7A),
        ropeB = Color(0xFFDDE7EE),
        oddWake = Color(0xFF5CC8F0),
        evenWake = Color(0xFFFFA65C),
        swimmer = SwimmerColors.Default,
      )
  }
}

data class SwimmerColors(
  val skin: Color,
  val suit: Color,
  val cap: Color,
  val goggles: Color,
  val splash: Color,
) {
  companion object {
    val Default =
      SwimmerColors(
        skin = Color(0xFFF6C9A0),
        suit = Color(0xFF26324A),
        cap = Color(0xFFFF5A7A),
        goggles = Color(0xFF14202E),
        splash = Color(0xFFF2FDFF),
      )
  }
}
