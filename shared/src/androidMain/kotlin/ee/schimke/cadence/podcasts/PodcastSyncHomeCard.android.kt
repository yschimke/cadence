package ee.schimke.cadence.podcasts

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ee.schimke.cadence.widgets.WidgetColors
import ee.schimke.composeai.rcwidget.creation.androidx.RcWidgetCaptureSize
import ee.schimke.composeai.rcwidget.launcher.RcAdaptiveRemoteDocumentPlayer
import ee.schimke.composeai.rcwidget.launcher.RcLauncherWidgetCapture
import ee.schimke.composeai.rcwidget.launcher.captureLauncherWidget
import kotlinx.coroutines.CancellationException

/**
 * The card's height for what it shows, at scale 1. Each row adds its own height plus the column's
 * 2dp gap: the 40dp headline row (its icon buttons set the height), two 23dp count lines (17sp
 * numbers), the 20dp battery and source lines (their icons set the height), the change summary
 * (at least 40dp) and its 16dp lines, each 44dp row of buttons (4dp above a 40dp pill), then 16dp
 * of padding and the pool.
 */
internal fun statusCardHeight(source: String?, extras: StatusCardExtras): Int {
  val rows = buildList {
    add(40)
    add(23)
    add(23)
    add(20)
    if (source != null) add(20)
    if (extras.changes != null) {
      add(40)
      repeat(extras.changeLines.size) { add(16) }
    }
    repeat(extras.buttons.chunked(STATUS_BUTTONS_PER_ROW).size) { add(44) }
  }
  return rows.sum() + (rows.size - 1) * 2 + 16 + STATUS_CARD_POOL_DP
}

@Composable
actual fun PodcastSyncHomeCard(
  copied: String,
  available: String,
  battery: String?,
  ready: Boolean,
  showSwimming: Boolean,
  headline: String?,
  source: String?,
  attention: Boolean,
  extras: StatusCardExtras,
  onAction: (String) -> Unit,
) {
  val context = LocalContext.current
  val colors = WidgetColors.from(MaterialTheme.colorScheme)
  val height = statusCardHeight(source, extras)
  val currentOnAction by rememberUpdatedState(onAction)
  BoxWithConstraints(Modifier.fillMaxWidth().height(height.dp)) {
    val width = maxWidth.value.toInt().coerceAtLeast(1)
    val capture by
      produceState<RcLauncherWidgetCapture?>(
        null,
        width,
        height,
        copied,
        available,
        battery,
        ready,
        showSwimming,
        headline,
        source,
        attention,
        extras,
        colors,
      ) {
        value =
          try {
            captureLauncherWidget(context, RcWidgetCaptureSize(width, height)) {
              PodcastSyncWidgetContent(
                ready,
                copied,
                available,
                battery,
                showSwimming,
                height,
                widthDp = width,
                colors = colors,
                headline = headline,
                source = source,
                attention = attention,
                extras = extras,
                fixedScale = 1f,
              )
            }
          } catch (cancelled: CancellationException) {
            throw cancelled
          } catch (_: Exception) {
            null
          }
      }
    val document = capture
    if (document == null)
      PodcastSyncHomeFallback(copied, available, battery, headline, source, extras, onAction)
    else
      RcAdaptiveRemoteDocumentPlayer(
        document.bytes,
        Modifier.fillMaxWidth().height(height.dp),
        onNamedAction = { name, metadata ->
          // Host actions arrive by name, or by index into the capture's actions.
          val id =
            (metadata as? String)?.substringBefore("\n--RC-METADATA-V1--\n")
              ?: name.toIntOrNull()?.let { document.actions[it] }
              ?: name
          currentOnAction(id)
        },
      )
  }
}
