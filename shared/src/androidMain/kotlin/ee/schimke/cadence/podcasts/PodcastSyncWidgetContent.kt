@file:Suppress("RestrictedApi", "RestrictedApiAndroidX")

package ee.schimke.cadence.podcasts

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Sync
import androidx.compose.remote.creation.compose.action.RemoteAction
import androidx.compose.remote.creation.compose.layout.RemoteAlignment
import androidx.compose.remote.creation.compose.layout.RemoteArrangement
import androidx.compose.remote.creation.compose.layout.RemoteBox
import androidx.compose.remote.creation.compose.layout.RemoteColumn
import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.remote.creation.compose.layout.RemoteRow
import androidx.compose.remote.creation.compose.layout.RemoteText
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.background
import androidx.compose.remote.creation.compose.modifier.clickable
import androidx.compose.remote.creation.compose.modifier.fillMaxSize
import androidx.compose.remote.creation.compose.modifier.fillMaxWidth
import androidx.compose.remote.creation.compose.modifier.height
import androidx.compose.remote.creation.compose.modifier.padding
import androidx.compose.remote.creation.compose.state.rc
import androidx.compose.remote.creation.compose.state.rdp
import androidx.compose.remote.creation.compose.state.rs
import androidx.compose.remote.creation.compose.state.rsp
import androidx.compose.remote.creation.compose.text.RemoteFontWeight
import androidx.compose.remote.creation.compose.text.RemoteTextOverflow
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.remote.creation.compose.modifier.RemoteRole
import androidx.compose.remote.creation.compose.modifier.clip
import androidx.compose.remote.creation.compose.modifier.contentDescription
import androidx.compose.remote.creation.compose.modifier.heightIn
import androidx.compose.remote.creation.compose.modifier.role
import androidx.compose.remote.creation.compose.modifier.semantics
import androidx.compose.remote.creation.compose.modifier.size
import androidx.compose.remote.creation.compose.modifier.width
import androidx.compose.remote.creation.compose.shapes.RemoteRoundedCornerShape
import ee.schimke.composeai.rcwidget.creation.androidx.rcWidgetHostAction
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import ee.schimke.cadence.swim.PoolSeason
import ee.schimke.cadence.swim.androidx.SwimmingPool
import ee.schimke.cadence.widgets.WidgetColors
import ee.schimke.cadence.widgets.waterColors

/**
 * The podcast sync widget as Remote Compose, and the status card at the top of Home: [headline]
 * says what to do next, then what the headphones hold, what this phone has ready, the battery and
 * where the episodes come from ([source]), as far as the slot has room. Drawn by the launcher itself (Android 16+), so it fills and
 * reflows with whatever slot the user resizes it to; [onTap] opens Cadence.
 *
 * Colours come from [colors] (system dynamic Material on device, [WidgetColors.Light] /
 * [WidgetColors.Dark] in previews) so the card follows light and dark home screens. The pool strip
 * at the bottom uses night water on dark chrome and outdoor pool water on light.
 */
@Composable
@RemoteComposable
fun PodcastSyncWidgetContent(
  ready: Boolean,
  copied: String,
  available: String,
  battery: String?,
  showSwimming: Boolean,
  heightDp: Int,
  onTap: RemoteAction? = null,
  widthDp: Int = 232,
  colors: WidgetColors = WidgetColors.Dark,
  /** The season's pool: its water, the swimmer's hat and what's going on around them. */
  season: PoolSeason = PoolSeason.None,
  /** What to do next, such as "Ready to sync"; null leaves it out. */
  headline: String? = null,
  /** Where the episodes come from and when they were checked; shown when the slot is tall. */
  source: String? = null,
  /** The headline asks for something now, so it takes the accent colour. */
  attention: Boolean = false,
  /**
   * Home's buttons, change list and icons, each tapping a host action named by its id. The
   * launcher widget leaves this out and opens Cadence on any tap.
   */
  extras: StatusCardExtras? = null,
  /** Home sizes the card to its content, so it draws at a fixed scale instead of the slot's. */
  fixedScale: Float? = null,
) {
  val lane = heightDp >= 140
  // A tall slot also has room for the source line under the battery.
  val roomy = lane && heightDp >= 156
  val shown = source.takeIf { roomy }
  val scale =
    fixedScale
      ?: minOf(widthDp / 232f, heightDp / if (shown != null) 172f else if (lane) 152f else 72f)
        .coerceIn(0.8f, 1.35f)
  val poolHeight =
    if (extras != null) STATUS_CARD_POOL_DP else (heightDp * 0.28f).toInt().coerceIn(40, 72)
  val padding = 8 * scale
  val icons =
    listOfNotNull(
      (Icons.Default.Refresh to "Check for new episodes").takeIf { extras?.refresh == true }?.let {
        StatusCardExtras.REFRESH to it
      },
      (Icons.Default.Headphones to "Audio controls").takeIf { extras?.audioControls == true }?.let {
        StatusCardExtras.AUDIO_CONTROLS to it
      },
    )
  // Body text stays at least 12sp after scale (launcher.type.min-size).
  val bodySp = (12 * scale).coerceAtLeast(12f)
  // No outer clip: the launcher applies its own system corner radius to the widget.
  RemoteBox(
    modifier =
      RemoteModifier.fillMaxSize()
        .let { if (onTap != null) it.clickable(onTap) else it }
        .background(colors.background.rc),
  ) {
    // Metrics hug the top; the pool sits on the bottom edge. SpaceBetween avoids an empty
    // middle band when the slot is taller than the three status lines.
    RemoteColumn(
      modifier = RemoteModifier.fillMaxSize(),
      verticalArrangement = if (lane) RemoteArrangement.SpaceBetween else RemoteArrangement.Center,
    ) {
      RemoteColumn(
        modifier = RemoteModifier.fillMaxWidth().padding((8 * scale).rdp),
        verticalArrangement = RemoteArrangement.spacedBy((2 * scale).rdp),
      ) {
        if (headline != null)
          RemoteRow(verticalAlignment = RemoteAlignment.CenterVertically) {
            val textWidth = widthDp - 2 * padding - icons.size * ICON_BUTTON_DP
            RemoteText(
              headline.rs,
              modifier = if (icons.isEmpty()) RemoteModifier else RemoteModifier.width(textWidth.rdp),
              color = (if (attention) colors.primary else colors.onBackground).rc,
              fontSize = (15 * scale).coerceAtLeast(13f).rsp,
              fontWeight = RemoteFontWeight.Medium,
              maxLines = 1,
              overflow = RemoteTextOverflow.Ellipsis,
            )
            icons.forEach { (id, icon) -> StatusIconButton(icon.first, icon.second, id, colors) }
          }
        SyncMetricLine(
          copied,
          Icons.Default.Headphones,
          "on headphones",
          colors.primary,
          colors.onBackground,
          scale,
          bodySp,
        )
        SyncMetricLine(
          available,
          Icons.Default.Sync,
          if (ready) "ready to sync" else "to prepare",
          if (ready) colors.tertiary else colors.warning,
          colors.onBackground,
          scale,
          bodySp,
        )
        // A one-row slot with a headline has room for two more lines, not three.
        if (lane || headline == null) RemoteRow(
          horizontalArrangement = RemoteArrangement.spacedBy(6.rdp),
          verticalAlignment = RemoteAlignment.CenterVertically,
        ) {
          SyncStatusIcon(Icons.Default.BatteryFull, colors.supporting, (14 * scale).toInt())
          RemoteText(
            (battery ?: "Battery unavailable").rs,
            color = colors.supporting.rc,
            fontSize = bodySp.rsp,
            maxLines = 1,
            overflow = RemoteTextOverflow.Ellipsis,
          )
        }
        if (shown != null)
          RemoteRow(
            horizontalArrangement = RemoteArrangement.spacedBy(6.rdp),
            verticalAlignment = RemoteAlignment.CenterVertically,
          ) {
            SyncStatusIcon(Icons.Default.Cloud, colors.supporting, (14 * scale).toInt())
            RemoteText(
              shown.rs,
              color = colors.supporting.rc,
              fontSize = bodySp.rsp,
              maxLines = 1,
              overflow = RemoteTextOverflow.Ellipsis,
            )
          }
        extras?.changes?.let { changes -> SyncChangesRow(changes, extras.changeLines, colors, bodySp) }
        // Two to a row: three buttons ("Eject USB", "Sync again", "Sync folders") don't fit a
        // 360dp phone, and a Remote Compose row can't wrap.
        extras?.buttons.orEmpty().chunked(STATUS_BUTTONS_PER_ROW).forEach { row ->
          RemoteRow(
            modifier = RemoteModifier.padding(top = 4.rdp),
            horizontalArrangement = RemoteArrangement.spacedBy(8.rdp),
            verticalAlignment = RemoteAlignment.CenterVertically,
          ) {
            row.forEach { StatusButton(it, colors) }
          }
        }
      }
      if (lane) {
        SwimmingPool(
          RemoteModifier.fillMaxWidth().height(poolHeight.rdp),
          water = colors.waterColors(),
          swimming = showSwimming && ready,
          level = 0.35f,
          bubbles = if (ready) 6 else 3,
          amplitude = 0.025f,
          swimmerSize = 0.48f,
          standing = !showSwimming,
          season = season,
        )
      }
    }
  }
}

@Composable
@RemoteComposable
private fun SyncMetricLine(
  text: String,
  icon: ImageVector,
  label: String,
  color: Color,
  bodyColor: Color,
  scale: Float,
  bodySp: Float,
) {
  val detail = text.substringAfter(": ", "")
  val count = detail.substringBefore(" eps").toIntOrNull()
  RemoteRow(
    horizontalArrangement = RemoteArrangement.spacedBy(6.rdp),
    verticalAlignment = RemoteAlignment.CenterVertically,
  ) {
    SyncStatusIcon(icon, color, (14 * scale).toInt())
    if (count != null) {
      RemoteText(
        count.toString().rs,
        color = color.rc,
        fontSize = (17 * scale).rsp,
        fontWeight = RemoteFontWeight.Medium,
        maxLines = 1,
      )
      val remainder = detail.substringAfter(" eps", "").replace(" marked", " played")
      RemoteText(
        "$label$remainder".rs,
        color = bodyColor.rc,
        fontSize = bodySp.rsp,
        maxLines = 1,
        overflow = RemoteTextOverflow.Ellipsis,
      )
    } else {
      RemoteText(
        text.rs,
        color = bodyColor.rc,
        fontSize = bodySp.rsp,
        maxLines = 1,
        overflow = RemoteTextOverflow.Ellipsis,
      )
    }
  }
}

/** At most this many buttons share a row in the status card. */
const val STATUS_BUTTONS_PER_ROW = 2

/** Height of the pool strip on Home's status card. */
const val STATUS_CARD_POOL_DP = 44

/** Width (and height) of each icon button beside the headline. */
private const val ICON_BUTTON_DP = 40f

@Composable
@RemoteComposable
private fun StatusIconButton(icon: ImageVector, label: String, id: String, colors: WidgetColors) {
  RemoteBox(
    modifier =
      RemoteModifier.size(ICON_BUTTON_DP.rdp)
        .clip(RemoteRoundedCornerShape((ICON_BUTTON_DP / 2).rdp))
        .semantics {
          contentDescription = label.rs
          role = RemoteRole.Button
        }
        .clickable(rcWidgetHostAction(name = id, payload = id)),
    contentAlignment = RemoteAlignment.Center,
  ) {
    SyncStatusIcon(icon, colors.primary, 22)
  }
}

/** "What will change · 3 new", tapping to show or hide one line per change below it. */
@Composable
@RemoteComposable
private fun SyncChangesRow(
  summary: String,
  lines: List<String>,
  colors: WidgetColors,
  bodySp: Float,
) {
  val expanded = lines.isNotEmpty()
  RemoteRow(
    modifier =
      RemoteModifier.heightIn(40.rdp, 48.rdp)
        .semantics {
          contentDescription =
            ((if (expanded) "Hide changes. " else "Show changes. ") + "What will change: $summary").rs
          role = RemoteRole.Button
        }
        .clickable(rcWidgetHostAction(name = StatusCardExtras.CHANGES, payload = StatusCardExtras.CHANGES)),
    horizontalArrangement = RemoteArrangement.spacedBy(6.rdp),
    verticalAlignment = RemoteAlignment.CenterVertically,
  ) {
    RemoteText(
      "What will change · $summary".rs,
      color = colors.onBackground.rc,
      fontSize = bodySp.rsp,
      fontWeight = RemoteFontWeight.Medium,
      maxLines = 1,
      overflow = RemoteTextOverflow.Ellipsis,
    )
    SyncStatusIcon(
      if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
      colors.onBackground,
      18,
    )
  }
  lines.forEach { line ->
    RemoteText(
      line.rs,
      color = colors.supporting.rc,
      fontSize = bodySp.rsp,
      maxLines = 1,
      overflow = RemoteTextOverflow.Ellipsis,
    )
  }
}

/** A filled pill for the step's main action, a text button for the others. */
@Composable
@RemoteComposable
private fun StatusButton(button: StatusCardButton, colors: WidgetColors) {
  RemoteBox(
    modifier =
      RemoteModifier.height(40.rdp)
        .clip(RemoteRoundedCornerShape(20.rdp))
        .let { if (button.primary) it.background(colors.primary.rc) else it }
        .semantics {
          contentDescription = button.label.rs
          role = RemoteRole.Button
        }
        .clickable(rcWidgetHostAction(name = button.id, payload = button.id))
        .padding(horizontal = 16.rdp),
    contentAlignment = RemoteAlignment.Center,
  ) {
    RemoteText(
      button.label.rs,
      color = (if (button.primary) colors.onPrimary else colors.primary).rc,
      fontSize = 14.rsp,
      fontWeight = RemoteFontWeight.Medium,
      maxLines = 1,
    )
  }
}
