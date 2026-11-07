@file:Suppress("RestrictedApi", "RestrictedApiAndroidX")

package ee.schimke.cadence.widgets

import androidx.compose.remote.creation.compose.layout.RemoteCanvas
import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.remote.creation.compose.layout.RemoteDrawScope
import androidx.compose.remote.creation.compose.layout.RemoteOffset
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.fillMaxSize
import androidx.compose.remote.creation.compose.state.RemoteFloat
import androidx.compose.remote.creation.compose.state.RemotePaint
import androidx.compose.remote.creation.compose.state.rc
import androidx.compose.remote.creation.compose.state.sin
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import ee.schimke.cadence.swim.PoolSeason
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin as sinOf

/**
 * The headphone widget's looks, one Livery design each. [Default] follows the system's dynamic
 * colours, written in per render. The rest are seasonal designs (`composeApp/livery/seasons/`),
 * each with its own fixed palette, the same on every home screen whether it is light or dark, and
 * an animated backdrop.
 *
 * A backdrop's [backdrop] is drawn under the content from a clock in seconds. The template passes
 * the wall clock while audio plays and a constant otherwise, so a paused widget holds still and
 * costs no repaints, like the water.
 */
enum class HeadphoneLook(
  val designId: String,
  val colors: WidgetColors?,
  val backdrop: (@Composable @RemoteComposable (clock: RemoteFloat) -> Unit)?,
  /** The same season in the swimming pool of Cadence's other widgets (the podcast sync card). */
  val pool: PoolSeason,
) {
  Default("headphone-default", colors = null, backdrop = null, PoolSeason.None),
  Winter("headphone-winter", WinterWidgetColors, { Snowfall(it) }, PoolSeason.Winter),
  Christmas(
    "headphone-christmas",
    ChristmasWidgetColors,
    { ChristmasLights(it) },
    PoolSeason.Christmas,
  ),
  NewYear("headphone-new-year", NewYearWidgetColors, { Fireworks(it) }, PoolSeason.NewYear),
  Halloween(
    "headphone-halloween",
    HalloweenWidgetColors,
    { HalloweenNight(it) },
    PoolSeason.Halloween,
  );

  companion object {
    fun ofDesign(designId: String?): HeadphoneLook =
      entries.firstOrNull { it.designId == designId } ?: Default
  }
}

/**
 * The look the season running now gives Cadence's widgets. The app sets [current] to ask its Livery
 * catalog, which decides the seasons; until then, and in previews, it is [HeadphoneLook.Default].
 */
object WidgetSeason {
  @Volatile var current: suspend () -> HeadphoneLook = { HeadphoneLook.Default }
}

/** Night sky and ice. */
val WinterWidgetColors =
  WidgetColors(
    background = Color(0xFF0F1B2D),
    onBackground = Color(0xFFEFF6FF),
    supporting = Color(0xFFA8C2DD),
    tonal = Color(0xFF223A57),
    onTonal = Color(0xFFDCEBFA),
    primary = Color(0xFFCDEBFF),
    onPrimary = Color(0xFF0B2740),
    tertiary = Color(0xFFB9E4D6),
    warning = Color(0xFFF2D7A6),
  )

/** Pine, cream and berry red. */
val ChristmasWidgetColors =
  WidgetColors(
    background = Color(0xFF0E2A1E),
    onBackground = Color(0xFFFFF6E5),
    supporting = Color(0xFFC9D9C4),
    tonal = Color(0xFF1F4532),
    onTonal = Color(0xFFF3EAD3),
    primary = Color(0xFFD7263D),
    onPrimary = Color(0xFFFFF6E5),
    tertiary = Color(0xFFF2C14E),
    warning = Color(0xFFF2C14E),
  )

/** Midnight and champagne gold. */
val NewYearWidgetColors =
  WidgetColors(
    background = Color(0xFF0B0B1A),
    onBackground = Color(0xFFFFF8E7),
    supporting = Color(0xFFC9C3D9),
    tonal = Color(0xFF24223D),
    onTonal = Color(0xFFF1E9FF),
    primary = Color(0xFFF2C14E),
    onPrimary = Color(0xFF2A1F00),
    tertiary = Color(0xFF9AD1F5),
    warning = Color(0xFFF2C14E),
  )

/** A purple night with a pumpkin-orange glow. */
val HalloweenWidgetColors =
  WidgetColors(
    background = Color(0xFF1A1024),
    onBackground = Color(0xFFFFEEDD),
    supporting = Color(0xFFCDB8DC),
    tonal = Color(0xFF34204A),
    onTonal = Color(0xFFF4E2FF),
    primary = Color(0xFFFF7A1A),
    onPrimary = Color(0xFF2B1000),
    tertiary = Color(0xFFB6E36A),
    warning = Color(0xFFFF7A1A),
  )

/** Snow over the whole widget. */
@Composable
@RemoteComposable
fun Snowfall(clock: RemoteFloat, modifier: RemoteModifier = RemoteModifier.fillMaxSize()) {
  RemoteCanvas(modifier) { drawSnow(clock, Flakes) }
}

/** A string of twinkling bulbs along the top edge, with a little snow. */
@Composable
@RemoteComposable
fun ChristmasLights(clock: RemoteFloat, modifier: RemoteModifier = RemoteModifier.fillMaxSize()) {
  RemoteCanvas(modifier) {
    val w = width
    val h = height
    drawSnow(clock, Flakes.filterIndexed { i, _ -> i % 2 == 0 })
    val bulbs = 11
    for (i in 0 until bulbs) {
      val x = (i + 0.5f) / bulbs
      // The wire sags between the corners.
      val sag = 0.05f + 0.035f * sinOf(PI.toFloat() * x)
      val twinkle = (sin(clock * (1.3f + 0.17f * (i % 4)) + i * 1.9f) + 1f) * 0.5f
      drawCircle(
        fill(BulbColors[i % BulbColors.size]),
        h * 0.016f + h * 0.008f * twinkle,
        RemoteOffset(w * x, h * sag),
      )
    }
  }
}

/** Fireworks bursting in turn across the sky. */
@Composable
@RemoteComposable
fun Fireworks(clock: RemoteFloat, modifier: RemoteModifier = RemoteModifier.fillMaxSize()) {
  RemoteCanvas(modifier) {
    val w = width
    val h = height
    Bursts.forEach { burst ->
      // 0 at the burst, 1 when its sparks have faded out; then it fires again.
      val progress = (clock / burst.period + burst.phase) % 1f
      val spread = h * burst.size * progress
      val spark = h * 0.018f + h * -0.018f * progress
      for (k in 0 until SPARKS) {
        val angle = 2f * PI.toFloat() * k / SPARKS
        drawCircle(
          fill(burst.color),
          spark,
          RemoteOffset(w * burst.x + spread * cos(angle), h * burst.y + spread * sinOf(angle)),
        )
      }
    }
    // Still stars between the bursts.
    Stars.forEach { (x, y) ->
      drawCircle(fill(Color(0x99FFF8E7)), h * 0.008f, RemoteOffset(w * x, h * y))
    }
  }
}

/** A harvest moon, with embers drifting up past it. */
@Composable
@RemoteComposable
fun HalloweenNight(clock: RemoteFloat, modifier: RemoteModifier = RemoteModifier.fillMaxSize()) {
  RemoteCanvas(modifier) {
    val w = width
    val h = height
    // Low in the corner, clear of the text and the buttons.
    drawCircle(fill(Color(0x33FF9A3C)), h * 0.20f, RemoteOffset(w * 0.93f, h * 0.92f))
    drawCircle(fill(Color(0xFFFFB25C)), h * 0.12f, RemoteOffset(w * 0.93f, h * 0.92f))
    drawCircle(fill(Color(0x40B8641F)), h * 0.025f, RemoteOffset(w * 0.91f, h * 0.88f))
    drawCircle(fill(Color(0x40B8641F)), h * 0.018f, RemoteOffset(w * 0.95f, h * 0.95f))
    Embers.forEachIndexed { i, ember ->
      // 0 below the bottom edge, 1 above the top.
      val rise = (clock / ember.period + ember.phase) % 1f
      val y = h * 1.1f + h * -1.2f * rise
      val x = w * ember.x + sin(clock * ember.sway + i * 2.1f) * h * 0.05f
      drawCircle(fill(EmberColors[i % EmberColors.size]), h * ember.radius, RemoteOffset(x, y))
    }
  }
}

private fun RemoteDrawScope.drawSnow(clock: RemoteFloat, flakes: List<Drift>) {
  val w = width
  val h = height
  flakes.forEach { flake ->
    // 0 above the top edge, 1 below the bottom. Every period divides 3600, so the clock's hourly
    // wrap is seamless.
    val fall = (clock / flake.period + flake.phase) % 1f
    val y = h * (fall * 1.2f - 0.1f)
    val x = w * flake.x + sin(clock * flake.sway + flake.phase * 6.28f) * h * 0.04f
    drawCircle(fill(Color.White.copy(alpha = flake.alpha)), h * flake.radius, RemoteOffset(x, y))
  }
}

private fun fill(color: Color) = RemotePaint { this.color = color.rc }

/** Something that drifts across the widget: snow down, embers up. */
private class Drift(
  val x: Float,
  val phase: Float,
  val period: Float,
  val radius: Float,
  val alpha: Float,
  val sway: Float,
)

/** A fixed scatter, so every capture draws the same snow, spread across the width and the fall. */
private val Flakes =
  listOf(
    Drift(0.06f, 0.10f, 12f, 0.018f, 0.55f, 0.9f),
    Drift(0.17f, 0.62f, 15f, 0.012f, 0.40f, 1.3f),
    Drift(0.29f, 0.35f, 10f, 0.022f, 0.50f, 0.7f),
    Drift(0.41f, 0.83f, 18f, 0.010f, 0.35f, 1.1f),
    Drift(0.52f, 0.21f, 12f, 0.016f, 0.45f, 1.5f),
    Drift(0.63f, 0.71f, 15f, 0.020f, 0.50f, 0.8f),
    Drift(0.74f, 0.47f, 10f, 0.012f, 0.40f, 1.2f),
    Drift(0.85f, 0.05f, 18f, 0.018f, 0.45f, 1.0f),
    Drift(0.94f, 0.56f, 12f, 0.014f, 0.35f, 1.4f),
  )

private val Embers =
  listOf(
    Drift(0.10f, 0.15f, 15f, 0.012f, 1f, 0.8f),
    Drift(0.27f, 0.70f, 12f, 0.009f, 1f, 1.2f),
    Drift(0.45f, 0.40f, 18f, 0.011f, 1f, 0.9f),
    Drift(0.61f, 0.90f, 15f, 0.008f, 1f, 1.4f),
    Drift(0.78f, 0.55f, 10f, 0.010f, 1f, 1.0f),
  )

private val BulbColors =
  listOf(Color(0xFFFF4D5E), Color(0xFFF2C14E), Color(0xFF4CD08A), Color(0xFF6FB7FF))

private val EmberColors = listOf(Color(0xCCFF8A2A), Color(0x99C38BFF), Color(0xAAFFB25C))

private class Burst(
  val x: Float,
  val y: Float,
  val size: Float,
  val period: Float,
  val phase: Float,
  val color: Color,
)

private const val SPARKS = 8

/** Along the top-right and bottom edges, clear of the title and the buttons. */
private val Bursts =
  listOf(
    Burst(0.84f, 0.16f, 0.16f, 4f, 0.0f, Color(0xFFF2C14E)),
    Burst(0.30f, 0.92f, 0.14f, 5f, 0.45f, Color(0xFFFF6FA8)),
    Burst(0.72f, 0.92f, 0.15f, 6f, 0.75f, Color(0xFF9AD1F5)),
  )

private val Stars =
  listOf(0.08f to 0.08f, 0.33f to 0.06f, 0.55f to 0.07f, 0.66f to 0.12f, 0.06f to 0.94f)
