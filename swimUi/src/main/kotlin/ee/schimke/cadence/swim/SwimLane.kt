@file:Suppress("RestrictedApi", "RestrictedApiAndroidX")

package ee.schimke.cadence.swim

import androidx.compose.remote.creation.compose.layout.RemoteCanvas
import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.remote.creation.compose.layout.RemoteDrawScope
import androidx.compose.remote.creation.compose.layout.RemoteOffset
import androidx.compose.remote.creation.compose.layout.RemoteSize
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.state.RemoteColor
import androidx.compose.remote.creation.compose.state.RemoteFloat
import androidx.compose.remote.creation.compose.state.RemotePaint
import androidx.compose.remote.creation.compose.state.clamp
import androidx.compose.remote.creation.compose.state.copySign
import androidx.compose.remote.creation.compose.state.floor
import androidx.compose.remote.creation.compose.state.min
import androidx.compose.remote.creation.compose.state.rc
import androidx.compose.remote.creation.compose.state.rf
import androidx.compose.remote.creation.compose.state.selectIfLt
import androidx.compose.remote.creation.compose.state.sin
import androidx.compose.remote.creation.compose.state.toRad
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.StrokeCap

/**
 * A linear progress indicator as a pool lane: a small swimmer swims [SwimLap.progress] of the way
 * along it, leaving a wake behind.
 *
 * Odd laps swim left to right, even laps right to left, and the wake changes colour with the lap so
 * the two are told apart at a glance. A lap that has only just begun ([SwimLap.isTurning]) opens
 * with a flip turn at the wall and a streamlined push off, played once when the document loads.
 *
 * [progressPerSecond] lets the player keep swimming without a new capture: pass `1 / duration` of
 * something that is actually progressing (a playing track), or 0 when nothing is moving. Progress
 * stops at the far wall.
 */
@Composable
@RemoteComposable
fun SwimLaneProgress(
  lap: SwimLap,
  modifier: RemoteModifier = RemoteModifier,
  colors: LaneColors = LaneColors.Pool,
  progressPerSecond: Float = 0f,
  clock: RemoteFloat = LocalSwimClock.current,
) {
  val start = LocalSwimClockStart.current
  RemoteCanvas(modifier) {
    // Seconds since capture: the turn plays once, and progress keeps pace from there.
    val elapsed = clock.secondsSince(start)
    val progress =
      if (progressPerSecond > 0f) min(elapsed * progressPerSecond + lap.progress, 1f.rf)
      else lap.progress.rf
    val wake = (if (lap.isOdd) colors.oddWake else colors.evenWake).copy(alpha = WakeAlpha)
    if (lap.isTurning) {
      val glide = glideFraction(elapsed)
      drawLane(
        colors = colors,
        direction = lap.direction.toFloat().rf,
        position = progress * easeOut(glide),
        wake = wake.rc,
        turnClock = elapsed,
        strokeClock = clock,
      )
    } else {
      drawLane(
        colors = colors,
        direction = lap.direction.toFloat().rf,
        position = progress,
        wake = wake.rc,
        turnClock = null,
        strokeClock = clock,
      )
    }
  }
}

/**
 * The swimmer doing endless laps of [lapSeconds] each (a divisor of 3600, see [SwimClock]), with a
 * flip turn at every wall: decoration for something that is ready and waiting, not a measure of
 * progress.
 */
@Composable
@RemoteComposable
fun SwimLaneLaps(
  modifier: RemoteModifier = RemoteModifier,
  colors: LaneColors = LaneColors.Pool,
  lapSeconds: Float = 8f,
  clock: RemoteFloat = LocalSwimClock.current,
) {
  RemoteCanvas(modifier) {
    val motion = LapMotion(clock, lapSeconds)
    drawLane(
      colors = colors,
      direction = motion.direction,
      position = motion.position,
      wake =
        motion.parity
          .isLessThan(0.5f.rf)
          .select(
            colors.oddWake.copy(alpha = WakeAlpha).rc,
            colors.evenWake.copy(alpha = WakeAlpha).rc,
          ),
      turnClock = motion.lapClock,
      strokeClock = clock,
    )
  }
}

/**
 * Open water with no lane: [WaterBackground] with the swimmer at the surface, doing endless laps of
 * the canvas width with a flip turn at each edge when [swimming], or treading water by the left
 * edge when not. [swimmerSize] is the swimmer's band as a fraction of the height.
 */
@Composable
@RemoteComposable
fun SwimmingPool(
  modifier: RemoteModifier = RemoteModifier,
  water: WaterColors = WaterColors.Night,
  swimmer: SwimmerColors = SwimmerColors.Default,
  swimming: Boolean = true,
  level: Float = 0.6f,
  crests: Int = 3,
  bubbles: Int = 5,
  bubbleScale: Float = 0.5f,
  swimmerSize: Float = 0.24f,
  lapSeconds: Float = 9f,
  clock: RemoteFloat = LocalSwimClock.current,
) {
  RemoteCanvas(modifier) {
    val w = width
    val h = height
    drawWater(water, level, crests, bubbles, clock, bubbleScale = bubbleScale, amplitude = 0.05f)
    val band = h * swimmerSize
    clipRect(0f.rf, 0f.rf, w, h) {
      translate(0f.rf, h * level - band * 0.45f) {
        val lane = LaneGeometry(w, band, wall = 0f.rf)
        if (swimming) {
          val motion = LapMotion(clock, lapSeconds)
          drawPushOffBubbles(
            swimmer.splash,
            lane.startX(motion.direction),
            lane.mid,
            band,
            motion.direction,
            motion.lapClock,
          )
          drawSwimmer(
            swimmer,
            lane.swimmerX(motion.direction, motion.position),
            lane.mid,
            lane.unit,
            motion.direction,
            motion.lapClock,
            clock,
          )
        } else {
          // Treading water: bobbing by the wall, unhurried strokes.
          val bob = sin(clock * (TwoPi / 3f)) * band * 0.05f
          drawSwimmer(
            swimmer,
            lane.swimmerX(1f.rf, 0f.rf),
            lane.mid + bob,
            lane.unit,
            1f.rf,
            null,
            clock * 0.3f,
          )
        }
      }
    }
  }
}

/** Endless laps of [lapSeconds]: which way, how far, and seconds since the last wall. */
private class LapMotion(clock: RemoteFloat, lapSeconds: Float) {
  private val laps = clock / lapSeconds
  private val lapIndex = floor(laps)
  /** 0 on odd laps (the first), 1 on even laps. */
  val parity = lapIndex % 2f
  /** +1, -1, +1, -1… */
  val direction = parity * -2f + 1f
  val lapClock = (laps - lapIndex) * lapSeconds
  private val swimStart = TurnSeconds + GlideSeconds
  private val swim = (lapClock - swimStart) / (lapSeconds - swimStart)
  /** The push off carries the first fifth of the lap; steady strokes carry the rest. */
  val position =
    selectIfLt(
      lapClock,
      swimStart.rf,
      easeOut(glideFraction(lapClock)) * 0.2f,
      clamp(swim, 0f, 1f) * 0.8f + 0.2f,
    )
}

/** Where things sit in a lane [w] wide and [h] high with walls [wall] thick. */
private class LaneGeometry(val w: RemoteFloat, val h: RemoteFloat, val wall: RemoteFloat) {
  val mid = h * 0.5f
  /** Swimmer scale: one unit is roughly head plus torso; with arms and legs ~1.4 units. */
  val unit = h * 0.8f
  /** From the swimmer's centre to its fingertips or toes. */
  val reach = unit * 0.7f
  val span = w - wall * 2f - reach * 2f

  fun swimmerX(direction: RemoteFloat, position: RemoteFloat) =
    w * 0.5f + direction * (position - 0.5f) * span

  /** The wall the lap swum in [direction] starts from. */
  fun startX(direction: RemoteFloat) = w * 0.5f - direction * (span * 0.5f + reach)
}

private const val WakeAlpha = 0.8f

/** How long the somersault at the wall takes, in seconds. */
private const val TurnSeconds = 0.7f
/** How long the streamlined glide off the wall takes, in seconds. */
private const val GlideSeconds = 0.9f

private fun glideFraction(turnClock: RemoteFloat) =
  clamp((turnClock - TurnSeconds) / GlideSeconds, 0f, 1f)

private fun easeOut(t: RemoteFloat) = t * (t * -1f + 2f)

/**
 * Lane, ropes, wake and swimmer. [position] runs 0 → 1 from the start wall of the lap swum in
 * [direction] (+1 rightwards). With a [turnClock] the swimmer starts the lap facing the wall it
 * just reached, somersaults, glides off on its back and rolls over before stroking again.
 */
private fun RemoteDrawScope.drawLane(
  colors: LaneColors,
  direction: RemoteFloat,
  position: RemoteFloat,
  wake: RemoteColor,
  turnClock: RemoteFloat?,
  strokeClock: RemoteFloat,
) {
  val w = width
  val h = height
  val wall = h * 0.12f
  val lane = LaneGeometry(w, h, wall)
  val mid = lane.mid
  val swimmerX = lane.swimmerX(direction, position)
  val startX = lane.startX(direction)

  clipRect(0f.rf, 0f.rf, w, h) {
    // Square: the host (or the caller's clip) gives the lane its outer shape.
    drawRect(fill(colors.water), RemoteOffset(0f, 0f), RemoteSize(w, h))
    // Wake: from the start wall to the swimmer's toes.
    val wakeEnd = swimmerX - direction * lane.reach
    val wakeLeft = selectIfLt(startX, wakeEnd, startX, wakeEnd)
    val wakeWidth = (wakeEnd - startX) * direction
    drawRoundRect(
      RemotePaint {
        color = wake
        style = PaintingStyle.Fill
      },
      RemoteOffset(wakeLeft, mid - h * 0.15f),
      RemoteSize(clampZero(wakeWidth), h * 0.3f),
      RemoteOffset(h * 0.15f, h * 0.15f),
    )
    // The black line on the pool floor, with its T a little short of each wall.
    val floor = fill(colors.floorLine.copy(alpha = 0.7f))
    val lineInset = wall + h * 0.35f
    drawRect(
      floor,
      RemoteOffset(lineInset, mid - h * 0.04f),
      RemoteSize(w - lineInset * 2f, h * 0.08f),
    )
    drawRect(floor, RemoteOffset(lineInset, mid - h * 0.18f), RemoteSize(h * 0.08f, h * 0.36f))
    drawRect(
      floor,
      RemoteOffset(w - lineInset - h * 0.08f, mid - h * 0.18f),
      RemoteSize(h * 0.08f, h * 0.36f),
    )
    drawRopes(colors, w, h)
    drawWalls(w, h, wall)
    if (turnClock != null) {
      drawPushOffBubbles(colors.swimmer.splash, startX, mid, h, direction, turnClock)
    }
    drawSwimmer(colors.swimmer, swimmerX, mid, lane.unit, direction, turnClock, strokeClock)
  }
}

/** Alternating lane-rope floats along the top and bottom edges. */
private fun RemoteDrawScope.drawRopes(colors: LaneColors, w: RemoteFloat, h: RemoteFloat) {
  val spacing = h * 0.3f
  val radius = h * 0.075f
  for (edge in listOf(0.09f, 0.91f)) {
    val y = h * edge
    // Two interleaved loops rather than a colour expression per float. Arguments are
    // (from, until, step).
    loop(spacing * 0.5f, w, spacing * 2f) { x ->
      drawCircle(fill(colors.ropeA), radius, RemoteOffset(x, y))
    }
    loop(spacing * 1.5f, w, spacing * 2f) { x ->
      drawCircle(fill(colors.ropeB), radius, RemoteOffset(x, y))
    }
  }
}

private fun RemoteDrawScope.drawWalls(w: RemoteFloat, h: RemoteFloat, wall: RemoteFloat) {
  val tile = fill(Color(0xFFE9F4F7))
  drawRect(tile, RemoteOffset(0f, 0f), RemoteSize(wall, h))
  drawRect(tile, RemoteOffset(w - wall, 0f.rf), RemoteSize(wall, h))
}

/** A burst of bubbles off the wall as the swimmer pushes away from it. */
private fun RemoteDrawScope.drawPushOffBubbles(
  splash: Color,
  wallX: RemoteFloat,
  mid: RemoteFloat,
  h: RemoteFloat,
  direction: RemoteFloat,
  turnClock: RemoteFloat,
) {
  val life = clamp((turnClock - TurnSeconds * 0.6f) / 1.3f, 0f, 1f)
  // 0 before the burst, 1 for its life, 0 again once it has risen away.
  val alive = clamp((turnClock - TurnSeconds * 0.6f) * 20f, 0f, 1f) * (life * -1f + 1f)
  val stroke = RemotePaint {
    color = splash.copy(alpha = 0.85f).rc
    style = PaintingStyle.Stroke
    strokeWidth = h * 0.035f
  }
  for (i in 0 until 4) {
    val drift = direction * h * (0.15f + i * 0.17f) * life
    val rise = h * (0.05f + i * 0.06f) * life
    drawCircle(
      stroke,
      h * (0.05f + (i % 2) * 0.025f) * alive,
      RemoteOffset(wallX + drift, mid + h * (i % 2 * 0.2f - 0.1f) - rise),
    )
  }
}

/**
 * The swimmer centred on ([x], [y]) facing [direction]. During a turn it starts facing the other
 * way, somersaults half a turn (head first, down into the water) and rolls back upright as it
 * glides; rotate(180°) then a vertical flip is the same as facing the new direction, so the pose
 * lands exactly on the steady swimming one.
 */
private fun RemoteDrawScope.drawSwimmer(
  colors: SwimmerColors,
  x: RemoteFloat,
  y: RemoteFloat,
  unit: RemoteFloat,
  direction: RemoteFloat,
  turnClock: RemoteFloat?,
  strokeClock: RemoteFloat,
) {
  val armBase = (strokeClock * 400f) % 360f
  val kickBase = sin(strokeClock * (TwoPi / 0.4f)) * 0.07f
  if (turnClock == null) {
    translate(x, y) {
      scale(unit * direction, unit, RemoteOffset(0f, 0f)) {
        drawSwimmerFigure(colors, armBase, kickBase)
      }
    }
    return
  }
  val tumble = clamp(turnClock / TurnSeconds, 0f, 1f) * 180f
  val glide = glideFraction(turnClock)
  // 1 → -1, snapping through the middle rather than squashing flat.
  val rollLinear = clamp(glide / 0.7f, 0f, 1f) * -2f + 1f
  val roll = rollLinear * 0.6f + copySign(0.4f, rollLinear)
  // Streamlined (arms forward, legs together) until the glide is mostly done.
  val strokes = clamp((turnClock - TurnSeconds - GlideSeconds * 0.6f) / 0.3f, 0f, 1f)
  val tumbleSin = sin(toRad(tumble))
  // Duck under while somersaulting, tucked into a ball.
  val dip = tumbleSin * unit * 0.12f
  val tuck = tumbleSin * -0.35f + 1f
  translate(x, y + dip) {
    scale(unit * direction * -1f, unit, RemoteOffset(0f, 0f)) {
      rotate(tumble, RemoteOffset(0f, 0f)) {
        scale(tuck, roll, RemoteOffset(0f, 0f)) {
          drawSwimmerFigure(colors, armBase * strokes, kickBase * strokes)
        }
      }
    }
  }
}

/**
 * Side view, facing +x, centred on the torso, in swimmer units. [armAngle] windmills the arms in
 * degrees (0 = reaching forward); [kick] is the flutter kick's vertical swing.
 */
private fun RemoteDrawScope.drawSwimmerFigure(
  colors: SwimmerColors,
  armAngle: RemoteFloat,
  kick: RemoteFloat,
) {
  val limb = { color: Color, width: Float ->
    RemotePaint {
      this.color = color.rc
      style = PaintingStyle.Stroke
      strokeWidth = width.rf
      strokeCap = StrokeCap.Round
    }
  }
  val shoulder = RemoteOffset(0.16f, -0.02f)
  val farSkin = colors.skin.copy(red = colors.skin.red * 0.85f, green = colors.skin.green * 0.8f)
  // Far arm and leg first, so the near ones overlap them.
  rotate(armAngle + 180f, shoulder) {
    drawLine(limb(farSkin, 0.11f), shoulder, RemoteOffset(0.5f, -0.02f))
  }
  drawLine(limb(farSkin, 0.12f), RemoteOffset(-0.28f, 0f), RemoteOffset(-0.6f.rf, kick * -1f))
  drawLine(limb(colors.skin, 0.12f), RemoteOffset(-0.28f, 0f), RemoteOffset(-0.6f.rf, kick))
  drawRoundRect(
    fill(colors.suit),
    RemoteOffset(-0.36f, -0.14f),
    RemoteSize(0.58f.rf, 0.28f.rf),
    RemoteOffset(0.14f, 0.14f),
  )
  // Head, cap (top half) and goggles.
  drawCircle(fill(colors.skin), 0.16f.rf, RemoteOffset(0.34f, -0.04f))
  drawArc(
    fill(colors.cap),
    180f.rf,
    180f.rf,
    true,
    RemoteOffset(0.17f, -0.215f),
    RemoteSize(0.34f.rf, 0.34f.rf),
  )
  drawCircle(fill(colors.goggles), 0.055f.rf, RemoteOffset(0.45f, -0.05f))
  drawCircle(fill(Color.White.copy(alpha = 0.8f)), 0.018f.rf, RemoteOffset(0.465f, -0.068f))
  rotate(armAngle, shoulder) {
    drawLine(limb(colors.skin, 0.11f), shoulder, RemoteOffset(0.5f, -0.02f))
  }
  // Flutter-kick splashes off the feet.
  val splash = clampZero(kick) * 0.6f + 0.02f
  drawCircle(fill(colors.splash.copy(alpha = 0.8f)), splash, RemoteOffset(-0.66f.rf, kick * -1f))
  drawCircle(fill(colors.splash.copy(alpha = 0.6f)), splash * 0.7f, RemoteOffset(-0.74f, -0.1f))
}
