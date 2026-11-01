@file:Suppress("RestrictedApi", "RestrictedApiAndroidX")

package ee.schimke.cadence.podcasts

import androidx.compose.remote.creation.compose.action.Action
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Sync
import androidx.compose.remote.creation.compose.layout.RemoteAlignment
import androidx.compose.remote.creation.compose.layout.RemoteArrangement
import androidx.compose.remote.creation.compose.layout.RemoteBox
import androidx.compose.remote.creation.compose.layout.RemoteColumn
import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.remote.creation.compose.layout.RemoteRow
import androidx.compose.remote.creation.compose.layout.RemoteText
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.background
import androidx.compose.remote.creation.compose.modifier.clickable
import androidx.compose.remote.creation.compose.modifier.clip
import androidx.compose.remote.creation.compose.modifier.fillMaxSize
import androidx.compose.remote.creation.compose.modifier.fillMaxWidth
import androidx.compose.remote.creation.compose.modifier.height
import androidx.compose.remote.creation.compose.modifier.padding
import androidx.compose.remote.creation.compose.modifier.size
import androidx.compose.remote.creation.compose.shapes.RemoteCircleShape
import androidx.compose.remote.creation.compose.shapes.RemoteRoundedCornerShape
import androidx.compose.remote.creation.compose.state.rc
import androidx.compose.remote.creation.compose.state.rdp
import androidx.compose.remote.creation.compose.state.rs
import androidx.compose.remote.creation.compose.state.rsp
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import ee.schimke.cadence.swim.SwimmingPool
import ee.schimke.cadence.swim.WaterColors
import ee.schimke.cadence.theme.CadencePalette

/**
 * The podcast sync widget as Remote Compose: whether the audio is ready to copy to the headphones,
 * and what preparation last reported. Drawn by the launcher itself (Android 16+), so it fills and
 * reflows with whatever slot the user resizes it to; [onTap] opens Cadence.
 *
 * The card is a pool at dusk: waves and bubbles drift along the bottom, where a swimmer does laps
 * (with a flip turn at each edge) once the audio is ready, or treads water by the wall until it is.
 * All animation runs in the launcher's player; the widget is only re-captured when the status
 * changes.
 */
@Composable
@RemoteComposable
fun PodcastSyncWidgetContent(
  ready: Boolean,
  copied: String,
  available: String,
  battery: String?,
  showSwimming: Boolean,
  heightDp: Int,
  onTap: Action? = null,
  widthDp: Int = 232,
) {
  val lane = heightDp >= 140
  val scale = minOf(widthDp / 232f, heightDp / if (lane) 152f else 72f).coerceIn(0.8f, 1.35f)
  val poolHeight = (heightDp * 0.28f).toInt().coerceIn(40, 72)
  val headerHeight = heightDp - poolHeight
  RemoteBox(
    modifier =
      RemoteModifier.fillMaxSize()
        .let { if (onTap != null) it.clickable(onTap) else it }
        .clip(RemoteRoundedCornerShape(16.rdp))
        .background(CadencePalette.Grey20.rc)
  ) {
    RemoteColumn(modifier = RemoteModifier.fillMaxSize(), verticalArrangement = if (lane) RemoteArrangement.Top else RemoteArrangement.Center) {
      RemoteColumn(
        modifier = (if (lane) RemoteModifier.fillMaxWidth().height(headerHeight.rdp) else RemoteModifier.fillMaxWidth()).padding((8 * scale).rdp),
        verticalArrangement = RemoteArrangement.spacedBy((1 * scale).rdp),
      ) {
        SyncMetricLine(copied, Icons.Default.Headphones, "on headphones", CadencePalette.Blue80, scale)
        SyncMetricLine(available, Icons.Default.Sync, if (ready) "ready to sync" else "to prepare", if (ready) CadencePalette.Aqua80 else CadencePalette.Orange80, scale)
        RemoteRow(horizontalArrangement = RemoteArrangement.spacedBy(6.rdp), verticalAlignment = RemoteAlignment.CenterVertically) {
          SyncStatusIcon(Icons.Default.BatteryFull, CadencePalette.GreyVariant80, (14 * scale).toInt())
          RemoteText((battery ?: "Battery unavailable").rs, color = CadencePalette.GreyVariant80.rc, fontSize = (11 * scale).rsp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }
      if (lane) SwimmingPool(
        RemoteModifier.fillMaxWidth().height((heightDp - headerHeight).rdp),
        water = WaterColors.Night,
        swimming = showSwimming && ready,
        level = 0.35f,
        bubbles = if (ready) 6 else 3,
        amplitude = 0.025f,
        swimmerSize = 0.48f,
        standing = !showSwimming,
      )
    }
  }
}

@Composable
@RemoteComposable
private fun SyncMetricLine(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: androidx.compose.ui.graphics.Color, scale: Float) {
  val detail = text.substringAfter(": ", "")
  val count = detail.substringBefore(" eps").toIntOrNull()
  RemoteRow(horizontalArrangement = RemoteArrangement.spacedBy(6.rdp), verticalAlignment = RemoteAlignment.CenterVertically) {
    SyncStatusIcon(icon, color, (14 * scale).toInt())
    if (count != null) {
      RemoteText(count.toString().rs, color = color.rc, fontSize = (17 * scale).rsp, fontWeight = FontWeight.Medium, maxLines = 1)
      val remainder = detail.substringAfter(" eps", "").replace(" marked", " played")
      RemoteText("$label$remainder".rs, color = CadencePalette.Grey90.rc, fontSize = (11 * scale).rsp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    } else {
      RemoteText(text.rs, color = CadencePalette.Grey90.rc, fontSize = (11 * scale).rsp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
  }
}
