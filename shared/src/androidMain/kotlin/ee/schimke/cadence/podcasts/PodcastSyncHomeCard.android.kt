package ee.schimke.cadence.podcasts

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ee.schimke.composeai.rcwidget.creation.RcWidgetCaptureSize
import ee.schimke.composeai.rcwidget.launcher.RcAdaptiveRemoteDocumentPlayer
import ee.schimke.composeai.rcwidget.launcher.captureLauncherWidget
import kotlinx.coroutines.CancellationException

@Composable
actual fun PodcastSyncHomeCard(copied: String, available: String, battery: String?, ready: Boolean, showSwimming: Boolean) {
  val context = LocalContext.current
  val height = 152
  BoxWithConstraints(Modifier.fillMaxWidth().height(height.dp)) {
    val width = maxWidth.value.toInt().coerceAtLeast(1)
    val bytes by produceState<ByteArray?>(null, width, height, copied, available, battery, ready, showSwimming) {
      value = try {
        captureLauncherWidget(context, RcWidgetCaptureSize(width, height)) {
          PodcastSyncWidgetContent(ready, copied, available, battery, showSwimming, height, widthDp = width)
        }.bytes
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (_: Exception) {
        null
      }
    }
    val document = bytes
    if (document == null) PodcastSyncHomeFallback(copied, available, battery)
    else RcAdaptiveRemoteDocumentPlayer(document, Modifier.fillMaxWidth().height(height.dp))
  }
}
