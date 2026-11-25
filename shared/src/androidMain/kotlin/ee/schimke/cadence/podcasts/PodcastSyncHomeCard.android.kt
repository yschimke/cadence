package ee.schimke.cadence.podcasts

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.remote.player.view.RemoteComposePlayer
import androidx.compose.ui.unit.dp
import ee.schimke.cadence.settings.LocalAppSettings
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
  working: Boolean,
  progress: Float?,
  onAction: (String) -> Unit,
) {
  val colors = WidgetColors.from(MaterialTheme.colorScheme)
  val height = statusCardHeight(source, extras, headline)
  val currentOnAction by rememberUpdatedState(onAction)
  val renderer = LocalStatusCardRenderer.current
  val design = LocalAppSettings.current.cardDesign.ifEmpty { StatusCardTemplate.SWIMMING_DESIGN }
  // A design that fails to play falls back to the card Home draws itself, until another is picked.
  var failed by remember(design) { mutableStateOf(false) }
  BoxWithConstraints(Modifier.fillMaxWidth().height(height.dp)) {
    val width = maxWidth.value.toInt().coerceAtLeast(1)
    val values =
      StatusCardValues(
        headline = headline,
        attention = attention,
        copied = copied,
        available = available,
        ready = ready,
        battery = battery,
        source = source,
        extras = extras,
        // Each change binds the design again: the swimmer moves in whole percents.
        pool =
          StatusCardPool.values(
            showSwimming,
            ready,
            working,
            progress?.let { (it.coerceIn(0f, 1f) * 100).toInt() / 100f },
          ),
        colors = colors,
      )
    val document by
      produceState<RenderedCard?>(null, renderer, design, failed, values, width, height) {
        // Keeps the last document while the next binds, so the card never blinks empty.
        if (renderer == null || failed) value = null
        else
          renderer.render(design, values, width, height)?.let { value = RenderedCard(it, values) }
            ?: run { value = null }
      }
    val rendered = document
    if (rendered != null) {
      // Taps are read against the values the playing document was drawn from: while the next one
      // binds, the card on screen still shows the old buttons in its slots.
      val shown by rememberUpdatedState(rendered.values)
      RcAdaptiveRemoteDocumentPlayer(
        rendered.bytes,
        Modifier.fillMaxWidth().height(height.dp),
        // The player is an Android View; screen readers need a name for it as well.
        contentDescription = headline ?: "Sync status",
        onError = { failed = true },
        onNamedAction = { name, metadata ->
          val action = (metadata as? String)?.substringBefore("\n--RC-METADATA-V1--\n") ?: name
          // A button slot's tap is the button in that slot.
          currentOnAction(shown.buttonFor(action)?.id ?: action)
        },
      )
    } else {
      CapturedStatusCard(
        copied,
        available,
        battery,
        ready,
        showSwimming,
        headline,
        source,
        attention,
        extras,
        working,
        progress,
        colors,
        width,
        height,
      ) {
        currentOnAction(it)
      }
    }
  }
}

/**
 * The card drawn on the device from [PodcastSyncWidgetContent], for when no Livery design can be
 * played: the pool is named values written into the playing card, so the swimmer keeps pace with a
 * task without a new capture.
 */
@Composable
private fun CapturedStatusCard(
  copied: String,
  available: String,
  battery: String?,
  ready: Boolean,
  showSwimming: Boolean,
  headline: String?,
  source: String?,
  attention: Boolean,
  extras: StatusCardExtras,
  working: Boolean,
  progress: Float?,
  colors: WidgetColors,
  width: Int,
  height: Int,
  onAction: (String) -> Unit,
) {
  val context = LocalContext.current
  val currentOnAction by rememberUpdatedState(onAction)
  val pool = StatusCardPool.values(showSwimming, ready, working, progress)
  val currentPool by rememberUpdatedState(pool)
  val player = remember { PlayerRef() }
  SideEffect { player.current?.write(pool) }
  val capture by
    produceState<RcLauncherWidgetCapture?>(
      null,
      width,
      height,
      copied,
      available,
      battery,
      ready,
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
              working = working,
              progress = progress,
              namedPool = true,
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
    PodcastSyncHomeFallback(
      copied,
      available,
      battery,
      headline,
      source,
      extras,
      working,
      progress,
      onAction,
    )
  else
    RcAdaptiveRemoteDocumentPlayer(
      document.bytes,
      Modifier.fillMaxWidth().height(height.dp),
      // Each new document gets a new player: hold on to it and give it the pool as it is now,
      // which may have moved on since the capture.
      seed = { playing ->
        player.current = playing
        playing.write(currentPool)
      },
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

/** A design's document and the values it was drawn with, whose button slots its taps name. */
private class RenderedCard(val bytes: ByteArray, val values: StatusCardValues)

/** The card's playing document, once there is one. */
private class PlayerRef {
  var current: RemoteComposePlayer? = null
}

/** Writes [pool] into the playing card's named values; booleans are named ints, 0 or 1. */
private fun RemoteComposePlayer.write(pool: StatusCardPool) {
  setUserLocalInt(StatusCardPool.SWIMMING, if (pool.swimming) 1 else 0)
  setUserLocalInt(StatusCardPool.STANDING, if (pool.standing) 1 else 0)
  setUserLocalFloat(StatusCardPool.PROGRESS, pool.progress)
  invalidate()
}
