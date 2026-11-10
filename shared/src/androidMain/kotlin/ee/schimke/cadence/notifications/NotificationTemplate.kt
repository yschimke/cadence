@file:Suppress("RestrictedApi", "RestrictedApiAndroidX")

package ee.schimke.cadence.notifications

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.remote.creation.compose.action.RemoteAction
import androidx.compose.remote.creation.compose.layout.RemoteAlignment
import androidx.compose.remote.creation.compose.layout.RemoteArrangement
import androidx.compose.remote.creation.compose.layout.RemoteBox
import androidx.compose.remote.creation.compose.layout.RemoteCanvas
import androidx.compose.remote.creation.compose.layout.RemoteColumn
import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.remote.creation.compose.layout.RemoteOffset
import androidx.compose.remote.creation.compose.layout.RemoteRow
import androidx.compose.remote.creation.compose.layout.RemoteSize
import androidx.compose.remote.creation.compose.layout.RemoteStateLayout
import androidx.compose.remote.creation.compose.layout.RemoteText
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.background
import androidx.compose.remote.creation.compose.modifier.clickable
import androidx.compose.remote.creation.compose.modifier.clip
import androidx.compose.remote.creation.compose.modifier.contentDescription
import androidx.compose.remote.creation.compose.modifier.fillMaxSize
import androidx.compose.remote.creation.compose.modifier.fillMaxWidth
import androidx.compose.remote.creation.compose.modifier.height
import androidx.compose.remote.creation.compose.modifier.padding
import androidx.compose.remote.creation.compose.modifier.role
import androidx.compose.remote.creation.compose.modifier.semantics
import androidx.compose.remote.creation.compose.modifier.size
import androidx.compose.remote.creation.compose.modifier.width
import androidx.compose.remote.creation.compose.shapes.RemoteRoundedCornerShape
import androidx.compose.remote.creation.compose.state.RemoteBoolean
import androidx.compose.remote.creation.compose.state.RemoteColor
import androidx.compose.remote.creation.compose.state.RemoteFloat
import androidx.compose.remote.creation.compose.state.RemoteInt
import androidx.compose.remote.creation.compose.state.RemotePaint
import androidx.compose.remote.creation.compose.state.RemoteState
import androidx.compose.remote.creation.compose.state.RemoteString
import androidx.compose.remote.creation.compose.state.rdp
import androidx.compose.remote.creation.compose.state.rs
import androidx.compose.remote.creation.compose.state.rsp
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import ee.schimke.cadence.widgets.WidgetColors
import ee.schimke.cadence.widgets.WidgetIcon
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

  /** The size the template is captured at; it fills whatever the shade gives it. */
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
      RemoteModifier.fillMaxSize().padding(horizontal = 16.rdp, vertical = 12.rdp).let {
        if (onOpen != null) it.clickable(onOpen) else it
      },
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
        modifier = RemoteModifier.weight(1f),
        verticalArrangement = RemoteArrangement.spacedBy(2.rdp),
      ) {
        RemoteText(
          title,
          color = onBackground,
          fontSize = 15.rsp,
          fontWeight = FontWeight.Medium,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        RemoteText(
          text,
          color = supporting,
          fontSize = 13.rsp,
          maxLines = 3,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
    // Present or absent by the named flags, as a height that is 0 when absent: instantiation writes
    // each flag in as a constant, which makes the height a literal. Not a conditional layout, which
    // AndroidX's player and the platform's lay out over the content above it in a column, nor a
    // visibility modifier, which the platform's player ignores. Each brings its own gap above.
    val barGap = hasProgress.select(10.rdp, 0.rdp)
    val barHeight = hasProgress.select(6.rdp, 0.rdp)
    // A canvas, not fillMaxWidth(progress): players size a box by a constant fraction only.
    RemoteCanvas(
      RemoteModifier.padding(top = barGap)
        .fillMaxWidth()
        .height(barHeight)
        .clip(RemoteRoundedCornerShape(3.rdp)),
    ) {
      drawRect(RemotePaint { color = tonal }, RemoteOffset(0f, 0f), RemoteSize(width, height))
      drawRect(
        RemotePaint { color = primary },
        RemoteOffset(0f, 0f),
        RemoteSize(width * progress, height),
      )
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
            .semantics {
              contentDescription = "Cancel".rs
              role = Role.Button
            }
            .let { if (onCancel != null) it.clickable(onCancel) else it }
            .padding(horizontal = 16.rdp),
        contentAlignment = RemoteAlignment.Center,
      ) {
        RemoteText("Cancel".rs, color = onTonal, fontSize = 14.rsp, fontWeight = FontWeight.Medium)
      }
    }
  }
}
