@file:Suppress("RestrictedApi", "RestrictedApiAndroidX")

package ee.schimke.cadence.swim

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.remote.creation.compose.layout.RemoteAlignment
import androidx.compose.remote.creation.compose.layout.RemoteBox
import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.remote.creation.compose.layout.RemoteText
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.background
import androidx.compose.remote.creation.compose.modifier.clip
import androidx.compose.remote.creation.compose.modifier.fillMaxSize
import androidx.compose.remote.creation.compose.shapes.RemoteRoundedCornerShape
import androidx.compose.remote.creation.compose.state.RemoteFloat
import androidx.compose.remote.creation.compose.state.rc
import androidx.compose.remote.creation.compose.state.rdp
import androidx.compose.remote.creation.compose.state.rf
import androidx.compose.remote.creation.compose.state.rs
import androidx.compose.remote.creation.compose.state.rsp
import androidx.compose.remote.creation.profile.Profile
import androidx.compose.remote.creation.profile.RcPlatformProfiles
import androidx.compose.remote.tooling.preview.RemoteContentPreview
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Previews play each document under the profile its host uses: launcher widgets (WIDGETS_V7) and
 * Wear widgets (WEAR_WIDGETS). A preview is a single frame, so the animated pieces take a fixed
 * clock here; the strips show one animation at several moments.
 */

@Preview(name = "Water buttons · launcher", widthDp = 300, heightDp = 236, showBackground = true)
@Composable
fun WaterButtonsLauncherPreview() {
  WaterButtons(RcPlatformProfiles.WIDGETS_V7)
}

@Preview(name = "Water buttons · Wear", widthDp = 300, heightDp = 236, showBackground = true)
@Composable
fun WaterButtonsWearPreview() {
  WaterButtons(RcPlatformProfiles.WEAR_WIDGETS)
}

@Composable
private fun WaterButtons(profile: Profile) {
  Column(
    Modifier.background(Color(0xFF0E0E11)).padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    val palettes = listOf("Play" to WaterColors.Pool, "Sync" to WaterColors.Lagoon)
    palettes.forEachIndexed { index, (label, colors) ->
      Slot(profile, 276.dp, 52.dp) { WaterPill(label, colors, clock = (1.3f + index * 0.9f).rf) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      listOf(0f, 0.8f, 1.6f).forEach { t ->
        Slot(profile, 84.dp, 84.dp) {
          WaterBackground(
            RemoteModifier.fillMaxSize().clip(RemoteRoundedCornerShape(42.rdp)),
            colors = WaterColors.Night,
            clock = (t + 0.4f).rf,
          )
        }
      }
    }
  }
}

@Composable
@RemoteComposable
private fun WaterPill(label: String, colors: WaterColors, clock: RemoteFloat) {
  RemoteBox(
    RemoteModifier.fillMaxSize().clip(RemoteRoundedCornerShape(26.rdp)),
    contentAlignment = RemoteAlignment.Center,
  ) {
    WaterBackground(RemoteModifier.fillMaxSize(), colors = colors, crests = 4, clock = clock)
    RemoteText(
      label.rs,
      color = Color(0xFF06243D).rc,
      fontSize = 18.rsp,
      fontWeight = FontWeight.SemiBold,
    )
  }
}

@Preview(name = "Swim lane · odd and even laps", widthDp = 300, heightDp = 200)
@Composable
fun SwimLaneLapsPreview() {
  Column(
    Modifier.background(Color(0xFF0E0E11)).padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    listOf(SwimLap(1, 0.15f), SwimLap(1, 0.62f), SwimLap(2, 0.35f), SwimLap(2, 0.9f)).forEach {
      Slot(RcPlatformProfiles.WIDGETS_V7, 276.dp, 36.dp) {
        SwimLaneProgress(it, RemoteModifier.fillMaxSize(), clock = 0.4f.rf)
      }
    }
  }
}

/** The flip turn starting lap 3, one row per moment of the turn and push off. */
@Preview(name = "Swim lane · flip turn", widthDp = 300, heightDp = 330)
@Composable
fun SwimLaneFlipTurnPreview() {
  FlipTurnStrip(RcPlatformProfiles.WEAR_WIDGETS, LaneColors.Night)
}

@Preview(name = "Swim lane · flip turn · launcher", widthDp = 300, heightDp = 330)
@Composable
fun SwimLaneFlipTurnLauncherPreview() {
  FlipTurnStrip(RcPlatformProfiles.WIDGETS_V7, LaneColors.Pool)
}

@Composable
private fun FlipTurnStrip(profile: Profile, colors: LaneColors) {
  Column(
    Modifier.background(Color(0xFF0E0E11)).padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    // Lap 2 just finished at the left wall; lap 3 swims right again, 30% in.
    listOf(0f, 0.25f, 0.5f, 0.7f, 1.0f, 1.3f, 1.6f, 2.4f).forEach { t ->
      Slot(profile, 276.dp, 32.dp) {
        SwimLaneProgress(
          SwimLap(3, 0.02f),
          RemoteModifier.fillMaxSize(),
          colors = colors,
          progressPerSecond = 0.12f,
          clock = t.rf,
        )
      }
    }
  }
}

@Preview(name = "Swim lane · endless laps", widthDp = 300, heightDp = 200)
@Composable
fun SwimLaneEndlessPreview() {
  Column(
    Modifier.background(Color(0xFF0E0E11)).padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    listOf(0.3f, 3.5f, 7.4f, 11f).forEach { t ->
      Slot(RcPlatformProfiles.WEAR_WIDGETS, 276.dp, 36.dp) {
        SwimLaneLaps(RemoteModifier.fillMaxSize(), clock = t.rf)
      }
    }
  }
}

/** The launcher card's pool: laps when ready, treading water by the wall while waiting. */
@Preview(name = "Swimming pool", widthDp = 300, heightDp = 300)
@Composable
fun SwimmingPoolPreview() {
  Column(
    Modifier.background(Color(0xFF0E0E11)).padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    listOf(true to 3.2f, false to 0.6f).forEach { (swimming, t) ->
      // V6: the strictest launcher profile (Android 16).
      Slot(RcPlatformProfiles.WIDGETS_V6, 276.dp, 130.dp) {
        RemoteBox(
          RemoteModifier.fillMaxSize()
            .clip(RemoteRoundedCornerShape(16.rdp))
            .background(Color(0xFF1B2024).rc)
        ) {
          SwimmingPool(RemoteModifier.fillMaxSize(), swimming = swimming, clock = t.rf)
        }
      }
    }
  }
}

@Composable
private fun Slot(
  profile: Profile,
  width: Dp,
  height: Dp,
  content: @Composable @RemoteComposable () -> Unit,
) {
  Box(Modifier.width(width).height(height)) {
    RemoteContentPreview(Modifier.fillMaxWidth().size(width, height), profile, content)
  }
}
