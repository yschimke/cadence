package ee.schimke.cadence.widgets

import ee.schimke.composeai.rccreation.compose.action.RemoteAction
import ee.schimke.composeai.rccreation.compose.modifier.RemoteModifier
import ee.schimke.composeai.rccreation.compose.modifier.RemoteRole
import ee.schimke.composeai.rccreation.compose.modifier.clickable
import ee.schimke.composeai.rccreation.compose.modifier.contentDescription
import ee.schimke.composeai.rccreation.compose.modifier.role
import ee.schimke.composeai.rccreation.compose.modifier.semantics
import ee.schimke.composeai.rccreation.compose.state.RemoteString

/**
 * Every tap in Cadence's Remote Compose content goes through here, so a tapped region always reads
 * as a button to accessibility services. rc-players' `clickable` writes no accessibility semantics
 * of its own (AndroidX's alpha23 wrote role = button for every click); this restores that, with an
 * optional [label] for what the region does. A null [action] (a preview, or a template with nothing
 * to send) adds no click and no role, but still gives the label.
 */
fun RemoteModifier.cadenceClickable(
  action: RemoteAction?,
  label: RemoteString? = null,
): RemoteModifier {
  val described =
    if (action == null && label == null) this
    else
      semantics {
        if (label != null) contentDescription = label
        if (action != null) role = RemoteRole.Button
      }
  return if (action != null) described.clickable(action) else described
}
