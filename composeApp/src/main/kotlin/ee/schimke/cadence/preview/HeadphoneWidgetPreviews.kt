@file:Suppress("RestrictedApi", "RestrictedApiAndroidX")

package ee.schimke.cadence.preview

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.remote.tooling.preview.RemoteContentPreview
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import ee.schimke.cadence.bluetooth.BluetoothState
import ee.schimke.cadence.swim.SwimClockAt
import ee.schimke.cadence.swim.rememberPreviewSeconds
import ee.schimke.cadence.widgets.HeadphoneControlsWidgetContent
import ee.schimke.cadence.widgets.HeadphoneWidgetLayout
import ee.schimke.cadence.widgets.HeadphoneWidgetState
import ee.schimke.cadence.widgets.WidgetColors
import ee.schimke.composeai.preview.AnimatedPreview
import ee.schimke.composeai.preview.LauncherWidgetPreview
import ee.schimke.composeai.rcwidget.launcher.RcLauncherWidgets

/**
 * The widget at whatever cell footprint the launcher annotation gives it: the layout tier is chosen
 * from the measured size, exactly as [ee.schimke.cadence.widgets.HeadphoneControlsWidget] chooses
 * it from the real slot.
 */
@Composable
private fun HeadphoneWidgetPreview(
  bluetooth: BluetoothState,
  colors: WidgetColors = WidgetColors.Dark,
  animated: Boolean = false,
) {
  // Animated previews re-capture every frame with the water's clock pinned to it.
  val seconds = if (animated) rememberPreviewSeconds() else 0f
  BoxWithConstraints(Modifier.fillMaxSize()) {
    val state = HeadphoneWidgetState.from(bluetooth)
    val layout = HeadphoneWidgetLayout.of(state, maxWidth.value.toInt(), maxHeight.value.toInt())
    // RemoteContentPreview captures its document once; a new size needs a new capture.
    key(maxWidth, maxHeight, seconds) {
      RemoteContentPreview(Modifier.fillMaxSize(), RcLauncherWidgets.profileFor()) {
        val content: @Composable @RemoteComposable () -> Unit = {
          HeadphoneControlsWidgetContent(
            state = state,
            layout = layout,
            colors = colors,
            onOpen = null,
            onAction = { null },
          )
        }
        if (animated) SwimClockAt(seconds, content) else content()
      }
    }
  }
}

// One preview per footprint rather than @LauncherWidgetResize: with compose-preview 2.79 every stop
// of a resize walk over Remote Compose content renders the first stop's size.
@Preview(name = "Headphone widget · MP3 · 2×1", widthDp = 152, heightDp = 72)
@LauncherWidgetPreview(width = 2, height = 1)
@Composable
internal fun HeadphoneWidgetMp32x1Preview() {
  HeadphoneWidgetPreview(PreviewFixtures.btMp3Mode)
}

@Preview(name = "Headphone widget · MP3 · 3×1", widthDp = 232, heightDp = 72)
@LauncherWidgetPreview(width = 3, height = 1)
@Composable
internal fun HeadphoneWidgetMp33x1Preview() {
  HeadphoneWidgetPreview(PreviewFixtures.btMp3Mode)
}

@Preview(name = "Headphone widget · MP3 · 5×1", widthDp = 392, heightDp = 72)
@LauncherWidgetPreview(width = 5, height = 1)
@Composable
internal fun HeadphoneWidgetMp35x1Preview() {
  HeadphoneWidgetPreview(PreviewFixtures.btMp3Mode)
}

@Preview(name = "Headphone widget · MP3 · 3×2", widthDp = 232, heightDp = 152)
@LauncherWidgetPreview(width = 3, height = 2)
@Composable
internal fun HeadphoneWidgetMp33x2Preview() {
  HeadphoneWidgetPreview(PreviewFixtures.btMp3Mode)
}

@Preview(name = "Headphone widget · MP3 · 5×3", widthDp = 392, heightDp = 232)
@LauncherWidgetPreview(width = 5, height = 3)
@Composable
internal fun HeadphoneWidgetMp35x3Preview() {
  HeadphoneWidgetPreview(PreviewFixtures.btMp3Mode)
}

@Preview(name = "Headphone widget · MP3", widthDp = 312, heightDp = 152)
@LauncherWidgetPreview(width = 4, height = 2)
@Composable
internal fun HeadphoneWidgetMp3Preview() {
  HeadphoneWidgetPreview(PreviewFixtures.btMp3Mode)
}

@Preview(name = "Headphone widget · Bluetooth", widthDp = 312, heightDp = 152)
@LauncherWidgetPreview(width = 4, height = 2)
@Composable
internal fun HeadphoneWidgetBluetoothPreview() {
  HeadphoneWidgetPreview(
    PreviewFixtures.btConnectedPlaying.copy(
      connectedDevice = PreviewFixtures.connectedDevice.copy(name = "OpenSwim Pro")
    )
  )
}

@Preview(name = "Headphone widget · disconnected", widthDp = 312, heightDp = 152)
@LauncherWidgetPreview(width = 4, height = 2)
@Composable
internal fun HeadphoneWidgetDisconnectedPreview() {
  HeadphoneWidgetPreview(PreviewFixtures.btDisconnected)
}

@Preview(name = "Headphone widget · light", widthDp = 312, heightDp = 152)
@LauncherWidgetPreview(width = 4, height = 2)
@Composable
internal fun HeadphoneWidgetLightPreview() {
  HeadphoneWidgetPreview(PreviewFixtures.btMp3Mode, WidgetColors.Light)
}

/** Playing: the play/pause button is a pool of moving water. */
@Preview(name = "Headphone widget · playing · animated", widthDp = 312, heightDp = 152)
@AnimatedPreview(durationMs = 3000, frameIntervalMs = 80, showCurves = false)
@Composable
internal fun HeadphoneWidgetPlayingAnimatedPreview() {
  HeadphoneWidgetPreview(
    PreviewFixtures.btConnectedPlaying.copy(
      connectedDevice = PreviewFixtures.connectedDevice.copy(name = "OpenSwim Pro")
    ),
    animated = true,
  )
}
