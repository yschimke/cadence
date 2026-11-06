@file:Suppress("RestrictedApi", "RestrictedApiAndroidX")

package ee.schimke.cadence.swim

import androidx.compose.remote.creation.compose.layout.RemoteDrawScope
import androidx.compose.remote.creation.compose.layout.RemoteOffset
import androidx.compose.remote.creation.compose.state.RemoteFloat
import androidx.compose.remote.creation.compose.state.RemotePaint
import androidx.compose.remote.creation.compose.state.rc
import androidx.compose.remote.creation.compose.state.rf
import androidx.compose.remote.creation.compose.state.sin
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.StrokeCap
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin as sinOf

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
      translate(x, y) { scale(size, size, RemoteOffset(0f, 0f)) { drawSharkSkeleton(clock) } }
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
      style = PaintingStyle.Stroke
      strokeWidth = width.rf
      strokeCap = StrokeCap.Round
    }
  }
  val spine = stroke(0.10f)
  val thin = stroke(0.06f)
  // Dorsal fin, above the water.
  drawLine(spine, RemoteOffset(-0.25f, -0.05f), RemoteOffset(0.0f, -0.95f))
  drawLine(spine, RemoteOffset(0.0f, -0.95f), RemoteOffset(0.30f, -0.05f))
  drawLine(thin, RemoteOffset(-0.05f, -0.05f), RemoteOffset(0.02f, -0.6f))
  // Spine and tail, the tail sweeping as it swims.
  val sweep = sin(clock * 6f) * 0.12f
  drawLine(spine, RemoteOffset(-1.25f, 0f), RemoteOffset(0.75f, 0f))
  drawLine(spine, RemoteOffset(-1.25f, 0f), RemoteOffset(-1.6f.rf, sweep - 0.45f))
  drawLine(spine, RemoteOffset(-1.25f, 0f), RemoteOffset(-1.55f.rf, sweep + 0.35f))
  // Ribs.
  for (k in 0 until 5) {
    val rx = -0.85f + k * 0.28f
    val half = 0.32f - (k - 2) * (k - 2) * 0.04f
    drawLine(thin, RemoteOffset(rx, -half), RemoteOffset(rx + 0.08f, half))
  }
  // Skull, eye socket and a jaw that snaps open and shut.
  drawCircle(fill(bone), 0.30f.rf, RemoteOffset(0.95f, -0.04f))
  drawCircle(fill(Color(0xFF1A1024)), 0.08f.rf, RemoteOffset(1.02f, -0.12f))
  val bite = (sin(clock * 4f) + 1f) * 0.09f
  drawLine(spine, RemoteOffset(0.8f, 0.16f), RemoteOffset(1.38f.rf, bite + 0.12f))
  for (k in 0 until 4) {
    val tx = 0.95f + k * 0.11f
    drawLine(thin, RemoteOffset(tx, 0.12f), RemoteOffset(tx + 0.03f, 0.24f))
  }
}
