@file:Suppress("RestrictedApi", "RestrictedApiAndroidX")

package ee.schimke.cadence.podcasts

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
import androidx.compose.remote.creation.compose.modifier.padding
import androidx.compose.remote.creation.compose.modifier.size
import androidx.compose.remote.creation.compose.shapes.RemoteCircleShape
import androidx.compose.remote.creation.compose.shapes.RemoteRoundedCornerShape
import androidx.compose.remote.creation.compose.state.rc
import androidx.compose.remote.creation.compose.state.rdp
import androidx.compose.remote.creation.compose.state.rs
import androidx.compose.remote.creation.compose.state.rsp
import androidx.compose.runtime.Composable
import ee.schimke.cadence.theme.CadencePalette

/**
 * The podcast sync widget as Remote Compose: whether the audio is ready to copy to the headphones,
 * and what preparation last reported. Drawn by the launcher itself (Android 16+), so it fills and
 * reflows with whatever slot the user resizes it to; [onTap] opens Cadence.
 */
@Composable
@RemoteComposable
internal fun PodcastSyncWidgetContent(ready: Boolean, status: String, onTap: Action? = null) {
  RemoteColumn(
    modifier =
      RemoteModifier.fillMaxSize()
        .let { if (onTap != null) it.clickable(onTap) else it }
        .clip(RemoteRoundedCornerShape(16.rdp))
        .background(CadencePalette.Grey20.rc)
        .padding(16.rdp),
    verticalArrangement = RemoteArrangement.spacedBy(6.rdp),
  ) {
    RemoteRow(
      horizontalArrangement = RemoteArrangement.spacedBy(8.rdp),
      verticalAlignment = RemoteAlignment.CenterVertically,
    ) {
      val dot = if (ready) CadencePalette.Aqua80 else CadencePalette.GreyVariant80
      RemoteBox(RemoteModifier.size(10.rdp).clip(RemoteCircleShape).background(dot.rc))
      RemoteText(
        (if (ready) "Ready to copy to USB" else "Audio not ready").rs,
        color = CadencePalette.Grey90.rc,
        fontSize = 16.rsp,
        maxLines = 1,
      )
    }
    RemoteText(
      status.rs,
      color = CadencePalette.GreyVariant80.rc,
      fontSize = 14.rsp,
      maxLines = 2,
    )
    RemoteText("Cadence".rs, color = CadencePalette.Blue80.rc, fontSize = 12.rsp)
  }
}
