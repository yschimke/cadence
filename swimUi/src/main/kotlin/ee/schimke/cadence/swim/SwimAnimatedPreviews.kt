@file:Suppress("RestrictedApi", "RestrictedApiAndroidX")

package ee.schimke.cadence.swim

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.remote.creation.compose.layout.RemoteAlignment
import androidx.compose.remote.creation.compose.layout.RemoteBox
import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.remote.creation.compose.layout.RemoteText
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.background
import androidx.compose.remote.creation.compose.modifier.clip
import androidx.compose.remote.creation.compose.modifier.fillMaxSize
import androidx.compose.remote.creation.compose.shapes.RemoteRoundedCornerShape
import androidx.compose.remote.creation.compose.state.rc
import androidx.compose.remote.creation.compose.state.rdp
import androidx.compose.remote.creation.compose.state.rf
import androidx.compose.remote.creation.compose.state.rs
import androidx.compose.remote.creation.compose.state.rsp
import androidx.compose.remote.creation.profile.Profile
import androidx.compose.remote.creation.profile.RcPlatformProfiles
import androidx.compose.remote.tooling.preview.RemoteContentPreview
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ee.schimke.composeai.preview.AnimatedPreview

/**
 * Seconds since this preview started, from the Compose frame clock.
 *
 * The Remote Compose preview player never advances its own animation time, so an animated preview
 * re-captures the document every frame (`key(seconds)`) with [LocalSwimClock] pinned to this value
 * — see [SwimClockAt].
 */
@Composable
fun rememberPreviewSeconds(): Float {
  var seconds by remember { mutableFloatStateOf(0f) }
  LaunchedEffect(Unit) {
    val start = withFrameNanos { it }
    while (true) withFrameNanos { seconds = (it - start) / 1_000_000_000f }
  }
  return seconds
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

@Preview(name = "Water button · animated", widthDp = 200, heightDp = 64)
@AnimatedPreview(durationMs = 3000, frameIntervalMs = 80, showCurves = false)
@Composable
fun WaterButtonAnimatedPreview() {
  AnimatedSlot(RcPlatformProfiles.WIDGETS_V7, 200.dp, 64.dp) {
    RemoteBox(
      RemoteModifier.fillMaxSize().background(Color(0xFF0E0E11).rc),
      contentAlignment = RemoteAlignment.Center,
    ) {
      RemoteBox(
        RemoteModifier.fillMaxSize().clip(RemoteRoundedCornerShape(32.rdp)),
        contentAlignment = RemoteAlignment.Center,
      ) {
        WaterBackground(RemoteModifier.fillMaxSize(), crests = 3)
        RemoteText(
          "Play".rs,
          color = Color(0xFF06243D).rc,
          fontSize = 18.rsp,
          fontWeight = FontWeight.SemiBold,
        )
      }
    }
  }
}

@Preview(name = "Water round button · animated", widthDp = 96, heightDp = 96)
@AnimatedPreview(durationMs = 3000, frameIntervalMs = 80, showCurves = false)
@Composable
fun WaterRoundButtonAnimatedPreview() {
  AnimatedSlot(RcPlatformProfiles.WEAR_WIDGETS, 96.dp, 96.dp) {
    WaterBackground(
      RemoteModifier.fillMaxSize().clip(RemoteRoundedCornerShape(48.rdp)),
      colors = WaterColors.Night,
    )
  }
}

/** Lap 2 finished at the left wall: lap 3 flip-turns, pushes off and swims right. */
@Preview(name = "Swim lane · flip turn · animated", widthDp = 280, heightDp = 40)
@AnimatedPreview(durationMs = 4000, frameIntervalMs = 80, showCurves = false)
@Composable
fun SwimLaneFlipTurnAnimatedPreview() {
  AnimatedSlot(RcPlatformProfiles.WIDGETS_V7, 280.dp, 40.dp) {
    SwimLaneProgress(
      SwimLap(3, 0.01f),
      RemoteModifier.fillMaxSize().clip(RemoteRoundedCornerShape(12.rdp)),
      progressPerSecond = 0.15f,
    )
  }
}

/** Out and back: odd laps swim right, even laps left, with a flip turn at each wall. */
@Preview(name = "Swim lane · laps · animated", widthDp = 280, heightDp = 40)
@AnimatedPreview(durationMs = 10000, frameIntervalMs = 100, showCurves = false)
@Composable
fun SwimLaneLapsAnimatedPreview() {
  AnimatedSlot(RcPlatformProfiles.WEAR_WIDGETS, 280.dp, 40.dp) {
    SwimLaneLaps(
      RemoteModifier.fillMaxSize().clip(RemoteRoundedCornerShape(12.rdp)),
      colors = LaneColors.Night,
      lapSeconds = 5f,
    )
  }
}

@Preview(name = "Swimming pool · animated", widthDp = 280, heightDp = 130)
@AnimatedPreview(durationMs = 10000, frameIntervalMs = 100, showCurves = false)
@Composable
fun SwimmingPoolAnimatedPreview() {
  AnimatedSlot(RcPlatformProfiles.WIDGETS_V6, 280.dp, 130.dp) {
    RemoteBox(
      RemoteModifier.fillMaxSize()
        .clip(RemoteRoundedCornerShape(16.rdp))
        .background(Color(0xFF1B2024).rc)
    ) {
      SwimmingPool(RemoteModifier.fillMaxSize(), lapSeconds = 5f)
    }
  }
}

@Composable
private fun AnimatedSlot(
  profile: Profile,
  width: Dp,
  height: Dp,
  content: @Composable @RemoteComposable () -> Unit,
) {
  val seconds = rememberPreviewSeconds()
  Column(
    Modifier.background(Color(0xFF0E0E11)).padding(0.dp),
    verticalArrangement = Arrangement.Top,
  ) {
    Box(Modifier.size(width, height)) {
      key(seconds) {
        RemoteContentPreview(Modifier.size(width, height), profile) {
          SwimClockAt(seconds, content)
        }
      }
    }
  }
}
