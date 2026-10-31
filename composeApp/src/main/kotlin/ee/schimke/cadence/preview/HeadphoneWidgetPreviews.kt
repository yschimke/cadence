@file:Suppress("RestrictedApi", "RestrictedApiAndroidX")

package ee.schimke.cadence.preview

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.remote.tooling.preview.RemoteContentPreview
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import ee.schimke.cadence.bluetooth.BluetoothState
import ee.schimke.cadence.widgets.HeadphoneControlsWidgetContent
import ee.schimke.cadence.widgets.HeadphoneWidgetState
import ee.schimke.composeai.rcwidget.launcher.RcLauncherWidgets

@Composable
private fun HeadphoneWidgetPreview(bluetooth: BluetoothState, compact: Boolean) {
  RemoteContentPreview(Modifier.fillMaxSize(), RcLauncherWidgets.profileFor()) {
    HeadphoneControlsWidgetContent(
      state = HeadphoneWidgetState.from(bluetooth),
      compact = compact,
      onOpen = null,
      onAction = { null },
    )
  }
}

@Preview(name = "Headphone widget · MP3", widthDp = 300, heightDp = 150)
@Composable
internal fun HeadphoneWidgetMp3Preview() {
  HeadphoneWidgetPreview(PreviewFixtures.btMp3Mode, compact = false)
}

@Preview(name = "Headphone widget · Bluetooth", widthDp = 300, heightDp = 150)
@Composable
internal fun HeadphoneWidgetBluetoothPreview() {
  HeadphoneWidgetPreview(
    PreviewFixtures.btConnectedPlaying.copy(
      connectedDevice = PreviewFixtures.connectedDevice.copy(name = "OpenSwim Pro")
    ),
    compact = false,
  )
}

@Preview(name = "Headphone widget · compact", widthDp = 300, heightDp = 100)
@Composable
internal fun HeadphoneWidgetCompactPreview() {
  HeadphoneWidgetPreview(PreviewFixtures.btMp3Mode, compact = true)
}

@Preview(name = "Headphone widget · disconnected", widthDp = 300, heightDp = 150)
@Composable
internal fun HeadphoneWidgetDisconnectedPreview() {
  HeadphoneWidgetPreview(PreviewFixtures.btDisconnected, compact = false)
}
