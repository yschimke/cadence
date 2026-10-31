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
import androidx.compose.remote.creation.compose.action.Action
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
import androidx.compose.remote.creation.compose.state.rc
import androidx.compose.remote.creation.compose.state.rdp
import androidx.compose.remote.creation.compose.state.rs
import androidx.compose.remote.creation.compose.state.rsp
import androidx.compose.remote.creation.compose.vector.painterRemoteVector
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow

/**
 * Phone counterpart of the Wear "Mode & controls" widget: the headset's mode, what is playing, the
 * transport, and a mode switch. Drawn by the launcher (Android 16+) so it reflows with the slot;
 * [HeadphoneControlsWidget] provides a `RemoteViews` fallback for older launchers.
 *
 * [onAction] returns the host action that fires a [HeadphoneWidgetAction]; null (previews) leaves
 * the control inert.
 */
@Composable
@RemoteComposable
internal fun HeadphoneControlsWidgetContent(
  state: HeadphoneWidgetState,
  layout: HeadphoneWidgetLayout,
  colors: WidgetColors,
  onOpen: Action?,
  onAction: @Composable (HeadphoneWidgetAction) -> Action?,
) {
  // No outer clip: the launcher applies its own system corner radius to the widget.
  val root =
    RemoteModifier.fillMaxSize()
      .background(colors.background.rc)
      .padding(HeadphoneWidgetLayout.PaddingDp.rdp)
  if (layout.singleRow) {
    RemoteRow(
      modifier = root,
      horizontalArrangement = RemoteArrangement.spacedBy(HeadphoneWidgetLayout.ButtonGapDp.rdp),
      verticalAlignment = RemoteAlignment.CenterVertically,
    ) {
      if (layout.showHeadline) {
        // The open-app tap target spans the row's 48dp height, not just the text line.
        RemoteColumn(
          modifier =
            RemoteModifier.width(layout.headlineWidthDp.rdp)
              .height(HeadphoneWidgetLayout.ButtonDp.rdp)
              .openOnTap(onOpen),
          verticalArrangement = RemoteArrangement.Center,
        ) {
          Headline(if (layout.shortHeadline) state.shortHeadline else state.headline, colors)
        }
      }
      Buttons(state, layout, colors, onAction)
    }
    return
  }
  RemoteColumn(modifier = root, verticalArrangement = RemoteArrangement.SpaceBetween) {
    RemoteColumn(
      modifier =
        RemoteModifier.fillMaxWidth()
          // Remote heightIn needs a real maximum; no widget is this tall.
          .heightIn(HeadphoneWidgetLayout.ButtonDp.rdp, 1_000.rdp)
          .openOnTap(onOpen),
      verticalArrangement = RemoteArrangement.spacedBy(2.rdp),
    ) {
      Headline(state.headline, colors)
      if (layout.showNowPlaying) {
        RemoteText(
          state.nowPlaying.rs,
          color = colors.supporting.rc,
          fontSize = 13.rsp,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      if (layout.showSettings) {
        state.settingsLine?.let {
          RemoteText(
            it.rs,
            color = colors.accent.rc,
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
      Buttons(state, layout, colors, onAction)
    }
  }
}

@Composable
@RemoteComposable
private fun Headline(text: String, colors: WidgetColors) {
  RemoteText(
    text.rs,
    color = colors.onBackground.rc,
    fontSize = 15.rsp,
    fontWeight = FontWeight.Medium,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
  )
}

@Composable
@RemoteComposable
private fun Buttons(
  state: HeadphoneWidgetState,
  layout: HeadphoneWidgetLayout,
  colors: WidgetColors,
  onAction: @Composable (HeadphoneWidgetAction) -> Action?,
) {
  layout.transport.forEach { action ->
    WidgetIconButton(
      action = action,
      primary = action == HeadphoneWidgetAction.Play || action == HeadphoneWidgetAction.Pause,
      colors = colors,
      onClick = onAction(action),
    )
  }
  if (layout.showModeSwitch) {
    state.modeSwitch?.let {
      WidgetIconButton(it, primary = false, colors = colors, onClick = onAction(it))
    }
  }
}

private fun RemoteModifier.openOnTap(onOpen: Action?): RemoteModifier =
  if (onOpen != null) clickable(onOpen) else this

@Composable
@RemoteComposable
private fun WidgetIconButton(
  action: HeadphoneWidgetAction,
  primary: Boolean,
  colors: WidgetColors,
  onClick: Action?,
) {
  val container = if (primary) colors.primary else colors.tonal
  val content = if (primary) colors.onPrimary else colors.onTonal
  val size = HeadphoneWidgetLayout.ButtonDp
  RemoteBox(
    modifier =
      RemoteModifier.size(size.rdp)
        .clip(RemoteRoundedCornerShape((size / 2).rdp))
        .background(container.rc)
        .semantics {
          contentDescription = action.label.rs
          role = Role.Button
        }
        .let { if (onClick != null) it.clickable(onClick) else it },
    contentAlignment = RemoteAlignment.Center,
  ) {
    RemoteBox(RemoteModifier.size(24.rdp).background(painterRemoteVector(action.icon, content.rc)))
  }
}

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
 * The widget's colour roles. Captured on the device, so [HeadphoneControlsWidget] fills them from
 * the system's dynamic Material scheme for the current light or dark mode, and recaptures when that
 * changes; [Dark] and [Light] are the fallbacks without dynamic colour, and what previews show.
 */
internal data class WidgetColors(
  val background: Color,
  val onBackground: Color,
  val supporting: Color,
  val tonal: Color,
  val onTonal: Color,
  val primary: Color,
  val onPrimary: Color,
  val accent: Color,
) {
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
        accent = scheme.tertiary,
      )

    val Dark = from(darkColorScheme())
    val Light = from(lightColorScheme())
  }
}
