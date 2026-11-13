package ee.schimke.cadence.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import ee.schimke.cadence.notifications.NotificationBody
import ee.schimke.cadence.notifications.NotificationIcon
import ee.schimke.cadence.notifications.NotificationTemplateContent
import ee.schimke.cadence.widgets.WidgetColors

/** The notification template's body with [body]'s values, over the shade's surface. */
@Composable
private fun NotificationBodyPreview(
  body: NotificationBody,
  cancel: Boolean = false,
  colors: WidgetColors = WidgetColors.Dark,
) {
  RcContentPreview(
    Modifier.fillMaxSize().background(if (colors == WidgetColors.Dark) ShadeDark else ShadeLight),
    key = listOf(body, cancel, colors),
  ) {
    NotificationTemplateContent(
      onOpen = null,
      onCancel = null,
      sample = body,
      sampleCancel = cancel,
      colors = colors,
    )
  }
}

private val ShadeDark = Color(0xFF1F1F23)
private val ShadeLight = Color(0xFFF3EDF7)

@Preview(name = "Notification · downloading", widthDp = 360, heightDp = 150)
@Composable
internal fun NotificationDownloadingPreview() {
  NotificationBodyPreview(
    NotificationBody(
      "Downloading podcasts",
      "Lighthouses of the North Sea · 42%",
      NotificationIcon.Download,
      progress = 0.42f,
    ),
    cancel = true,
  )
}

@Preview(name = "Notification · syncing · light", widthDp = 360, heightDp = 104)
@Composable
internal fun NotificationSyncingPreview() {
  NotificationBodyPreview(
    NotificationBody(
      "Syncing files",
      "2026-10-28.mp3 (3/12)",
      NotificationIcon.Sync,
      progress = 0.25f,
    ),
    colors = WidgetColors.Light,
  )
}

@Preview(name = "Notification · ready", widthDp = 360, heightDp = 88)
@Composable
internal fun NotificationReadyPreview() {
  NotificationBodyPreview(
    NotificationBody(
      "3 episodes ready",
      "Plug in your headphones to copy them",
      NotificationIcon.Done,
    ),
  )
}

@Preview(name = "Notification · failed", widthDp = 360, heightDp = 104)
@Composable
internal fun NotificationFailedPreview() {
  NotificationBodyPreview(
    NotificationBody(
      "Download stopped",
      "Couldn't download “Tides of the Pacific”: the server sent an empty file. Try again to " +
        "retry the rest.",
      NotificationIcon.Error,
    ),
  )
}
