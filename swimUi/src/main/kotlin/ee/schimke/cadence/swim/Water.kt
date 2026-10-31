@file:Suppress("RestrictedApi", "RestrictedApiAndroidX")

package ee.schimke.cadence.swim

import androidx.compose.remote.creation.RemotePath
import androidx.compose.remote.creation.compose.layout.RemoteCanvas
import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.remote.creation.compose.layout.RemoteDrawScope
import androidx.compose.remote.creation.compose.layout.RemoteOffset
import androidx.compose.remote.creation.compose.layout.RemoteSize
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.drawWithContent
import androidx.compose.remote.creation.compose.state.RemoteFloat
import androidx.compose.remote.creation.compose.state.RemotePaint
import androidx.compose.remote.creation.compose.state.RemoteTimeVariables
import androidx.compose.remote.creation.compose.state.rc
import androidx.compose.remote.creation.compose.state.rf
import androidx.compose.remote.creation.compose.state.sin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PaintingStyle

/** Seconds since the player loaded the document: the clock every animation here runs on. */
val SwimClock: RemoteFloat
  get() = RemoteTimeVariables.animationTime

/**
 * The clock the swimming composables animate on: [SwimClock] unless a preview or capture pins it to
 * a moment, so that a single frame of any animation can be rendered.
 */
val LocalSwimClock = staticCompositionLocalOf { SwimClock }

/**
 * Animated water to sit behind a button's content: two wave layers drifting against each other,
 * rising bubbles and surface glints. The player animates it from [clock]; nothing is re-captured
 * per frame.
 *
 * [level] is the resting surface as a fraction of the height from the top; [crests] how many wave
 * crests span the width (2 suits a round or pill button, more for a wide strip). Clip the modifier
 * to the button's shape: the waves are drawn wider than the canvas and clipped to its rectangle.
 */
@Composable
@RemoteComposable
fun WaterBackground(
  modifier: RemoteModifier = RemoteModifier,
  colors: WaterColors = WaterColors.Pool,
  level: Float = 0.38f,
  crests: Int = 2,
  bubbles: Int = 5,
  clock: RemoteFloat = LocalSwimClock.current,
) {
  RemoteCanvas(modifier) { drawWater(colors, level, crests, bubbles, clock) }
}

/**
 * [WaterBackground] as a modifier: water drawn behind whatever the component draws, so it can sit
 * under an existing button (give the button a transparent container). Put a `clip` to the button's
 * shape before it.
 */
fun RemoteModifier.waterBackground(
  colors: WaterColors = WaterColors.Pool,
  level: Float = 0.38f,
  crests: Int = 2,
  bubbles: Int = 5,
  clock: RemoteFloat = SwimClock,
): RemoteModifier = drawWithContent {
  drawWater(colors, level, crests, bubbles, clock)
  drawContent()
}

/** [WaterBackground]'s drawing, for canvases that layer more on top. */
fun RemoteDrawScope.drawWater(
  colors: WaterColors,
  level: Float = 0.38f,
  crests: Int = 2,
  bubbles: Int = 5,
  clock: RemoteFloat = SwimClock,
  bubbleScale: Float = 1f,
  amplitude: Float = 0.075f,
) {
  val w = width
  val h = height
  val wavelength = w / crests.coerceAtLeast(1).toFloat()
  clipRect(0f.rf, 0f.rf, w, h) {
    drawRect(fill(colors.air), RemoteOffset(0f, 0f), RemoteSize(w, h))
    // Sun glints dancing just above the surface.
    Glints.forEach { glint ->
      val twinkle = sin(clock * glint.speed + glint.phase)
      drawCircle(
        fill(colors.foam.copy(alpha = 0.85f)),
        h * (0.035f * bubbleScale) * clampZero(twinkle),
        RemoteOffset(w * glint.x, h * (level - 0.16f * bubbleScale)),
      )
    }
    // Back layer drifts right and slower; front layer drifts left: the parallax reads as swell.
    drawWave(colors.backWave, wavelength, h, level - 0.07f, amplitude * 0.8f, crests) {
      clock / 3.6f
    }
    drawWave(colors.frontWave, wavelength, h, level, amplitude, crests, reverse = false) {
      clock / 2.4f + 0.3f
    }
    // A highlight riding the front crest.
    drawWaveLine(colors.foam.copy(alpha = 0.55f), wavelength, h, level, amplitude, crests) {
      clock / 2.4f + 0.3f
    }
    drawRect(
      fill(colors.deep.copy(alpha = 0.55f)),
      RemoteOffset(0f.rf, h * 0.78f),
      RemoteSize(w, h * 0.22f),
    )
    Bubbles.take(bubbles.coerceIn(0, Bubbles.size)).forEachIndexed { index, bubble ->
      drawBubble(bubble, index, w, h, level, colors.foam, clock, bubbleScale)
    }
  }
}

private fun RemoteDrawScope.drawBubble(
  bubble: Bubble,
  index: Int,
  w: RemoteFloat,
  h: RemoteFloat,
  level: Float,
  foam: Color,
  clock: RemoteFloat,
  scale: Float,
) {
  // 0 at the bottom, 1 at the surface, then it pops and starts again.
  val rise = (clock / bubble.period + bubble.phase) % 1f
  val bottom = 1.08f
  val y = h * bottom - h * (bottom - level - 0.04f) * rise
  val x = w * bubble.x + sin(clock * 1.9f + index * 1.3f) * h * 0.045f
  val radius = h * (bubble.radius * scale) * (rise * 0.5f + 0.6f)
  drawCircle(
    RemotePaint {
      color = foam.copy(alpha = 0.75f).rc
      style = PaintingStyle.Stroke
      strokeWidth = h * (0.014f * scale)
    },
    radius,
    RemoteOffset(x, y),
  )
  drawCircle(
    fill(foam.copy(alpha = 0.9f)),
    radius * 0.3f,
    RemoteOffset(x - radius * 0.35f, y - radius * 0.35f),
  )
}

/**
 * One filled wave layer. The path is built once in wavelength units (x) and height units (y), then
 * scaled to the canvas and slid by a whole wavelength per cycle so it loops seamlessly.
 */
private fun RemoteDrawScope.drawWave(
  color: Color,
  wavelength: RemoteFloat,
  h: RemoteFloat,
  level: Float,
  amplitude: Float,
  crests: Int,
  reverse: Boolean = true,
  cycles: () -> RemoteFloat,
) {
  val path = wavePath(crests + 1, amplitude, filled = true)
  slideWave(wavelength, h, level, reverse, cycles()) { drawPath(path, fill(color)) }
}

private fun RemoteDrawScope.drawWaveLine(
  color: Color,
  wavelength: RemoteFloat,
  h: RemoteFloat,
  level: Float,
  amplitude: Float,
  crests: Int,
  cycles: () -> RemoteFloat,
) {
  val path = wavePath(crests + 1, amplitude, filled = false)
  slideWave(wavelength, h, level, reverse = false, cycles()) {
    drawPath(
      path,
      RemotePaint {
        this.color = color.rc
        style = PaintingStyle.Stroke
        // In height units: the path is scaled by the canvas height vertically.
        strokeWidth = 0.03f.rf
      },
    )
  }
}

private fun RemoteDrawScope.slideWave(
  wavelength: RemoteFloat,
  h: RemoteFloat,
  level: Float,
  reverse: Boolean,
  cycles: RemoteFloat,
  draw: RemoteDrawScope.() -> Unit,
) {
  val shift = (cycles % 1f) * wavelength
  // Both directions keep the path's left edge in [-wavelength, 0], so it always covers the width.
  val dx = if (reverse) shift - wavelength else -shift
  translate(dx, h * level) { scale(wavelength, h, RemoteOffset(0f, 0f)) { draw() } }
}

/**
 * A sine-like surface of [waves] wavelengths from x = 0, at y = 0 with peaks at ±[amplitude].
 * Quadratic segments with control points at twice the amplitude meet with continuous tangents.
 */
private fun wavePath(waves: Int, amplitude: Float, filled: Boolean): RemotePath =
  RemotePath().apply {
    moveTo(0f, 0f)
    for (k in 0 until waves) {
      quadTo(k + 0.25f, -2f * amplitude, k + 0.5f, 0f)
      quadTo(k + 0.75f, 2f * amplitude, k + 1f, 0f)
    }
    if (filled) {
      lineTo(waves.toFloat(), 1.2f)
      lineTo(0f, 1.2f)
      close()
    }
  }

internal fun fill(color: Color) = RemotePaint {
  this.color = color.rc
  style = PaintingStyle.Fill
}

/** max(0, v) without leaving the expression: (v + |v|) / 2. */
internal fun clampZero(v: RemoteFloat): RemoteFloat =
  (v + androidx.compose.remote.creation.compose.state.abs(v)) * 0.5f

private class Bubble(val x: Float, val period: Float, val phase: Float, val radius: Float)

private class Glint(val x: Float, val speed: Float, val phase: Float)

// Periods are distinct and not multiples of each other, so the bubbles never fall into step.
private val Bubbles =
  listOf(
    Bubble(x = 0.2f, period = 3.1f, phase = 0.0f, radius = 0.05f),
    Bubble(x = 0.47f, period = 4.3f, phase = 0.55f, radius = 0.035f),
    Bubble(x = 0.72f, period = 3.7f, phase = 0.25f, radius = 0.06f),
    Bubble(x = 0.86f, period = 5.2f, phase = 0.8f, radius = 0.04f),
    Bubble(x = 0.33f, period = 4.7f, phase = 0.4f, radius = 0.03f),
    Bubble(x = 0.6f, period = 3.4f, phase = 0.9f, radius = 0.045f),
  )

private val Glints =
  listOf(
    Glint(x = 0.24f, speed = 2.3f, phase = 0f),
    Glint(x = 0.58f, speed = 1.7f, phase = 2.1f),
    Glint(x = 0.8f, speed = 2.9f, phase = 4.0f),
  )
