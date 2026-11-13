package ee.schimke.cadence.home

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import ee.schimke.cadence.bluetooth.BluetoothState
import ee.schimke.cadence.bluetooth.ShokzPlayer
import ee.schimke.cadence.metro.AppGraphProvider
import ee.schimke.cadence.widgets.HeadphoneControlsWidgetContent
import ee.schimke.cadence.widgets.HeadphoneWidgetAction
import ee.schimke.cadence.widgets.HeadphoneWidgetLayout
import ee.schimke.cadence.widgets.HeadphoneWidgetState
import ee.schimke.cadence.widgets.WidgetColors
import ee.schimke.composeai.rccreation.bridge.ExperimentalRcCreationApi
import ee.schimke.composeai.rccreation.compose.capture.RemoteCreationDisplayInfo
import ee.schimke.composeai.rcplayer.embedded.RcEmbeddedPlayer
import ee.schimke.composeai.rcplayer.runtime.RcPlayerEvent
import ee.schimke.composeai.rcwidget.creation.rcWidgetHostAction

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalRcCreationApi::class)
@Composable
actual fun HomeMiniPlayer(
  state: BluetoothState,
  onPrevious: () -> Unit,
  onPlayPause: () -> Unit,
  onNext: () -> Unit,
) {
  val context = LocalContext.current
  val shokz =
    remember(context) {
      (context.applicationContext as? AppGraphProvider)
        ?.appGraph
        ?.bluetoothController
        ?.let(::ShokzPlayer)
    }
  DisposableEffect(shokz) { onDispose { shokz?.release() } }
  val colors = WidgetColors.from(MaterialTheme.colorScheme)
  val widgetState =
    HeadphoneWidgetState.from(state).let {
      if (state.connectedDevice != null) it
      else
        it.copy(
          title = "Playback unavailable over USB",
          shortTitle = "Playback unavailable",
          transport = emptyList(),
          modeSwitch = null,
        )
    }
  BoxWithConstraints(Modifier.fillMaxWidth().height(80.dp)) {
    val width = maxWidth.value.toInt().coerceAtLeast(1)
    val densityDpi = LocalConfiguration.current.densityDpi
    val display =
      remember(width, densityDpi) {
        RemoteCreationDisplayInfo(
          (width * densityDpi / 160f).toInt().coerceAtLeast(1),
          (80 * densityDpi / 160f).toInt().coerceAtLeast(1),
          densityDpi,
        )
      }
    // What the card draws: a change to any of it composes the widget's content again.
    val shown = Triple(widgetState, colors, width)
    // A card that can't be drawn falls back to the plain controls until what it shows changes.
    var failedAt by remember { mutableStateOf<Any?>(null) }
    if (failedAt == shown) {
      HomeMiniPlayerFallback(state, onPrevious, onPlayPause, onNext)
    } else {
      // The widget's own drawing, composed and played in the app by rc-players' Compose player:
      // no capture, and no launcher profile, which only the launcher's player needs.
      RcEmbeddedPlayer(
        display = display,
        modifier = Modifier.fillMaxWidth().height(80.dp),
        onEvent = { event ->
          val wire = (event as? RcPlayerEvent.HostNamedAction)?.name
          when (wire?.let(HeadphoneWidgetAction::fromWire)) {
            HeadphoneWidgetAction.Previous ->
              if (state.localAudioControls && shokz != null) shokz.previousTrack() else onPrevious()
            HeadphoneWidgetAction.Play ->
              if (state.localAudioControls && shokz != null) shokz.play() else onPlayPause()
            HeadphoneWidgetAction.Pause ->
              if (state.localAudioControls && shokz != null) shokz.pause() else onPlayPause()
            HeadphoneWidgetAction.Next ->
              if (state.localAudioControls && shokz != null) shokz.nextTrack() else onNext()
            else -> Unit
          }
        },
        onFailure = { failedAt = shown },
        key = shown,
      ) {
        HeadphoneControlsWidgetContent(
          state = widgetState,
          layout = HeadphoneWidgetLayout.of(widgetState, width, 80),
          colors = colors,
          onOpen = null,
          onAction = { action ->
            rcWidgetHostAction(name = action.wireName, payload = action.wireName)
          },
        )
      }
    }
  }
}
