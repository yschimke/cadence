package ee.schimke.cadence.podcasts

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ee.schimke.cadence.widgets.WidgetColors
import ee.schimke.composeai.rcwidget.creation.RcWidgetCaptureSize
import ee.schimke.composeai.rcwidget.creation.rcWidgetHostAction
import ee.schimke.composeai.rcwidget.launcher.RcAdaptiveRemoteDocumentPlayer
import ee.schimke.composeai.rcwidget.launcher.RcLauncherWidgetCapture
import ee.schimke.composeai.rcwidget.launcher.captureLauncherWidget
import kotlinx.coroutines.CancellationException

@Composable
actual fun CloudSyncHomeCard(
  status: CloudSyncStatus,
  onOpen: () -> Unit,
  onRefresh: () -> Unit,
  onDownload: () -> Unit,
  onProcess: () -> Unit,
) {
  val context = LocalContext.current
  val colors = WidgetColors.from(MaterialTheme.colorScheme)
  BoxWithConstraints(Modifier.fillMaxWidth().height(192.dp)) {
    val width = maxWidth.value.toInt().coerceAtLeast(1)
    val capture by
      produceState<RcLauncherWidgetCapture?>(null, width, status, colors) {
        value =
          try {
            captureLauncherWidget(context, RcWidgetCaptureSize(width, 192)) {
              CloudSyncWidgetContent(
                status,
                onOpen = rcWidgetHostAction(name = "open", payload = "open"),
                onRefresh = rcWidgetHostAction(name = "refresh", payload = "refresh"),
                onDownload = rcWidgetHostAction(name = "download", payload = "download"),
                onProcess = rcWidgetHostAction(name = "process", payload = "process"),
                widthDp = width,
                heightDp = 192,
                colors = colors,
              )
            }
          } catch (cancelled: CancellationException) {
            throw cancelled
          } catch (_: Exception) {
            null
          }
      }
    val doc = capture
    if (doc == null) CloudSyncHomeFallback(status, onOpen, onRefresh, onDownload, onProcess)
    else
      RcAdaptiveRemoteDocumentPlayer(
        doc.bytes,
        Modifier.fillMaxWidth().height(192.dp),
        onNamedAction = { name, metadata ->
          val action =
            (metadata as? String)?.substringBefore("\n--RC-METADATA-V1--\n")
              ?: name.toIntOrNull()?.let { doc.actions[it] }
              ?: name
          when (action) {
            "open" -> onOpen()
            "refresh" -> onRefresh()
            "download" -> onDownload()
            "process" -> onProcess()
          }
        },
      )
  }
}
