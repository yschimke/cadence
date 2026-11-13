@file:Suppress("RestrictedApi", "RestrictedApiAndroidX")

package ee.schimke.cadence.swim

import androidx.compose.ui.graphics.Color
import ee.schimke.composeai.rccreation.compose.layout.RemoteDrawScope
import ee.schimke.composeai.rccreation.compose.layout.RemoteOffset
import ee.schimke.composeai.rccreation.compose.state.RemoteFloat
import ee.schimke.composeai.rccreation.compose.state.RemotePaint
import ee.schimke.composeai.rccreation.compose.state.rc
import ee.schimke.composeai.rccreation.compose.state.rf
import ee.schimke.composeai.rccreation.compose.state.sin
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin as sinOf

/**
 * What [season] adds above the water, behind the swimmer. [level] is the surface (0 top, 1 bottom).
 */
internal fun RemoteDrawScope.drawSeasonSky(season: PoolSeason, level: Float, clock: RemoteFloat) {
  val w = width
  val h = height
  when (season) {
    PoolSeason.None,
    PoolSeason.Winter,
    PoolSeason.Christmas -> Unit
    PoolSeason.NewYear ->
      PoolBursts.forEach { burst ->
        // 0 at the burst, 1 when the sparks have gone; then it fires again.
        val progress = (clock / burst.period + burst.phase) % 1f
        val spread = h * 0.30f * progress
        val spark = h * 0.05f + h * -0.05f * progress
        for (k in 0 until 8) {
          val angle = 2f * PI.toFloat() * k / 8
          drawCircle(
            fill(burst.color),
            spark,
            RemoteOffset(
              w * burst.x + spread * cos(angle),
              h * (level * burst.y) + spread * sinOf(angle),
            ),
          )
        }
      }
    PoolSeason.Halloween -> {
      val moon = RemoteOffset(w * 0.88f, h * level * 0.45f)
      drawCircle(fill(Color(0x33FF9A3C)), h * level * 0.42f, moon)
      drawCircle(fill(Color(0xFFFFB25C)), h * level * 0.28f, moon)
    }
  }
}

/** What [season] adds in front of everything: falling snow, or a skeleton shark cruising past. */
internal fun RemoteDrawScope.drawSeasonFront(season: PoolSeason, level: Float, clock: RemoteFloat) {
  val w = width
  val h = height
  when (season) {
    PoolSeason.None,
    PoolSeason.NewYear -> Unit
    PoolSeason.Winter,
    PoolSeason.Christmas ->
      PoolFlakes.forEach { flake ->
        // Falls from above the top edge into the water, and melts at the surface.
        val fall = (clock / flake.period + flake.phase) % 1f
        val y = h * -0.05f + h * (level + 0.05f) * fall
        val x = w * flake.x + sin(clock * flake.sway + flake.phase * 6.28f) * h * 0.06f
        drawCircle(fill(Color.White.copy(alpha = 0.8f)), h * flake.radius, RemoteOffset(x, y))
      }
    PoolSeason.Halloween -> {
      // A skeleton shark cruises past just under the surface, its fin cutting the water: across
      // in 20 s (a divisor of 3600), rising and sinking a little on the swell.
      val cruise = (clock / 20f + 0.55f) % 1f
      val size = h * 0.30f
      val x = w * -0.25f + w * 1.5f * cruise
      val y = h * level + size * 0.55f + sin(clock * 1.7f) * h * 0.02f
      translate(x, y) { scale(size, size, offset(0f, 0f)) { drawSharkSkeleton(clock) } }
    }
  }
}

private class PoolFlake(
  val x: Float,
  val phase: Float,
  val period: Float,
  val radius: Float,
  val sway: Float,
)

private val PoolFlakes =
  listOf(
    PoolFlake(0.07f, 0.10f, 6f, 0.030f, 1.1f),
    PoolFlake(0.19f, 0.55f, 9f, 0.022f, 0.8f),
    PoolFlake(0.33f, 0.30f, 5f, 0.026f, 1.4f),
    PoolFlake(0.46f, 0.80f, 8f, 0.020f, 0.9f),
    PoolFlake(0.58f, 0.15f, 6f, 0.030f, 1.2f),
    PoolFlake(0.71f, 0.65f, 9f, 0.022f, 1.0f),
    PoolFlake(0.83f, 0.40f, 5f, 0.026f, 1.3f),
    PoolFlake(0.95f, 0.90f, 8f, 0.020f, 0.7f),
  )

private class PoolBurst(
  val x: Float,
  val y: Float,
  val period: Float,
  val phase: Float,
  val color: Color,
)

private val PoolBursts =
  listOf(
    PoolBurst(0.20f, 0.45f, 3f, 0.0f, Color(0xFFF2C14E)),
    PoolBurst(0.55f, 0.35f, 4f, 0.4f, Color(0xFFFF6FA8)),
    PoolBurst(0.85f, 0.50f, 5f, 0.7f, Color(0xFF9AD1F5)),
  )

/**
 * A shark's skeleton facing +x, in units of its body height, centred on the spine: a fin above,
 * ribs and a skull with a snapping jaw.
 */
private fun RemoteDrawScope.drawSharkSkeleton(clock: RemoteFloat) {
  val bone = Color(0xFFEFE8D8)
  val stroke = { width: Float ->
    RemotePaint {
      color = bone.rc
      style = StrokeStyle
      strokeWidth = width.rf
      strokeCap = RoundCap
    }
  }
  val spine = stroke(0.10f)
  val thin = stroke(0.06f)
  // Dorsal fin, above the water.
  drawLine(spine, offset(-0.25f, -0.05f), offset(0.0f, -0.95f))
  drawLine(spine, offset(0.0f, -0.95f), offset(0.30f, -0.05f))
  drawLine(thin, offset(-0.05f, -0.05f), offset(0.02f, -0.6f))
  // Spine and tail, the tail sweeping as it swims.
  val sweep = sin(clock * 6f) * 0.12f
  drawLine(spine, offset(-1.25f, 0f), offset(0.75f, 0f))
  drawLine(spine, offset(-1.25f, 0f), RemoteOffset(-1.6f.rf, sweep - 0.45f))
  drawLine(spine, offset(-1.25f, 0f), RemoteOffset(-1.55f.rf, sweep + 0.35f))
  // Ribs.
  for (k in 0 until 5) {
    val rx = -0.85f + k * 0.28f
    val half = 0.32f - (k - 2) * (k - 2) * 0.04f
    drawLine(thin, offset(rx, -half), offset(rx + 0.08f, half))
  }
  // Skull, eye socket and a jaw that snaps open and shut.
  drawCircle(fill(bone), 0.30f.rf, offset(0.95f, -0.04f))
  drawCircle(fill(Color(0xFF1A1024)), 0.08f.rf, offset(1.02f, -0.12f))
  val bite = (sin(clock * 4f) + 1f) * 0.09f
  drawLine(spine, offset(0.8f, 0.16f), RemoteOffset(1.38f.rf, bite + 0.12f))
  for (k in 0 until 4) {
    val tx = 0.95f + k * 0.11f
    drawLine(thin, offset(tx, 0.12f), offset(tx + 0.03f, 0.24f))
  }
}
