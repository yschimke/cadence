package ee.schimke.cadence.notifications

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import ee.schimke.composeai.rccreation.compose.action.RemoteAction
import ee.schimke.composeai.rccreation.compose.layout.RemoteAlignment
import ee.schimke.composeai.rccreation.compose.layout.RemoteArrangement
import ee.schimke.composeai.rccreation.compose.layout.RemoteBox
import ee.schimke.composeai.rccreation.compose.layout.RemoteColumn
import ee.schimke.composeai.rccreation.compose.layout.RemoteComposable
import ee.schimke.composeai.rccreation.compose.layout.RemoteRow
import ee.schimke.composeai.rccreation.compose.layout.RemoteStateLayout
import ee.schimke.composeai.rccreation.compose.layout.RemoteText
import ee.schimke.composeai.rccreation.compose.modifier.RemoteModifier
import ee.schimke.composeai.rccreation.compose.modifier.background
import ee.schimke.composeai.rccreation.compose.modifier.clickable
import ee.schimke.composeai.rccreation.compose.modifier.clip
import ee.schimke.composeai.rccreation.compose.modifier.contentDescription
import ee.schimke.composeai.rccreation.compose.modifier.fillMaxSize
import ee.schimke.composeai.rccreation.compose.modifier.fillMaxWidth
import ee.schimke.composeai.rccreation.compose.modifier.height
import ee.schimke.composeai.rccreation.compose.modifier.padding
import ee.schimke.composeai.rccreation.compose.modifier.role
import ee.schimke.composeai.rccreation.compose.modifier.semantics
import ee.schimke.composeai.rccreation.compose.modifier.size
import ee.schimke.composeai.rccreation.compose.modifier.width
import ee.schimke.composeai.rccreation.compose.shapes.RemoteRoundedCornerShape
import ee.schimke.composeai.rccreation.compose.state.RemoteBoolean
import ee.schimke.composeai.rccreation.compose.state.RemoteColor
import ee.schimke.composeai.rccreation.compose.state.RemoteFloat
import ee.schimke.composeai.rccreation.compose.state.RemoteInt
import ee.schimke.composeai.rccreation.compose.state.RemoteState
import ee.schimke.composeai.rccreation.compose.state.RemoteString
import ee.schimke.composeai.rccreation.compose.state.rdp
import ee.schimke.composeai.rccreation.compose.state.rf
import ee.schimke.composeai.rccreation.compose.state.rs
import ee.schimke.composeai.rccreation.compose.state.rsp
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import ee.schimke.composeai.rccreation.compose.modifier.RemoteRole
import ee.schimke.composeai.rccreation.compose.text.RemoteFontWeight
import ee.schimke.composeai.rccreation.compose.text.RemoteTextOverflow
import ee.schimke.cadence.swim.SwimLaneProgress
import ee.schimke.cadence.widgets.WidgetColors
import ee.schimke.cadence.widgets.WidgetIcon
import ee.schimke.cadence.widgets.cadenceClickable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Every Cadence notification's expanded body as one Livery template (contract [CONTRACT]): an icon
 * for what is happening, the title and text, an optional progress bar and an optional Cancel
 * button. The values are named; [NotificationBody] fills them per post.
 */
object NotificationTemplate {
  const val CONTRACT = "cadence/notification@1"
  const val CONTRACT_MINOR = 0

  const val TITLE = "title"
  const val TEXT = "text"
  const val ICON = "icon"
  const val HAS_PROGRESS = "has_progress"
  const val PROGRESS = "progress"
  const val HAS_CANCEL = "has_cancel"

  /** The taps a template emits. */
  const val OPEN = "open"
  const val CANCEL = "cancel"

  /** Template sizes: the shade's width, and up to Android's 256dp cap on a custom body. */
  const val MIN_WIDTH_DP = 200
  const val MIN_HEIGHT_DP = 64
  const val MAX_HEIGHT_DP = 256

  /** The progress bar: a pool lane tall enough to see its swimmer. */
  const val LANE_HEIGHT_DP = 24

  /**
   * The size the template is captured at. It fills the shade's width but only the height it
   * draws: Android 17's player gives a body all the height it is offered unless it asks for less.
   */
  const val CAPTURE_WIDTH_DP = 360
  const val CAPTURE_HEIGHT_DP = 128

  /** Colour roles, as `theme.*` inputs written in per post: text over the system's shade. */
  val COLORS: Map<String, (WidgetColors) -> Color> =
    linkedMapOf(
      "theme.on_background" to { it.onBackground },
      "theme.supporting" to { it.supporting },
      "theme.tonal" to { it.tonal },
      "theme.on_tonal" to { it.onTonal },
      "theme.primary" to { it.primary },
      "theme.on_primary" to { it.onPrimary },
      "theme.tertiary" to { it.tertiary },
    )

  fun data(body: NotificationBody, colors: WidgetColors): JsonObject = buildJsonObject {
    put(TITLE, body.title.take(80))
    put(TEXT, body.text.take(240))
    put(ICON, body.icon.ordinal)
    put(HAS_PROGRESS, body.progress != null)
    put(PROGRESS, (body.progress ?: 0f).coerceIn(0f, 1f))
    put(HAS_CANCEL, body.cancelIntent != null)
    COLORS.forEach { (role, of) -> put(role, "#%08X".format(of(colors).toArgb())) }
  }
}

/** What a notification is about: picks its icon and accent. */
enum class NotificationIcon(val vector: ImageVector, val label: String) {
  Download(Icons.Filled.Download, "Downloading"),
  Process(Icons.Filled.GraphicEq, "Processing"),
  Sync(Icons.Filled.Headphones, "Headphones"),
  Done(Icons.Filled.CheckCircle, "Done"),
  Error(Icons.Filled.Error, "Problem"),
}

/**
 * The template: [onOpen] on the body, [onCancel] on its Cancel button. The icon is a conditional
 * layout over a named value, and the progress bar and Cancel are sized by named flags, so one
 * document serves every notification.
 *
 * [sample] and [colors] are only the named values' defaults, for previews: SystemUI is handed the
 * values written in by instantiation. The captured template leaves them blank.
 */
@Composable
@RemoteComposable
fun NotificationTemplateContent(
  onOpen: RemoteAction?,
  onCancel: RemoteAction?,
  sample: NotificationBody? = null,
  sampleCancel: Boolean = sample?.cancelIntent != null,
  colors: WidgetColors = WidgetColors.Dark,
) {
  val t = NotificationTemplate
  val user = RemoteState.Domain.User
  fun color(role: String) =
    RemoteColor.createNamedRemoteColor(role, t.COLORS.getValue(role)(colors).toArgb(), user)
  val title = RemoteString.createNamedRemoteString(t.TITLE, sample?.title.orEmpty(), user)
  val text = RemoteString.createNamedRemoteString(t.TEXT, sample?.text.orEmpty(), user)
  val icon = RemoteInt.createNamedRemoteInt(t.ICON, sample?.icon?.ordinal ?: 0, user)
  val hasProgress =
    RemoteBoolean.createNamedRemoteBoolean(t.HAS_PROGRESS, sample?.progress != null, user)
  val progress = RemoteFloat.createNamedRemoteFloat(t.PROGRESS, sample?.progress ?: 0f, user)
  val hasCancel =
    RemoteBoolean.createNamedRemoteBoolean(t.HAS_CANCEL, sampleCancel, user)
  val onBackground = color("theme.on_background")
  val supporting = color("theme.supporting")
  val tonal = color("theme.tonal")
  val onTonal = color("theme.on_tonal")
  val primary = color("theme.primary")
  val onPrimary = color("theme.on_primary")
  val tertiary = color("theme.tertiary")
  RemoteColumn(
    modifier =
      RemoteModifier.fillMaxWidth()
        .padding(horizontal = 16.rdp, vertical = 12.rdp)
        .cadenceClickable(onOpen),
  ) {
    RemoteRow(
      modifier = RemoteModifier.fillMaxWidth(),
      verticalAlignment = RemoteAlignment.CenterVertically,
    ) {
      val entries = NotificationIcon.entries
      RemoteStateLayout(icon, *entries.map { it.ordinal }.toIntArray()) { index: Int ->
        val which = entries[index]
        val done = which == NotificationIcon.Done
        val error = which == NotificationIcon.Error
        RemoteBox(
          modifier =
            RemoteModifier.size(40.rdp)
              .clip(RemoteRoundedCornerShape(20.rdp))
              .background(if (done) tertiary else if (error) primary else tonal)
              .semantics { contentDescription = which.label.rs },
          contentAlignment = RemoteAlignment.Center,
        ) {
          WidgetIcon(which.vector, if (done || error) onPrimary else onTonal)
        }
      }
      RemoteBox(RemoteModifier.width(16.rdp))
      RemoteColumn(
        modifier = RemoteModifier.weight(1f.rf),
        verticalArrangement = RemoteArrangement.spacedBy(2.rdp),
      ) {
        RemoteText(
          title,
          color = onBackground,
          fontSize = 15.rsp,
          fontWeight = RemoteFontWeight.Medium,
          maxLines = 1,
          overflow = RemoteTextOverflow.Ellipsis,
        )
        RemoteText(
          text,
          color = supporting,
          fontSize = 13.rsp,
          maxLines = 3,
          overflow = RemoteTextOverflow.Ellipsis,
        )
      }
    }
    // Present or absent by the named flags, as a height that is 0 when absent: instantiation writes
    // each flag in as a constant, which makes the height a literal. Not a conditional layout, which
    // AndroidX's player and the platform's lay out over the content above it in a column, nor a
    // visibility modifier, which the platform's player ignores. Each brings its own gap above.
    // The swimmer's one lap: a canvas draws it from the value, where a box could only be sized by
    // a constant fraction. The box is sized by the flag, like Cancel; inside it a conditional layout
    // picks the lane or nothing, so a body without a bar holds no lane at all once instantiated
    // (the layout collapses), and nothing animates. Placed in a box, the conditional layout can't be
    // laid out over the title.
    RemoteBox(
      RemoteModifier.padding(top = hasProgress.select(10.rdp, 0.rdp))
        .fillMaxWidth()
        .height(hasProgress.select(t.LANE_HEIGHT_DP.rdp, 0.rdp))
        .clip(RemoteRoundedCornerShape((t.LANE_HEIGHT_DP / 2).rdp)),
    ) {
      RemoteStateLayout(hasProgress.toRemoteInt(), 0, 1, modifier = RemoteModifier.fillMaxSize()) {
        shown: Int ->
        if (shown == 1) {
          SwimLaneProgress(
            progress,
            RemoteModifier.fillMaxSize(),
            heightPx = t.LANE_HEIGHT_DP.rdp.toPx(),
          )
        } else {
          RemoteBox(RemoteModifier.fillMaxSize())
        }
      }
    }
    RemoteRow(
      modifier = RemoteModifier.padding(top = hasCancel.select(10.rdp, 0.rdp)).fillMaxWidth(),
      horizontalArrangement = RemoteArrangement.End,
    ) {
      // Clipped to its height, so at 0 its label isn't drawn either.
      RemoteBox(
        modifier =
          RemoteModifier.height(hasCancel.select(36.rdp, 0.rdp))
            .clip(RemoteRoundedCornerShape(18.rdp))
            .background(tonal)
            .cadenceClickable(onCancel, "Cancel".rs)
            .padding(horizontal = 16.rdp),
        contentAlignment = RemoteAlignment.Center,
      ) {
        RemoteText("Cancel".rs, color = onTonal, fontSize = 14.rsp, fontWeight = RemoteFontWeight.Medium)
      }
    }
  }
}
