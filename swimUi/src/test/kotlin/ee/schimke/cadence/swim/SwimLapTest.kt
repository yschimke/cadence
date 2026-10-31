package ee.schimke.cadence.swim

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SwimLapTest {
  @Test
  fun oddLapsSwimOutAndEvenLapsSwimBack() {
    assertEquals(1, SwimLap(1, 0.5f).direction)
    assertEquals(-1, SwimLap(2, 0.5f).direction)
    assertEquals(1, SwimLap(3, 0.5f).direction)
    assertTrue(SwimLap(7, 0f).isOdd)
    assertFalse(SwimLap(8, 0f).isOdd)
  }

  @Test
  fun everyLapButTheFirstStartsWithAFlipTurn() {
    assertFalse(SwimLap(1, 0f).isTurning, "the first lap dives in, it doesn't turn")
    assertTrue(SwimLap(2, 0f).isTurning)
    assertTrue(SwimLap(3, 0.01f).isTurning)
    assertFalse(SwimLap(2, 0.5f).isTurning, "already off the wall")
  }

  @Test
  fun lapsAreOneBased() {
    assertFailsWith<IllegalArgumentException> { SwimLap(0, 0f) }
  }

  @Test
  fun trackProgressComesFromPositionOverDuration() {
    assertEquals(
      SwimLap(2, 0.25f),
      SwimLap.ofTrack(2, positionMs = 450_000, durationMs = 1_800_000),
    )
    assertEquals(SwimLap(1, 0f), SwimLap.ofTrack(0, positionMs = null, durationMs = null))
    assertEquals(SwimLap(3, 0f), SwimLap.ofTrack(3, positionMs = 10, durationMs = 0))
    assertEquals(SwimLap(3, 1f), SwimLap.ofTrack(3, positionMs = 9_000, durationMs = 5_000))
  }

  @Test
  fun overallProgressSplitsIntoEqualLaps() {
    assertEquals(SwimLap(1, 0f), SwimLap.ofOverall(0f, 4))
    assertEquals(SwimLap(3, 0f), SwimLap.ofOverall(0.5f, 4))
    assertEquals(SwimLap(2, 0.5f), SwimLap.ofOverall(0.375f, 4))
    // The final wall finishes lap 4 rather than starting a fifth.
    assertEquals(SwimLap(4, 1f), SwimLap.ofOverall(1f, 4))
    assertEquals(SwimLap(1, 1f), SwimLap.ofOverall(1f, 0))
  }
}
