package ee.schimke.cadence.home

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ee.schimke.cadence.bluetooth.BluetoothState
import ee.schimke.cadence.bluetooth.ShokzPlayer
import ee.schimke.cadence.metro.AppGraphProvider
import androidx.media3.common.util.UnstableApi
import ee.schimke.cadence.widgets.HeadphoneControlsWidgetContent
import ee.schimke.cadence.widgets.HeadphoneWidgetAction
import ee.schimke.cadence.widgets.HeadphoneWidgetLayout
import ee.schimke.cadence.widgets.HeadphoneWidgetState
import ee.schimke.cadence.widgets.WidgetColors
import ee.schimke.composeai.rcwidget.creation.RcWidgetCaptureSize
import ee.schimke.composeai.rcwidget.creation.rcWidgetHostAction
import ee.schimke.composeai.rcwidget.launcher.RcAdaptiveRemoteDocumentPlayer
import ee.schimke.composeai.rcwidget.launcher.RcLauncherWidgetCapture
import ee.schimke.composeai.rcwidget.launcher.captureLauncherWidget
import kotlinx.coroutines.CancellationException

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
actual fun HomeMiniPlayer(state: BluetoothState, onPrevious: () -> Unit, onPlayPause: () -> Unit, onNext: () -> Unit) {
  val context = LocalContext.current
  val shokz = remember(context) { (context.applicationContext as? AppGraphProvider)?.appGraph?.bluetoothController?.let(::ShokzPlayer) }
  DisposableEffect(shokz) { onDispose { shokz?.release() } }
  val colors = WidgetColors.from(MaterialTheme.colorScheme)
  val widgetState = HeadphoneWidgetState.from(state).let {
    if (state.connectedDevice != null) it else it.copy(title = "Playback unavailable over USB", shortTitle = "Playback unavailable", transport = emptyList(), modeSwitch = null)
  }
  BoxWithConstraints(Modifier.fillMaxWidth().height(80.dp)) {
    val width = maxWidth.value.toInt().coerceAtLeast(1)
    val capture by produceState<RcLauncherWidgetCapture?>(null, width, widgetState, colors) {
      value = try {
        captureLauncherWidget(context, RcWidgetCaptureSize(width, 80)) {
          HeadphoneControlsWidgetContent(
            state = widgetState,
            layout = HeadphoneWidgetLayout.of(widgetState, width, 80),
            colors = colors,
            onOpen = null,
            onAction = { action -> rcWidgetHostAction(name = action.wireName, payload = action.wireName) },
          )
        }
      } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { null }
    }
    val document = capture
    if (document == null) HomeMiniPlayerFallback(state, onPrevious, onPlayPause, onNext)
    else RcAdaptiveRemoteDocumentPlayer(document.bytes, Modifier.fillMaxWidth().height(80.dp), onNamedAction = { name, metadata ->
      val payload = (metadata as? String)?.substringBefore("\n--RC-METADATA-V1--\n")
        ?: name.toIntOrNull()?.let { document.actions[it] } ?: name
      when (HeadphoneWidgetAction.fromWire(payload)) {
        HeadphoneWidgetAction.Previous -> if (state.localAudioControls && shokz != null) shokz.previousTrack() else onPrevious()
        HeadphoneWidgetAction.Play -> if (state.localAudioControls && shokz != null) shokz.play() else onPlayPause()
        HeadphoneWidgetAction.Pause -> if (state.localAudioControls && shokz != null) shokz.pause() else onPlayPause()
        HeadphoneWidgetAction.Next -> if (state.localAudioControls && shokz != null) shokz.nextTrack() else onNext()
        else -> Unit
      }
    })
  }
}
