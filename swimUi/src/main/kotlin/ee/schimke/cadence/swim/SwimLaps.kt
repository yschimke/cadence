package ee.schimke.cadence.swim

/**
 * Where the swimmer is in the pool: [lap] is a 1-based track number and [progress] how far along
 * that lap (0 at the start wall, 1 at the far wall).
 *
 * Odd laps swim away from the start wall (left to right); even laps swim back, so half of all
 * progress fills right to left. Finishing an odd lap ends at the far wall, where the next, even,
 * lap starts with a flip turn and a push off the other way.
 */
data class SwimLap(val lap: Int, val progress: Float) {
  init {
    require(lap >= 1) { "Laps are 1-based, got $lap" }
  }

  /** +1 swimming left to right (odd laps), -1 swimming back (even laps). */
  val direction: Int
    get() = if (lap % 2 == 1) 1 else -1

  val isOdd: Boolean
    get() = lap % 2 == 1

  /**
   * True right after a wall: every lap but the first starts with a flip turn. A document captured
   * now plays the turn and push off once when it loads.
   */
  val isTurning: Boolean
    get() = lap > 1 && progress < TurnWindow

  companion object {
    /** Laps whose progress is below this still show the flip turn on load. */
    const val TurnWindow = 0.04f

    /**
     * A track as a lap: [trackNumber] is 1-based; position over duration is the lap's progress.
     * Unknown or empty durations sit at the start wall.
     */
    fun ofTrack(trackNumber: Int, positionMs: Long?, durationMs: Long?): SwimLap {
      val progress =
        if (positionMs == null || durationMs == null || durationMs <= 0) 0f
        else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
      return SwimLap(trackNumber.coerceAtLeast(1), progress)
    }

    /**
     * A run of [laps] equal laps where only the overall [fraction] is known, such as files synced
     * out of a total: lap 3 of 4 at half way through the whole swim.
     */
    fun ofOverall(fraction: Float, laps: Int): SwimLap {
      val count = laps.coerceAtLeast(1)
      val distance = fraction.coerceIn(0f, 1f) * count
      // The final wall finishes the last lap rather than starting a new one.
      val lap = (distance.toInt() + 1).coerceAtMost(count)
      return SwimLap(lap, (distance - (lap - 1)).coerceIn(0f, 1f))
    }
  }
}
