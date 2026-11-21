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
 * The card's height at scale 1: the taller of its two columns, then 16dp of padding and the pool.
 * On the left, each row plus the column's 2dp gap: the headline (up to two 22dp lines), two 23dp
 * count lines (17sp numbers), the 20dp battery and source lines (their icons set the height), and
 * the change summary (at least 48dp) with its 16dp lines. On the right, the 48dp icon buttons and
 * a 48dp pill per button, 4dp apart.
 */
internal fun statusCardHeight(
  source: String?,
  extras: StatusCardExtras,
  headline: String? = null,
): Int {
  val left = buildList {
    if (headline != null) add(if (headline.length > 26) 44 else 22)
    add(23)
    add(23)
    add(20)
    if (source != null) add(20)
    if (extras.changes != null) {
      add(48)
      repeat(extras.changeLines.size) { add(16) }
    }
  }
  val right = buildList {
    if (extras.refresh || extras.audioControls) add(48)
    repeat(extras.buttons.size) { add(48) }
  }
  val leftHeight = left.sum() + (left.size - 1) * 2
  val rightHeight = right.sum() + (right.size - 1).coerceAtLeast(0) * 4
  return maxOf(leftHeight, rightHeight) + 16 + STATUS_CARD_POOL_DP
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
  val height = statusCardHeight(source, extras, headline)
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
        // The player is an Android View; screen readers need a name for it as well.
        contentDescription = headline ?: "Sync status",
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
