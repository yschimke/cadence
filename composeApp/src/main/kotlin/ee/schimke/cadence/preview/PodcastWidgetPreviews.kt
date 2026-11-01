@file:Suppress("RestrictedApi", "RestrictedApiAndroidX")

package ee.schimke.cadence.preview

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.remote.tooling.preview.RemoteContentPreview
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import ee.schimke.cadence.podcasts.CloudSyncStatus
import ee.schimke.cadence.podcasts.CloudSyncWidgetContent
import ee.schimke.cadence.podcasts.PodcastSyncWidgetContent
import ee.schimke.composeai.preview.LauncherWidgetPreview
import ee.schimke.composeai.rcwidget.creation.rcWidgetHostAction
import ee.schimke.composeai.rcwidget.launcher.RcLauncherWidgets

@Composable
private fun PodcastWidgetPreview(
  swimming: Boolean,
  battery: String? = "50% · ~3h MP3 · ~4h 30m BT",
) {
  BoxWithConstraints(Modifier.fillMaxSize()) {
    val width = maxWidth.value.toInt()
    val height = maxHeight.value.toInt()
    key(width, height) {
      RemoteContentPreview(Modifier.fillMaxSize(), RcLauncherWidgets.profileFor()) {
        PodcastSyncWidgetContent(
          true,
          "Last copy: 3 eps · 138m · 0 marked",
          "Ready: 3 eps · 138m",
          battery,
          swimming,
          height,
          widthDp = width,
        )
      }
    }
  }
}

@Preview(name = "Podcast widget · Bluetooth", widthDp = 232, heightDp = 72)
@LauncherWidgetPreview(width = 3, height = 1)
@Composable
internal fun PodcastWidgetBluetoothPreview() {
  PodcastWidgetPreview(false)
}

@Preview(name = "Podcast widget · MP3", widthDp = 312, heightDp = 152)
@LauncherWidgetPreview(width = 4, height = 2)
@Composable
internal fun PodcastWidgetMp3Preview() {
  PodcastWidgetPreview(true)
}

@Preview(name = "Podcast widget · Bluetooth pool", widthDp = 312, heightDp = 152)
@LauncherWidgetPreview(width = 4, height = 2)
@Composable
internal fun PodcastWidgetBluetoothPoolPreview() {
  PodcastWidgetPreview(false)
}

@Preview(name = "Podcast widget · MP3 small", widthDp = 232, heightDp = 72)
@LauncherWidgetPreview(width = 3, height = 1)
@Composable
internal fun PodcastWidgetMp3SmallPreview() {
  PodcastWidgetPreview(true)
}

@Preview(name = "Podcast widget · disconnected", widthDp = 312, heightDp = 152)
@LauncherWidgetPreview(width = 4, height = 2)
@Composable
internal fun PodcastWidgetDisconnectedPreview() {
  PodcastWidgetPreview(false, null)
}

@Preview(name = "Cloud sync widget", widthDp = 312, heightDp = 192)
@LauncherWidgetPreview(width = 4, height = 2)
@Composable
internal fun CloudSyncWidgetPreview() {
  BoxWithConstraints(Modifier.fillMaxSize()) {
    val width = maxWidth.value.toInt()
    val height = maxHeight.value.toInt()
    RemoteContentPreview(Modifier.fillMaxSize(), RcLauncherWidgets.profileFor()) {
      CloudSyncWidgetContent(
        CloudSyncStatus(
          "Pocket Casts · Swimming",
          "1 downloaded · 2 to download",
          "0 processed · 3 to process",
          "Last checked: just now",
          true,
          true,
          true,
          true,
          downloadedCount = 1,
          downloadNeeded = 2,
          processingNeeded = 3,
        ),
        onRefresh = rcWidgetHostAction(name = "refresh", payload = "refresh"),
        onDownload = rcWidgetHostAction(name = "download", payload = "download"),
        onProcess = rcWidgetHostAction(name = "process", payload = "process"),
        widthDp = width,
        heightDp = height,
      )
    }
  }
}

@Preview(name = "Cloud sync widget · small", widthDp = 250, heightDp = 160)
@LauncherWidgetPreview(width = 3, height = 2)
@Composable
internal fun CloudSyncWidgetSmallPreview() {
  CloudSyncWidgetPreview()
}

@Preview(name = "Cloud sync widget · large", widthDp = 420, heightDp = 300)
@LauncherWidgetPreview(width = 5, height = 3)
@Composable
internal fun CloudSyncWidgetLargePreview() {
  CloudSyncWidgetPreview()
}

@Preview(name = "Podcast widget · MP3 large", widthDp = 420, heightDp = 260)
@LauncherWidgetPreview(width = 5, height = 3)
@Composable
internal fun PodcastWidgetMp3LargePreview() {
  PodcastWidgetPreview(true)
}
