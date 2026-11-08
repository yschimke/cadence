@file:Suppress("RestrictedApi", "RestrictedApiAndroidX")

package ee.schimke.cadence.preview

import android.content.res.Configuration.UI_MODE_NIGHT_NO
import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.remote.tooling.preview.RemoteContentPreview
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import ee.schimke.cadence.podcasts.CloudSyncStatus
import ee.schimke.cadence.podcasts.CloudSyncWidgetContent
import ee.schimke.cadence.podcasts.PodcastSyncWidgetContent
import ee.schimke.cadence.swim.PoolSeason
import ee.schimke.cadence.swim.SwimClockAt
import ee.schimke.cadence.swim.rememberPreviewSeconds
import ee.schimke.cadence.widgets.WidgetColors
import ee.schimke.composeai.preview.AnimatedPreview
import ee.schimke.composeai.preview.LauncherWidgetPreview
import ee.schimke.composeai.rcwidget.creation.rcWidgetHostAction
import ee.schimke.composeai.rcwidget.launcher.RcLauncherWidgets

@Composable
private fun PodcastWidgetPreview(
  swimming: Boolean,
  battery: String? = "50% · ~3h MP3 · ~4h 30m BT",
  colors: WidgetColors = WidgetColors.Dark,
  season: PoolSeason = PoolSeason.None,
  /** Freeze static previews so wall-clock swimming never creates spurious screenshot diffs. */
  seconds: Float = 3.2f,
) {
  BoxWithConstraints(Modifier.fillMaxSize()) {
    val width = maxWidth.value.toInt()
    val height = maxHeight.value.toInt()
    key(width, height, colors, seconds) {
      RemoteContentPreview(Modifier.fillMaxSize(), RcLauncherWidgets.profileFor()) {
        val content: @Composable @RemoteComposable () -> Unit = {
          PodcastSyncWidgetContent(
            true,
            "Last copy: 3 eps · 138m · 0 marked",
            "Ready: 3 eps · 138m",
            battery,
            swimming,
            height,
            widthDp = width,
            colors = colors,
            season = season,
          )
        }
        SwimClockAt(seconds, content)
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

@Preview(
  name = "Podcast widget · MP3 · light",
  widthDp = 312,
  heightDp = 152,
  uiMode = UI_MODE_NIGHT_NO,
)
@LauncherWidgetPreview(width = 4, height = 2)
@Composable
internal fun PodcastWidgetMp3LightPreview() {
  PodcastWidgetPreview(true, colors = WidgetColors.Light)
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

@Composable
private fun CloudSyncPreviewBody(colors: WidgetColors) {
  BoxWithConstraints(Modifier.fillMaxSize()) {
    val width = maxWidth.value.toInt()
    val height = maxHeight.value.toInt()
    key(width, height, colors) {
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
          colors = colors,
        )
      }
    }
  }
}

@Preview(name = "Cloud sync widget", widthDp = 312, heightDp = 192, uiMode = UI_MODE_NIGHT_YES)
@LauncherWidgetPreview(width = 4, height = 2)
@Composable
internal fun CloudSyncWidgetPreview() {
  CloudSyncPreviewBody(WidgetColors.Dark)
}

@Preview(
  name = "Cloud sync widget · light",
  widthDp = 312,
  heightDp = 192,
  uiMode = UI_MODE_NIGHT_NO,
)
@LauncherWidgetPreview(width = 4, height = 2)
@Composable
internal fun CloudSyncWidgetLightPreview() {
  CloudSyncPreviewBody(WidgetColors.Light)
}

@Preview(name = "Cloud sync widget · small", widthDp = 250, heightDp = 160)
@LauncherWidgetPreview(width = 3, height = 2)
@Composable
internal fun CloudSyncWidgetSmallPreview() {
  CloudSyncPreviewBody(WidgetColors.Dark)
}

@Preview(name = "Cloud sync widget · large", widthDp = 420, heightDp = 300)
@LauncherWidgetPreview(width = 5, height = 3)
@Composable
internal fun CloudSyncWidgetLargePreview() {
  CloudSyncPreviewBody(WidgetColors.Dark)
}

@Preview(name = "Podcast widget · MP3 large", widthDp = 420, heightDp = 260)
@LauncherWidgetPreview(width = 5, height = 3)
@Composable
internal fun PodcastWidgetMp3LargePreview() {
  PodcastWidgetPreview(true)
}

// The seasons' pools (`composeApp/livery/seasons/`), swimming, each pinned to one moment.

@Preview(name = "Podcast widget · winter", widthDp = 312, heightDp = 152)
@LauncherWidgetPreview(width = 4, height = 2)
@Composable
internal fun PodcastWidgetWinterPreview() {
  PodcastWidgetPreview(true, season = PoolSeason.Winter, seconds = 3.2f)
}

@Preview(name = "Podcast widget · Christmas", widthDp = 312, heightDp = 152)
@LauncherWidgetPreview(width = 4, height = 2)
@Composable
internal fun PodcastWidgetChristmasPreview() {
  PodcastWidgetPreview(true, season = PoolSeason.Christmas, seconds = 3.2f)
}

@Preview(name = "Podcast widget · New Year", widthDp = 312, heightDp = 152)
@LauncherWidgetPreview(width = 4, height = 2)
@Composable
internal fun PodcastWidgetNewYearPreview() {
  PodcastWidgetPreview(true, season = PoolSeason.NewYear, seconds = 1.2f)
}

@Preview(name = "Podcast widget · Halloween", widthDp = 312, heightDp = 152)
@LauncherWidgetPreview(width = 4, height = 2)
@Composable
internal fun PodcastWidgetHalloweenPreview() {
  PodcastWidgetPreview(true, season = PoolSeason.Halloween, seconds = 18f)
}

@Preview(name = "Podcast widget · Christmas · Bluetooth", widthDp = 312, heightDp = 152)
@LauncherWidgetPreview(width = 4, height = 2)
@Composable
internal fun PodcastWidgetChristmasStandingPreview() {
  PodcastWidgetPreview(false, season = PoolSeason.Christmas, seconds = 0f)
}

/** Christmas while swimming: snow falling into the pool. */
@Preview(name = "Podcast widget · Christmas · animated", widthDp = 312, heightDp = 152)
@AnimatedPreview(durationMs = 4000, frameIntervalMs = 80, showCurves = false)
@Composable
internal fun PodcastWidgetChristmasAnimatedPreview() {
  PodcastWidgetPreview(true, season = PoolSeason.Christmas, seconds = rememberPreviewSeconds())
}
