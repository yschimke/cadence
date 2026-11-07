@file:Suppress("RestrictedApi", "RestrictedApiAndroidX")

package ee.schimke.cadence.widgets

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.remote.creation.RemotePath
import androidx.compose.remote.creation.compose.action.RemoteAction
import androidx.compose.remote.creation.compose.layout.RemoteAlignment
import androidx.compose.remote.creation.compose.layout.RemoteArrangement
import androidx.compose.remote.creation.compose.layout.RemoteBox
import androidx.compose.remote.creation.compose.layout.RemoteCanvas
import androidx.compose.remote.creation.compose.layout.RemoteColumn
import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.remote.creation.compose.layout.RemoteOffset
import androidx.compose.remote.creation.compose.layout.RemoteRow
import androidx.compose.remote.creation.compose.layout.RemoteText
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.background
import androidx.compose.remote.creation.compose.modifier.clickable
import androidx.compose.remote.creation.compose.modifier.clip
import androidx.compose.remote.creation.compose.modifier.contentDescription
import androidx.compose.remote.creation.compose.modifier.fillMaxSize
import androidx.compose.remote.creation.compose.modifier.fillMaxWidth
import androidx.compose.remote.creation.compose.modifier.height
import androidx.compose.remote.creation.compose.modifier.heightIn
import androidx.compose.remote.creation.compose.modifier.padding
import androidx.compose.remote.creation.compose.modifier.role
import androidx.compose.remote.creation.compose.modifier.semantics
import androidx.compose.remote.creation.compose.modifier.size
import androidx.compose.remote.creation.compose.modifier.width
import androidx.compose.remote.creation.compose.shapes.RemoteRoundedCornerShape
import androidx.compose.remote.creation.compose.state.RemoteColor
import androidx.compose.remote.creation.compose.state.RemotePaint
import androidx.compose.remote.creation.compose.state.RemoteString
import androidx.compose.remote.creation.compose.state.rc
import androidx.compose.remote.creation.compose.state.rdp
import androidx.compose.remote.creation.compose.state.rf
import androidx.compose.remote.creation.compose.state.rs
import androidx.compose.remote.creation.compose.state.rsp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathIterator
import androidx.compose.ui.graphics.PathSegment
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import ee.schimke.cadence.swim.LocalSwimClock
import ee.schimke.cadence.swim.WaterBackground

/**
 * Phone counterpart of the Wear "Mode & controls" widget: the headset's mode, what is playing, the
 * transport, and a mode switch. Drawn by the launcher (Android 16+) so it reflows with the slot;
 * the launcher widget provider provides a `RemoteViews` fallback for older launchers.
 *
 * [onAction] returns the host action that fires a [HeadphoneWidgetAction]; null (previews) leaves
 * the control inert.
 */
@Composable
@RemoteComposable
fun HeadphoneControlsWidgetContent(
  state: HeadphoneWidgetState,
  layout: HeadphoneWidgetLayout,
  colors: WidgetColors,
  onOpen: RemoteAction?,
  onAction: @Composable (HeadphoneWidgetAction) -> RemoteAction?,
  /** A seasonal look's palette and backdrop in place of [colors]; the template draws the same. */
  look: HeadphoneLook = HeadphoneLook.Default,
) {
  val palette = RemotePalette.of(look.colors ?: colors)
  val body: @Composable @RemoteComposable () -> Unit = {
    HeadphoneControlsBody(
      texts =
        HeadphoneWidgetTexts(
          title = state.title.rs,
          shortTitle = state.shortTitle.rs,
          detail = state.detail?.rs,
          extra = state.extra?.rs,
        ),
      palette = palette,
      layout = layout,
      modeSwitch = state.modeSwitch,
      showWater = state.showWater,
      onOpen = onOpen,
      onAction = onAction,
      transportButton = { action, button -> button(action) },
      drawBackground = look.backdrop == null,
    )
  }
  val backdrop = look.backdrop
  if (backdrop == null) {
    body()
  } else {
    RemoteBox(RemoteModifier.fillMaxSize().background(palette.background)) {
      val playing = HeadphoneWidgetAction.Pause in state.transport
      backdrop(if (playing) LocalSwimClock.current else 7.5f.rf)
      body()
    }
  }
}

/** The widget's text: constants for a capture with data, named values for a template. */
class HeadphoneWidgetTexts(
  val title: RemoteString,
  val shortTitle: RemoteString,
  /** Null when the slot shows no detail line (the layout drops its row). */
  val detail: RemoteString?,
  val extra: RemoteString?,
)

/**
 * The widget's layout and controls, drawn from remote values so the same code makes a document with
 * data written in ([HeadphoneControlsWidgetContent]) and a values-free template
 * ([HeadphoneControlsTemplate]). [transportButton] draws each transport control: as itself, or
 * inside a conditional layout that picks Play or Pause from a named value.
 */
@Composable
@RemoteComposable
internal fun HeadphoneControlsBody(
  texts: HeadphoneWidgetTexts,
  palette: RemotePalette,
  layout: HeadphoneWidgetLayout,
  modeSwitch: HeadphoneWidgetAction?,
  showWater: Boolean,
  onOpen: RemoteAction?,
  onAction: @Composable (HeadphoneWidgetAction) -> RemoteAction?,
  transportButton:
    @Composable
    (HeadphoneWidgetAction, @Composable (HeadphoneWidgetAction) -> Unit) -> Unit,
  /**
   * False when the caller draws the widget's background itself (a seasonal design's backdrop, drawn
   * once under every state's branch).
   */
  drawBackground: Boolean = true,
) {
  // No outer clip: the launcher applies its own system corner radius to the widget.
  val root =
    RemoteModifier.fillMaxSize()
      .let { if (drawBackground) it.background(palette.background) else it }
      .padding(HeadphoneWidgetLayout.PaddingDp.rdp)
  HeadphoneControlsContent(
    texts,
    palette,
    layout,
    modeSwitch,
    showWater,
    onOpen,
    onAction,
    transportButton,
    root,
  )
}

@Composable
@RemoteComposable
private fun HeadphoneControlsContent(
  texts: HeadphoneWidgetTexts,
  palette: RemotePalette,
  layout: HeadphoneWidgetLayout,
  modeSwitch: HeadphoneWidgetAction?,
  showWater: Boolean,
  onOpen: RemoteAction?,
  onAction: @Composable (HeadphoneWidgetAction) -> RemoteAction?,
  transportButton:
    @Composable
    (HeadphoneWidgetAction, @Composable (HeadphoneWidgetAction) -> Unit) -> Unit,
  root: RemoteModifier,
) {
  // [spaced]: a gap before every button but the first. A one-row slot spaces with these rather
  // than spacedBy: the player shares a weighted row's width out before spacing, so spacedBy would
  // run the last button off the edge.
  val buttons: @Composable (spaced: Boolean) -> Unit = { spaced ->
    layout.transport.forEachIndexed { i, action ->
      if (spaced && i > 0) Gap()
      transportButton(action) { shown ->
        WidgetIconButton(
          action = shown,
          primary = shown == HeadphoneWidgetAction.Play || shown == HeadphoneWidgetAction.Pause,
          showWater = showWater,
          palette = palette,
          onClick = onAction(shown),
        )
      }
    }
    if (layout.showModeSwitch) {
      modeSwitch?.let {
        if (spaced) Gap()
        WidgetIconButton(it, primary = false, palette = palette, onClick = onAction(it))
      }
    }
  }
  if (layout.singleRow) {
    RemoteRow(modifier = root, verticalAlignment = RemoteAlignment.CenterVertically) {
      if (layout.showTitle) {
        // The open-app tap target spans the row's 48dp height, not just the text line. It takes
        // what the buttons leave, so one template fits every width in its tier.
        RemoteColumn(
          modifier =
            RemoteModifier.weight(1f).height(HeadphoneWidgetLayout.ButtonDp.rdp).openOnTap(onOpen),
          verticalArrangement = RemoteArrangement.Center,
        ) {
          Headline(if (layout.shortTitle) texts.shortTitle else texts.title, palette)
        }
        Gap()
      }
      buttons(true)
    }
    return
  }
  RemoteColumn(modifier = root, verticalArrangement = RemoteArrangement.Center) {
    RemoteColumn(
      modifier =
        RemoteModifier.fillMaxWidth()
          // Remote heightIn needs a real maximum; no widget is this tall.
          .heightIn(HeadphoneWidgetLayout.ButtonDp.rdp, 1_000.rdp)
          .openOnTap(onOpen),
      verticalArrangement = RemoteArrangement.spacedBy(2.rdp),
    ) {
      // Hierarchy by size and colour: what is happening, then what to check, then settings.
      Headline(texts.title, palette, fontSize = 17)
      if (layout.showDetail) {
        texts.detail?.let {
          RemoteText(
            it,
            color = palette.onBackground,
            fontSize = 13.rsp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
      if (layout.showExtra) {
        texts.extra?.let {
          RemoteText(
            it,
            color = palette.supporting,
            fontSize = 12.rsp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
    RemoteRow(
      modifier = RemoteModifier.fillMaxWidth(),
      horizontalArrangement = RemoteArrangement.SpaceEvenly,
      verticalAlignment = RemoteAlignment.CenterVertically,
    ) {
      buttons(false)
    }
  }
}

@Composable
@RemoteComposable
private fun Gap() {
  RemoteBox(RemoteModifier.width(HeadphoneWidgetLayout.ButtonGapDp.rdp))
}

@Composable
@RemoteComposable
private fun Headline(text: RemoteString, palette: RemotePalette, fontSize: Int = 15) {
  RemoteText(
    text,
    color = palette.onBackground,
    fontSize = fontSize.rsp,
    fontWeight = FontWeight.Medium,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
  )
}

private fun RemoteModifier.openOnTap(onOpen: RemoteAction?): RemoteModifier =
  if (onOpen != null) clickable(onOpen) else this

@Composable
@RemoteComposable
private fun WidgetIconButton(
  action: HeadphoneWidgetAction,
  primary: Boolean,
  showWater: Boolean = false,
  palette: RemotePalette,
  onClick: RemoteAction?,
) {
  val water = primary && showWater
  val content = if (water) OnWater.rc else if (primary) palette.onPrimary else palette.onTonal
  val size = HeadphoneWidgetLayout.ButtonDp
  // Shown as Pause while audio plays: that is when the water moves.
  val playing = action == HeadphoneWidgetAction.Pause
  RemoteBox(
    modifier =
      RemoteModifier.size(size.rdp)
        .clip(RemoteRoundedCornerShape((size / 2).rdp))
        .let { if (water) it else it.background(if (primary) palette.primary else palette.tonal) }
        .semantics {
          contentDescription = action.label.rs
          role = Role.Button
        }
        .let { if (onClick != null) it.clickable(onClick) else it },
    contentAlignment = RemoteAlignment.Center,
  ) {
    if (water) {
      // The primary button is a little pool: still water while paused, waves and bubbles while
      // playing. A canvas under the icon, not a drawWithContent modifier: Android 16's platform
      // player recurses forever on DrawContent inside a modifier.
      WaterBackground(
        RemoteModifier.fillMaxSize(),
        level = 0.42f,
        bubbles = if (playing) 4 else 0,
        clock = if (playing) LocalSwimClock.current else 1.1f.rf,
        // The button's own size: the template draws this pool inside conditional layouts.
        sizePx = size.rdp.toPx(),
      )
    }
    WidgetIcon(action.icon, content)
  }
}

/**
 * [icon] in a 24dp canvas of its own, its paths scaled by the screen density.
 *
 * Not `painterRemoteVector`: that scales by the canvas's measured width, and the template draws
 * every button inside conditional layouts. Android 16's platform widget player evaluates a hidden
 * branch's drawing too, before that branch has a size, and the widget fails with "FloatExpression
 * ... len = 3". A canvas of its own rather than a painter background: a background painter is a
 * drawWithContent, whose DrawContent Android 16's player repaints forever
 * (yschimke/rc-players#776).
 */
@Composable
@RemoteComposable
private fun WidgetIcon(icon: ImageVector, color: RemoteColor) {
  val path = remember(icon) { icon.toRemotePath() }
  val density = 1.rdp.toPx()
  RemoteCanvas(RemoteModifier.size(IconDp.rdp)) {
    scale(density, density, RemoteOffset(0f, 0f)) {
      drawPath(path, RemotePaint { this.color = color })
    }
  }
}

/** The Material icons' viewport, and the size the buttons draw them at. */
private const val IconDp = 24

/**
 * [this] icon's paths as one path in its viewport's units (no group transforms: Material icons).
 */
private fun ImageVector.toRemotePath(): RemotePath {
  val out = RemotePath()
  fun append(nodes: List<PathNode>) {
    val path = PathParser().addPathNodes(nodes).toPath()
    for (segment in path.iterator(PathIterator.ConicEvaluation.AsQuadratics, 0.25f)) {
      val p = segment.points
      when (segment.type) {
        PathSegment.Type.Move -> out.moveTo(p[0], p[1])
        PathSegment.Type.Line -> out.lineTo(p[2], p[3])
        PathSegment.Type.Quadratic -> out.quadTo(p[2], p[3], p[4], p[5])
        PathSegment.Type.Cubic -> out.cubicTo(p[2], p[3], p[4], p[5], p[6], p[7])
        PathSegment.Type.Close -> out.close()
        else -> Unit
      }
    }
  }
  fun visit(group: VectorGroup) {
    for (node in group) {
      when (node) {
        is VectorGroup -> visit(node)
        is VectorPath -> append(node.pathData)
      }
    }
  }
  visit(root)
  return out
}

/** The widget's colour roles as remote colours: constants, or named values a template reads. */
class RemotePalette(
  val background: RemoteColor,
  val onBackground: RemoteColor,
  val supporting: RemoteColor,
  val tonal: RemoteColor,
  val onTonal: RemoteColor,
  val primary: RemoteColor,
  val onPrimary: RemoteColor,
) {
  companion object {
    fun of(colors: WidgetColors) =
      RemotePalette(
        background = colors.background.rc,
        onBackground = colors.onBackground.rc,
        supporting = colors.supporting.rc,
        tonal = colors.tonal.rc,
        onTonal = colors.onTonal.rc,
        primary = colors.primary.rc,
        onPrimary = colors.onPrimary.rc,
      )
  }
}

/** Icons on [WaterColors.Pool], whatever the system colour scheme. */
private val OnWater = Color(0xFF06243D)

internal val HeadphoneWidgetAction.icon: ImageVector
  get() =
    when (this) {
      HeadphoneWidgetAction.Previous -> Icons.Filled.SkipPrevious
      HeadphoneWidgetAction.Play -> Icons.Filled.PlayArrow
      HeadphoneWidgetAction.Pause -> Icons.Filled.Pause
      HeadphoneWidgetAction.Next -> Icons.Filled.SkipNext
      HeadphoneWidgetAction.SwitchToMp3 -> Icons.Filled.MusicNote
      HeadphoneWidgetAction.SwitchToBluetooth -> Icons.Filled.Bluetooth
    }

/**
 * The widget's colour roles. Captured on the device, so the launcher widget provider fills them
 * from the system's dynamic Material scheme for the current light or dark mode, and recaptures when
 * that changes; [Dark] and [Light] are the fallbacks without dynamic colour, and what previews
 * show.
 *
 * Shared by every Cadence Remote Compose launcher surface (headphones, podcast sync, cloud sync) so
 * light and dark home screens never get a fixed dark chrome card.
 */
data class WidgetColors(
  val background: Color,
  val onBackground: Color,
  val supporting: Color,
  val tonal: Color,
  val onTonal: Color,
  val primary: Color,
  val onPrimary: Color,
  /** Ready / complete accents (sync-ready, downloaded). */
  val tertiary: Color,
  /** Pending work accents (to prepare, to download). */
  val warning: Color,
) {
  /** Whether [background] reads as a dark surface — picks the matching swim palette. */
  val isDark: Boolean
    get() = background.luminance() < 0.5f

  companion object {
    fun from(scheme: ColorScheme) =
      WidgetColors(
        background = scheme.surfaceContainer,
        onBackground = scheme.onSurface,
        supporting = scheme.onSurfaceVariant,
        tonal = scheme.secondaryContainer,
        onTonal = scheme.onSecondaryContainer,
        primary = scheme.primary,
        onPrimary = scheme.onPrimary,
        tertiary = scheme.tertiary,
        warning = scheme.secondary,
      )

    val Dark = from(darkColorScheme())
    val Light = from(lightColorScheme())
  }
}
