@file:Suppress("RestrictedApi", "RestrictedApiAndroidX")

package ee.schimke.cadence.widgets

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
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
import androidx.compose.remote.creation.compose.modifier.fillMaxSize
import androidx.compose.remote.creation.compose.modifier.fillMaxWidth
import androidx.compose.remote.creation.compose.modifier.padding
import androidx.compose.remote.creation.compose.modifier.size
import androidx.compose.remote.creation.compose.shapes.RemoteRoundedCornerShape
import androidx.compose.remote.creation.compose.state.rc
import androidx.compose.remote.creation.compose.state.rdp
import androidx.compose.remote.creation.compose.state.rs
import androidx.compose.remote.creation.compose.state.rsp
import androidx.compose.remote.creation.compose.vector.painterRemoteVector
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
  compact: Boolean,
  onOpen: Action?,
  onAction: @Composable (HeadphoneWidgetAction) -> Action?,
) {
  RemoteColumn(
    modifier =
      RemoteModifier.fillMaxSize()
        .clip(RemoteRoundedCornerShape(16.rdp))
        .background(WidgetColors.background.rc)
        .padding(12.rdp),
    verticalArrangement = RemoteArrangement.SpaceBetween,
  ) {
    RemoteColumn(
      modifier =
        RemoteModifier.fillMaxWidth().let { if (onOpen != null) it.clickable(onOpen) else it },
      verticalArrangement = RemoteArrangement.spacedBy(2.rdp),
    ) {
      RemoteText(
        state.headline.rs,
        color = WidgetColors.onBackground.rc,
        fontSize = 15.rsp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      RemoteText(
        state.nowPlaying.rs,
        color = WidgetColors.supporting.rc,
        fontSize = 13.rsp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      if (!compact) {
        state.settingsLine?.let {
          RemoteText(
            it.rs,
            color = WidgetColors.accent.rc,
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
      state.transport.forEach { action ->
        WidgetIconButton(
          icon = action.icon,
          primary = action == HeadphoneWidgetAction.Play || action == HeadphoneWidgetAction.Pause,
          action = onAction(action),
        )
      }
      if (!compact) {
        state.modeSwitch?.let { action ->
          WidgetIconButton(icon = action.icon, primary = false, action = onAction(action))
        }
      }
    }
  }
}

@Composable
@RemoteComposable
private fun WidgetIconButton(icon: ImageVector, primary: Boolean, action: Action?) {
  val container = if (primary) WidgetColors.primary else WidgetColors.tonal
  val content = if (primary) WidgetColors.onPrimary else WidgetColors.onBackground
  RemoteBox(
    modifier =
      RemoteModifier.size(44.rdp)
        .clip(RemoteRoundedCornerShape(22.rdp))
        .background(container.rc)
        .let { if (action != null) it.clickable(action) else it },
    contentAlignment = RemoteAlignment.Center,
  ) {
    RemoteBox(RemoteModifier.size(24.rdp).background(painterRemoteVector(icon, content.rc)))
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

/** Same dark tonal palette as the Wear widgets, so both surfaces read as one product. */
internal object WidgetColors {
  val background = Color(0xFF17161E)
  val onBackground = Color(0xFFE6E1E9)
  val supporting = Color(0xFFCBC3D7)
  val tonal = Color(0xFF343044)
  val primary = Color(0xFFD7BFFF)
  val onPrimary = Color(0xFF3B2A5C)
  val accent = Color(0xFF9ED1FF)
}
