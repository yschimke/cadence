@file:Suppress("RestrictedApi", "RestrictedApiAndroidX")

package ee.schimke.cadence.swim

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import ee.schimke.composeai.rccreation.compose.layout.RemoteCanvas
import ee.schimke.composeai.rccreation.compose.layout.RemoteComposable
import ee.schimke.composeai.rccreation.compose.layout.RemoteDrawScope
import ee.schimke.composeai.rccreation.compose.layout.RemoteOffset
import ee.schimke.composeai.rccreation.compose.layout.RemoteSize
import ee.schimke.composeai.rccreation.compose.modifier.RemoteModifier
import ee.schimke.composeai.rccreation.compose.path.RemotePath
import ee.schimke.composeai.rccreation.compose.state.RemoteFloat
import ee.schimke.composeai.rccreation.compose.state.RemoteInt
import ee.schimke.composeai.rccreation.compose.state.RemotePaint
import ee.schimke.composeai.rccreation.compose.state.RemoteTimeVariables
import ee.schimke.composeai.rccreation.compose.state.clamp
import ee.schimke.composeai.rccreation.compose.state.rc
import ee.schimke.composeai.rccreation.compose.state.rf
import ee.schimke.composeai.rccreation.compose.state.ri
import ee.schimke.composeai.rccreation.compose.state.sin
import java.time.LocalTime

/**
 * The second of the hour, continuous (0 until 3600): the clock every animation here runs on.
 *
 * Not the player's animation time: Android 16's platform widget player only schedules a repaint for
 * a document that reads the wall-clock variables (continuous seconds every frame, seconds, or
 * minutes), so a document driven by animation time draws once and freezes on the home screen. Every
 * period here divides 3600, so the hourly wrap is seamless.
 */
val SwimClock: RemoteFloat
  get() = RemoteTimeVariables.continuousSeconds

/**
 * The clock the swimming composables animate on: [SwimClock] unless a preview or capture pins it to
 * a moment, so that a single frame of any animation can be rendered.
 */
val LocalSwimClock = staticCompositionLocalOf { SwimClock }

/**
 * [LocalSwimClock]'s reading when the document was captured, for one-shot animations (a flip turn
 * as a new lap starts) that count seconds from then. [SwimClockAt] sets it to 0.
 */
val LocalSwimClockStart = staticCompositionLocalOf { secondOfHourNow() }

/** Seconds since [start] on a clock that wraps every hour. */
fun RemoteFloat.secondsSince(start: Float): RemoteFloat = (this - start + 3600f) % 3600f

private fun secondOfHourNow(): Float {
  val now = LocalTime.now()
  return now.minute * 60f + now.second + now.nano / 1_000_000_000f
}

/** Runs [content] with the swim animations frozen at [seconds]. */
@Composable
@RemoteComposable
fun SwimClockAt(seconds: Float, content: @Composable @RemoteComposable () -> Unit) {
  CompositionLocalProvider(
    LocalSwimClock provides seconds.rf,
    LocalSwimClockStart provides 0f,
    content = content,
  )
}

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
  /**
   * The canvas's size in pixels when the caller knows it, so the drawing doesn't read the canvas's
   * measured size. Pass it for water inside a conditional layout: Android 16's platform widget
   * player evaluates a hidden branch's drawing too, before that branch has a size, and fails.
   */
  sizePx: RemoteFloat? = null,
) {
  RemoteCanvas(modifier) {
    drawWater(
      colors,
      level.rf,
      crests.ri,
      bubbles.ri,
      clock,
      w = sizePx ?: width,
      h = sizePx ?: height,
    )
  }
}

/**
 * [WaterBackground]'s drawing, for canvases that layer more on top. [level], [crests], [bubbles]
 * and [bubbleScale] may be values that change while the document plays. A constant [crests] or
 * [bubbles] draws exactly that many; one that can change draws for up to [MaxCrests] crests and all
 * the bubbles, hiding the ones not wanted.
 */
fun RemoteDrawScope.drawWater(
  colors: WaterColors,
  level: RemoteFloat = 0.38f.rf,
  crests: RemoteInt = 2.ri,
  bubbles: RemoteInt = 5.ri,
  clock: RemoteFloat = SwimClock,
  bubbleScale: RemoteFloat = 1f.rf,
  amplitude: Float = 0.075f,
  w: RemoteFloat = width,
  h: RemoteFloat = height,
) {
  val crestCount = clamp(crests, 1.ri, MaxCrests.ri)
  // The wave paths cover the most crests the drawing can be asked for.
  val pathCrests = crests.constantValueOrNull?.coerceIn(1, MaxCrests) ?: MaxCrests
  val wavelength = w / crestCount.toRemoteFloat()
  clipRect(0f.rf, 0f.rf, w, h) {
    drawRect(fill(colors.air), offset(0f, 0f), RemoteSize(w, h))
    // Sun glints dancing just above the surface.
    Glints.forEach { glint ->
      val twinkle = sin(clock * glint.speed + glint.phase)
      drawCircle(
        fill(colors.foam.copy(alpha = 0.85f)),
        h * bubbleScale * 0.035f * clampZero(twinkle),
        RemoteOffset(w * glint.x, h * (level - bubbleScale * 0.16f)),
      )
    }
    // Back layer drifts right and slower; front layer drifts left: the parallax reads as swell.
    drawWave(colors.backWave, wavelength, h, level - 0.07f, amplitude * 0.8f, pathCrests) {
      clock / 3.6f
    }
    drawWave(colors.frontWave, wavelength, h, level, amplitude, pathCrests, reverse = false) {
      clock / 2.4f + 0.3f
    }
    // A highlight riding the front crest.
    drawWaveLine(colors.foam.copy(alpha = 0.55f), wavelength, h, level, amplitude, pathCrests) {
      clock / 2.4f + 0.3f
    }
    drawRect(
      fill(colors.deep.copy(alpha = 0.55f)),
      RemoteOffset(0f.rf, h * 0.78f),
      RemoteSize(w, h * 0.22f),
    )
    val fixed = bubbles.constantValueOrNull
    if (fixed != null) {
      Bubbles.take(fixed.coerceIn(0, Bubbles.size)).forEachIndexed { index, bubble ->
        drawBubble(bubble, index, w, h, level, colors.foam, clock, bubbleScale)
      }
    } else {
      // Every bubble, those past the count shrunk to nothing.
      Bubbles.forEachIndexed { index, bubble ->
        val shown = index.ri.isLessThan(bubbles).select(1f.rf, 0f.rf)
        drawBubble(bubble, index, w, h, level, colors.foam, clock, bubbleScale * shown)
      }
    }
  }
}

/** The most wave crests [drawWater] draws across its width. */
const val MaxCrests = 8

private fun RemoteDrawScope.drawBubble(
  bubble: Bubble,
  index: Int,
  w: RemoteFloat,
  h: RemoteFloat,
  level: RemoteFloat,
  foam: Color,
  clock: RemoteFloat,
  scale: RemoteFloat,
) {
  // 0 at the bottom, 1 at the surface, then it pops and starts again.
  val rise = (clock / bubble.period + bubble.phase) % 1f
  val bottom = 1.08f
  val y = h * bottom - h * (level * -1f + (bottom - 0.04f)) * rise
  val x = w * bubble.x + sin(clock * (TwoPi / 3f) + index * 1.3f) * h * 0.045f
  val radius = h * scale * bubble.radius * (rise * 0.5f + 0.6f)
  drawCircle(
    RemotePaint {
      color = foam.copy(alpha = 0.75f).rc
      style = StrokeStyle
      strokeWidth = h * scale * 0.014f
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
  level: RemoteFloat,
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
  level: RemoteFloat,
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
        style = StrokeStyle
        // In height units: the path is scaled by the canvas height vertically.
        strokeWidth = 0.03f.rf
      },
    )
  }
}

private fun RemoteDrawScope.slideWave(
  wavelength: RemoteFloat,
  h: RemoteFloat,
  level: RemoteFloat,
  reverse: Boolean,
  cycles: RemoteFloat,
  draw: RemoteDrawScope.() -> Unit,
) {
  val shift = (cycles % 1f) * wavelength
  // Both directions keep the path's left edge in [-wavelength, 0], so it always covers the width.
  val dx = if (reverse) shift - wavelength else -shift
  translate(dx, h * level) { scale(wavelength, h, offset(0f, 0f)) { draw() } }
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
  style = FillStyle
}

/** max(0, v) without leaving the expression: (v + |v|) / 2. */
internal fun clampZero(v: RemoteFloat): RemoteFloat =
  (v + ee.schimke.composeai.rccreation.compose.state.abs(v)) * 0.5f

private class Bubble(val x: Float, val period: Float, val phase: Float, val radius: Float)

private class Glint(val x: Float, val speed: Float, val phase: Float)

// Distinct periods so the bubbles rarely fall into step; each divides 3600 (see [SwimClock]).
private val Bubbles =
  listOf(
    Bubble(x = 0.2f, period = 3f, phase = 0.0f, radius = 0.05f),
    Bubble(x = 0.47f, period = 4f, phase = 0.55f, radius = 0.035f),
    Bubble(x = 0.72f, period = 3.6f, phase = 0.25f, radius = 0.06f),
    Bubble(x = 0.86f, period = 4.8f, phase = 0.8f, radius = 0.04f),
    Bubble(x = 0.33f, period = 4.5f, phase = 0.4f, radius = 0.03f),
    Bubble(x = 0.6f, period = 3.75f, phase = 0.9f, radius = 0.045f),
  )

private val Glints =
  listOf(
    Glint(x = 0.24f, speed = TwoPi / 2.5f, phase = 0f),
    Glint(x = 0.58f, speed = TwoPi / 3.6f, phase = 2.1f),
    Glint(x = 0.8f, speed = TwoPi / 2f, phase = 4.0f),
  )

internal const val TwoPi = 6.2831855f
