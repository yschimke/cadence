@file:Suppress("RestrictedApi", "RestrictedApiAndroidX")

package ee.schimke.cadence.podcasts

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.remote.creation.compose.action.Action
import androidx.compose.remote.creation.compose.layout.*
import androidx.compose.remote.creation.compose.modifier.*
import androidx.compose.remote.creation.compose.shapes.RemoteRoundedCornerShape
import androidx.compose.remote.creation.compose.state.*
import androidx.compose.remote.creation.compose.vector.painterRemoteVector
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import ee.schimke.cadence.theme.CadencePalette

@Composable
@RemoteComposable
fun CloudSyncWidgetContent(status: CloudSyncStatus, onOpen: Action? = null, onRefresh: Action? = null, onDownload: Action? = null, onProcess: Action? = null, widthDp: Int = 250, heightDp: Int = 180) {
  val scale = minOf(widthDp / 250f, heightDp / 180f).coerceIn(0.85f, 1.35f)
  val padding = 12 * scale
  val headerWidth = (widthDp - padding * 2 - 48 - 16 * scale).coerceAtLeast(90f)
  val cellWidth = ((widthDp - padding * 2 - 12 * scale) / 3).coerceAtLeast(48f)
  RemoteColumn(
    modifier = RemoteModifier.fillMaxSize().clip(RemoteRoundedCornerShape(16.rdp)).background(CadencePalette.Grey20.rc).padding((12 * scale).rdp),
    verticalArrangement = RemoteArrangement.SpaceEvenly,
  ) {
    RemoteRow(modifier = RemoteModifier.fillMaxWidth(), horizontalArrangement = RemoteArrangement.spacedBy((8 * scale).rdp), verticalAlignment = RemoteAlignment.CenterVertically) {
      SyncStatusIcon(Icons.Default.Cloud, CadencePalette.Blue80, (20 * scale).toInt())
      RemoteColumn(modifier = RemoteModifier.width((headerWidth - 20 * scale).coerceAtLeast(60f).rdp)) {
        RemoteText("CLOUD SYNC".rs, color = CadencePalette.GreyVariant80.rc, fontSize = (10 * scale).rsp, fontWeight = FontWeight.Medium, maxLines = 1)
        RemoteText(status.source.rs, color = CadencePalette.Grey90.rc, fontSize = (12 * scale).rsp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
      if (status.hasSource) {
        val refresh = onRefresh.takeIf { status.canRefresh }
        RemoteBox(
          modifier = RemoteModifier.size(48.rdp)
            .clip(RemoteRoundedCornerShape(24.rdp))
            .semantics { contentDescription = "Refresh podcasts".rs; role = Role.Button }
            .let { if (refresh != null) it.clickable(refresh) else it },
          contentAlignment = RemoteAlignment.Center,
        ) {
          SyncStatusIcon(Icons.Default.Refresh, if (refresh != null) CadencePalette.Blue80 else CadencePalette.GreyVariant80, (22 * scale).toInt())
        }
      }
    }
    if (status.hasSource && status.downloadNeeded != null && status.processingNeeded != null) {
      RemoteRow(modifier = RemoteModifier.fillMaxWidth(), horizontalArrangement = RemoteArrangement.SpaceEvenly) {
        CloudMetric(status.downloadNeeded, "to download", Icons.Default.Download, scale, cellWidth, onDownload.takeIf { status.canDownload }, "Download ${status.downloadNeeded} episodes")
        CloudMetric(status.processingNeeded, "to process", Icons.Default.GraphicEq, scale, cellWidth, onProcess.takeIf { status.canProcess }, "Process ${status.processingNeeded} episodes")
        status.downloadedCount?.let { CloudMetric(it, "downloaded", Icons.Default.Cloud, scale, cellWidth) }
      }
      RemoteText((status.progress ?: status.checked).rs, color = CadencePalette.GreyVariant80.rc, fontSize = (10 * scale).rsp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    } else if (status.hasSource) {
      RemoteText(status.downloads.rs, color = CadencePalette.Grey90.rc, fontSize = 12.rsp, maxLines = 1)
      RemoteText(status.processing.rs, color = CadencePalette.GreyVariant80.rc, fontSize = 12.rsp, maxLines = 1)
      RemoteText((status.progress ?: status.checked).rs, color = CadencePalette.GreyVariant80.rc, fontSize = 10.rsp, maxLines = 1)
    } else if (status.progress != null) RemoteText(status.progress.rs, color = CadencePalette.Blue80.rc, fontSize = 12.rsp, maxLines = 1)
    RemoteRow(modifier = RemoteModifier.fillMaxWidth(), horizontalArrangement = RemoteArrangement.spacedBy((14 * scale).rdp)) {
      if (!status.hasSource && onOpen != null) CloudAction("Connect", onOpen, scale)
      if (status.hasSource && status.downloadNeeded == null && status.canDownload && onDownload != null) CloudAction("Download", onDownload, scale)
      if (status.hasSource && status.processingNeeded == null && status.canProcess && onProcess != null) CloudAction("Process", onProcess, scale)
    }
  }
}

@Composable @RemoteComposable
private fun CloudMetric(value: Int, label: String, icon: ImageVector, scale: Float, width: Float, action: Action? = null, description: String = "") {
  val color = if (value > 0 && label != "downloaded") CadencePalette.Orange80 else CadencePalette.Aqua80
  RemoteColumn(modifier = RemoteModifier.width(width.rdp).height((60 * scale).coerceAtLeast(48f).rdp)
    .clip(RemoteRoundedCornerShape(12.rdp))
    .let { if (action != null) it.background(CadencePalette.GreyVariant30.rc).semantics { contentDescription = description.rs; role = Role.Button }.clickable(action) else it }
    .padding((4 * scale).rdp), verticalArrangement = RemoteArrangement.Center) {
    RemoteRow(horizontalArrangement = RemoteArrangement.spacedBy(4.rdp), verticalAlignment = RemoteAlignment.CenterVertically) {
      SyncStatusIcon(icon, color, (14 * scale).toInt())
      RemoteText(value.toString().rs, color = color.rc, fontSize = (21 * scale).rsp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
    RemoteText(label.rs, color = CadencePalette.GreyVariant80.rc, fontSize = (10 * scale).rsp, maxLines = 1)
  }
}

@Composable @RemoteComposable
internal fun SyncStatusIcon(icon: ImageVector, color: Color, size: Int) {
  val painter = painterRemoteVector(icon, color.rc)
  RemoteCanvas(RemoteModifier.size(size.rdp)) { with(painter) { draw() } }
}

@Composable @RemoteComposable
private fun CloudAction(label: String, action: Action, scale: Float) {
  RemoteBox(modifier = RemoteModifier.height((48 * scale.coerceAtLeast(1f)).rdp).clickable(action), contentAlignment = RemoteAlignment.Center) {
    RemoteText(label.rs, color = CadencePalette.Blue80.rc, fontSize = (13 * scale).rsp, maxLines = 1)
  }
}
