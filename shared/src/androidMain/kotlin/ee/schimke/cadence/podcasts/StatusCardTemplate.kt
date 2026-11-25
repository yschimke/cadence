package ee.schimke.cadence.podcasts

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import ee.schimke.cadence.platform.DEFAULT_CARD_DESIGN
import ee.schimke.cadence.swim.SwimmingPool
import ee.schimke.cadence.swim.WaterColors
import ee.schimke.cadence.widgets.WidgetColors
import ee.schimke.cadence.widgets.WidgetIcon
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
import ee.schimke.composeai.rccreation.compose.modifier.RemoteRole
import ee.schimke.composeai.rccreation.compose.modifier.background
import ee.schimke.composeai.rccreation.compose.modifier.clickable
import ee.schimke.composeai.rccreation.compose.modifier.clip
import ee.schimke.composeai.rccreation.compose.modifier.contentDescription
import ee.schimke.composeai.rccreation.compose.modifier.fillMaxSize
import ee.schimke.composeai.rccreation.compose.modifier.fillMaxWidth
import ee.schimke.composeai.rccreation.compose.modifier.height
import ee.schimke.composeai.rccreation.compose.modifier.heightIn
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
import ee.schimke.composeai.rccreation.compose.state.ri
import ee.schimke.composeai.rccreation.compose.state.rsp
import ee.schimke.composeai.rccreation.compose.text.RemoteFontWeight
import ee.schimke.composeai.rccreation.compose.text.RemoteTextOverflow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Home's status card as a Livery template (contract [CONTRACT]): one values-free document per
 * design, whose every word, flag, colour and the pool's state are named values in the User domain.
 *
 * Home plays a design's template as it is and writes [StatusCardValues] into the player
 * ([StatusCardValues.names]), so the card changes, text and swimmer alike, without a new document.
 * A row that comes and goes is a conditional layout of its own on one flag, not one branch per
 * combination, which keeps a design's document small.
 *
 * Swimming ([SWIMMING_DESIGN]) is the first design; another (runners) draws the same contract with
 * its own scene.
 */
object StatusCardTemplate {
  const val CONTRACT = "cadence/status-card@1"

  /** The contract's id without its major, as Livery's queries and design lists take it. */
  const val CONTRACT_ID = "cadence/status-card"

  /** The minor of [CONTRACT] this app implements (`composeApp/livery/contracts/status-card.json`). */
  const val CONTRACT_MINOR = 0

  /** The swimming pool design, and the one drawn until the user picks another. */
  const val SWIMMING_DESIGN = DEFAULT_CARD_DESIGN

  const val HEADLINE = "headline"
  const val HAS_HEADLINE = "has_headline"
  const val ATTENTION = "attention"
  const val HEADPHONES_COUNT = "headphones_count"
  const val HAS_HEADPHONES_COUNT = "has_headphones_count"
  const val HEADPHONES_TEXT = "headphones_text"
  const val PHONE_COUNT = "phone_count"
  const val HAS_PHONE_COUNT = "has_phone_count"
  const val PHONE_TEXT = "phone_text"
  const val READY = "ready"
  const val BATTERY = "battery"
  const val SOURCE = "source"
  const val HAS_SOURCE = "has_source"
  const val CHANGES = "changes"
  const val HAS_CHANGES = "has_changes"
  const val CHANGES_EXPANDED = "changes_expanded"
  const val CHANGE_LINES = "change_lines"
  const val REFRESH = "refresh"
  const val AUDIO_CONTROLS = "audio_controls"
  const val SWIMMING = "swimming"
  const val STANDING = "standing"
  const val PROGRESS = "progress"
  const val DARK = "dark"

  /** The button slots beside the status, in order: [BUTTON_SLOTS] of them. */
  const val BUTTON_SLOTS = 3

  fun hasButton(slot: Int) = "has_button_$slot"

  fun buttonLabel(slot: Int) = "button_${slot}_label"

  fun buttonPrimary(slot: Int) = "button_${slot}_primary"

  /**
   * The slot's icon, drawn instead of its label (which is what is read out): 0 shows the label,
   * otherwise one more than its index in [StatusButtonIcons].
   */
  fun buttonIcon(slot: Int) = "button_${slot}_icon"

  /** The action a button slot's tap fires; Home maps it back to the button in that slot. */
  fun buttonAction(slot: Int) = "button_$slot"

  /** Colour roles, as `theme.*` inputs: Home's Material colours, written in as they change. */
  val COLORS: Map<String, (WidgetColors) -> Color> =
    linkedMapOf(
      "theme.background" to { it.background },
      "theme.on_background" to { it.onBackground },
      "theme.supporting" to { it.supporting },
      "theme.primary" to { it.primary },
      "theme.on_primary" to { it.onPrimary },
      "theme.tertiary" to { it.tertiary },
      "theme.warning" to { it.warning },
    )

  /** A template's size range: the card fills whatever height Home gives it. */
  const val MIN_WIDTH_DP = 280
  const val MIN_HEIGHT_DP = 120
}

/**
 * Everything the status card shows, decided by Home: what [StatusCardTemplate]'s named values hold.
 * [buttons] fill the slots in order; [colors] are written as the `theme.*` roles.
 */
data class StatusCardValues(
  val headline: String?,
  val attention: Boolean,
  val copied: String,
  val available: String,
  val ready: Boolean,
  val battery: String?,
  val source: String?,
  val extras: StatusCardExtras,
  val pool: StatusCardPool,
  val colors: WidgetColors,
) {
  /** Every named value by name: a [String], [Boolean], [Int], [Float] or [Color]. */
  val names: Map<String, Any>
    get() = buildMap {
      val t = StatusCardTemplate
      put(t.HEADLINE, headline.orEmpty())
      put(t.HAS_HEADLINE, headline != null)
      put(t.ATTENTION, attention)
      val headphones = MetricText.of(copied, "on headphones")
      put(t.HEADPHONES_COUNT, headphones.count.orEmpty())
      put(t.HAS_HEADPHONES_COUNT, headphones.count != null)
      put(t.HEADPHONES_TEXT, headphones.text)
      val phone = MetricText.of(available, if (ready) "ready" else "to prepare")
      put(t.PHONE_COUNT, phone.count.orEmpty())
      put(t.HAS_PHONE_COUNT, phone.count != null)
      put(t.PHONE_TEXT, phone.text)
      put(t.READY, ready)
      // "50% · ~3h MP3 · ~4h 30m BT" reads as "50% · ~3h MP3": the charge and one estimate.
      put(t.BATTERY, battery?.split(" · ")?.take(2)?.joinToString(" · ") ?: "Battery unknown")
      put(t.SOURCE, source.orEmpty())
      put(t.HAS_SOURCE, source != null)
      put(t.CHANGES, extras.changes?.let { "What will change · $it" }.orEmpty())
      put(t.HAS_CHANGES, extras.changes != null)
      put(t.CHANGES_EXPANDED, extras.changeLines.isNotEmpty())
      put(t.CHANGE_LINES, extras.changeLines.joinToString("\n"))
      put(t.REFRESH, extras.refresh)
      put(t.AUDIO_CONTROLS, extras.audioControls)
      for (slot in 1..t.BUTTON_SLOTS) {
        val button = extras.buttons.getOrNull(slot - 1)
        put(t.hasButton(slot), button != null)
        put(t.buttonLabel(slot), button?.label.orEmpty())
        put(t.buttonPrimary(slot), button?.primary == true)
        put(
          t.buttonIcon(slot),
          button?.let { b -> StatusButtonIcons.indexOfFirst { b.id in it.first } + 1 } ?: 0,
        )
      }
      put(t.SWIMMING, pool.swimming)
      put(t.STANDING, pool.standing)
      put(t.PROGRESS, pool.progress)
      put(t.DARK, colors.isDark)
      t.COLORS.forEach { (role, of) -> put(role, of(colors)) }
    }

  /** [names] as the contract's data, colours as `#AARRGGBB`. */
  fun data(): JsonObject = buildJsonObject {
    names.forEach { (name, value) ->
      when (value) {
        is String -> put(name, value)
        is Boolean -> put(name, value)
        is Int -> put(name, value)
        is Float -> put(name, value)
        is Color -> put(name, "#%08X".format(value.toArgb()))
      }
    }
  }

  /** The button whose slot fired [action], if it is a slot's. */
  fun buttonFor(action: String): StatusCardButton? =
    (1..StatusCardTemplate.BUTTON_SLOTS)
      .firstOrNull { StatusCardTemplate.buttonAction(it) == action }
      ?.let { extras.buttons.getOrNull(it - 1) }
}

/**
 * A count line's parts: "Last copy: 3 eps · 138m · 0 marked" is the count "3" and "on headphones ·
 * 2h 18m" (none played left out); a line without a count is shown as it is.
 */
internal data class MetricText(val count: String?, val text: String) {
  companion object {
    fun of(line: String, label: String): MetricText {
      val detail = line.substringAfter(": ", "")
      val count = detail.substringBefore(" eps").toIntOrNull() ?: return MetricText(null, line)
      val remainder =
        detail
          .substringAfter(" eps", "")
          .split(" · ")
          .filter { it.isNotBlank() && it != "0 marked" }
          .joinToString("") { " · " + compactMinutes(it).replace(" marked", " played") }
      return MetricText(count.toString(), "$label$remainder")
    }
  }
}

/** The card's colour roles as named remote colours ([WidgetColors.Dark] until written). */
private class CardPalette(named: (String) -> RemoteColor) {
  val background = named("theme.background")
  val onBackground = named("theme.on_background")
  val supporting = named("theme.supporting")
  val primary = named("theme.primary")
  val onPrimary = named("theme.on_primary")
  val tertiary = named("theme.tertiary")
  val warning = named("theme.warning")
}

/**
 * The swimming design of [StatusCardTemplate]: Home's card laid out as [PodcastSyncWidgetContent]
 * lays it out at scale 1 with its side column, every value named. [onAction] gives the host action
 * each tap fires, by the contract's action name.
 */
@Composable
@RemoteComposable
fun StatusCardTemplateContent(onAction: @Composable (String) -> RemoteAction?) {
  val t = StatusCardTemplate
  val user = RemoteState.Domain.User
  fun text(name: String) = RemoteString.createNamedRemoteString(name, "", user)
  fun flag(name: String) = RemoteBoolean.createNamedRemoteBoolean(name, false, user)
  val palette = CardPalette { role ->
    RemoteColor.createNamedRemoteColor(role, t.COLORS.getValue(role)(WidgetColors.Dark).toArgb(), user)
  }
  val headline = text(t.HEADLINE)
  RemoteBox(
    modifier =
      RemoteModifier.fillMaxSize()
        .semantics { contentDescription = headline }
        .background(palette.background),
  ) {
    IconDefinitions()
    RemoteColumn(
      modifier = RemoteModifier.fillMaxSize(),
      verticalArrangement = RemoteArrangement.SpaceBetween,
    ) {
      RemoteRow(
        modifier = RemoteModifier.fillMaxWidth().padding(8.rdp),
        horizontalArrangement = RemoteArrangement.spacedBy(8.rdp),
      ) {
        RemoteColumn(
          modifier = RemoteModifier.weight(1f.rf),
          verticalArrangement = RemoteArrangement.spacedBy(2.rdp),
        ) {
          Shown(flag(t.HAS_HEADLINE)) {
              RemoteText(
                text(t.HEADLINE),
                color = flag(t.ATTENTION).select(palette.primary, palette.onBackground),
                fontSize = 15.rsp,
                fontWeight = RemoteFontWeight.Medium,
                maxLines = 2,
                overflow = RemoteTextOverflow.Ellipsis,
              )
          }
          MetricLine(
            Icons.Default.Headphones,
            palette.primary,
            t.HAS_HEADPHONES_COUNT,
            t.HEADPHONES_COUNT,
            t.HEADPHONES_TEXT,
            palette.onBackground,
          )
          MetricLine(
            Icons.Default.PhoneAndroid,
            flag(t.READY).select(palette.tertiary, palette.warning),
            t.HAS_PHONE_COUNT,
            t.PHONE_COUNT,
            t.PHONE_TEXT,
            palette.onBackground,
          )
          InfoLine(Icons.Default.BatteryFull, text(t.BATTERY), palette.supporting)
          Shown(flag(t.HAS_SOURCE)) {
            InfoLine(Icons.Default.Cloud, text(t.SOURCE), palette.supporting)
          }
          Shown(flag(t.HAS_CHANGES)) {
              ChangesRow(
                text(t.CHANGES),
                flag(t.CHANGES_EXPANDED),
                text(t.CHANGE_LINES),
                palette,
                onAction(StatusCardExtras.CHANGES),
              )
          }
        }
        // Icons and buttons hug the card's end edge; the buttons share the column's width.
        RemoteColumn(
          modifier = RemoteModifier.width((SIDE_COLUMN_DP + 8).rdp).padding(end = 8.rdp),
          verticalArrangement = RemoteArrangement.spacedBy(4.rdp),
          horizontalAlignment = RemoteAlignment.End,
        ) {
          RemoteRow(verticalAlignment = RemoteAlignment.CenterVertically) {
            Shown(flag(t.REFRESH)) {
                IconButton(
                  Icons.Default.Refresh,
                  "Check for new episodes",
                  palette.primary,
                  onAction(StatusCardExtras.REFRESH),
                )
            }
            Shown(flag(t.AUDIO_CONTROLS)) {
                IconButton(
                  Icons.Default.Headphones,
                  "Audio controls",
                  palette.primary,
                  onAction(StatusCardExtras.AUDIO_CONTROLS),
                )
            }
          }
          for (slot in 1..t.BUTTON_SLOTS) {
            Shown(flag(t.hasButton(slot))) {
                SlotButton(
                  text(t.buttonLabel(slot)),
                  flag(t.buttonPrimary(slot)),
                  RemoteInt.createNamedRemoteInt(t.buttonIcon(slot), 0, user),
                  palette,
                  onAction(t.buttonAction(slot)),
                )
            }
          }
        }
      }
      val ready = flag(t.READY)
      // The pool's water follows the card's light or dark colours.
      RemoteStateLayout(flag(t.DARK)) { dark ->
        SwimmingPool(
          RemoteModifier.fillMaxWidth().height(STATUS_CARD_POOL_DP.rdp),
          water = if (dark) WaterColors.Night else WaterColors.Pool,
          swimming = flag(t.SWIMMING),
          level = 0.35f.rf,
          bubbles = ready.select(6.ri, 3.ri),
          amplitude = 0.025f,
          swimmerSize = 0.48f.rf,
          standing = flag(t.STANDING),
          progress = RemoteFloat.createNamedRemoteFloat(t.PROGRESS, NoProgressValue, user),
        )
      }
    }
  }
}

/**
 * [content] while [flag] holds, else a zero-size box: a conditional layout keeps both branches, and
 * one that draws nothing at all doesn't survive instantiation.
 */
@Composable
@RemoteComposable
private fun Shown(flag: RemoteBoolean, content: @Composable @RemoteComposable () -> Unit) {
  RemoteStateLayout(flag) { shown -> if (shown) content() else RemoteBox(RemoteModifier.size(0.rdp)) }
}

/** [ee.schimke.cadence.swim.NoProgress], for a template's default. */
private const val NoProgressValue = -1f

@Composable
@RemoteComposable
private fun MetricLine(
  icon: ImageVector,
  accent: RemoteColor,
  hasCount: String,
  count: String,
  text: String,
  body: RemoteColor,
) {
  val user = RemoteState.Domain.User
  RemoteRow(
    horizontalArrangement = RemoteArrangement.spacedBy(6.rdp),
    verticalAlignment = RemoteAlignment.CenterVertically,
  ) {
    WidgetIcon(icon, accent)
    Shown(RemoteBoolean.createNamedRemoteBoolean(hasCount, false, user)) {
        RemoteText(
          RemoteString.createNamedRemoteString(count, "", user),
          color = accent,
          fontSize = 17.rsp,
          fontWeight = RemoteFontWeight.Medium,
          maxLines = 1,
        )
    }
    RemoteText(
      RemoteString.createNamedRemoteString(text, "", user),
      color = body,
      fontSize = 12.rsp,
      maxLines = 1,
      overflow = RemoteTextOverflow.Ellipsis,
    )
  }
}

@Composable
@RemoteComposable
private fun InfoLine(icon: ImageVector, text: RemoteString, color: RemoteColor) {
  RemoteRow(
    horizontalArrangement = RemoteArrangement.spacedBy(6.rdp),
    verticalAlignment = RemoteAlignment.CenterVertically,
  ) {
    WidgetIcon(icon, color)
    RemoteText(
      text,
      color = color,
      fontSize = 12.rsp,
      maxLines = 1,
      overflow = RemoteTextOverflow.Ellipsis,
    )
  }
}

/** "What will change · 3 new", tapping to show or hide one line per change below it. */
@Composable
@RemoteComposable
private fun ChangesRow(
  summary: RemoteString,
  expanded: RemoteBoolean,
  lines: RemoteString,
  palette: CardPalette,
  onTap: RemoteAction?,
) {
  RemoteColumn {
    RemoteRow(
      modifier =
        RemoteModifier.heightIn(48.rdp, 56.rdp)
          .semantics {
            contentDescription = summary
            role = RemoteRole.Button
          }
          .let { if (onTap != null) it.clickable(onTap) else it },
      horizontalArrangement = RemoteArrangement.spacedBy(6.rdp),
      verticalAlignment = RemoteAlignment.CenterVertically,
    ) {
      RemoteText(
        summary,
        color = palette.onBackground,
        fontSize = 12.rsp,
        fontWeight = RemoteFontWeight.Medium,
        maxLines = 1,
        overflow = RemoteTextOverflow.Ellipsis,
      )
      RemoteStateLayout(expanded) { open ->
        WidgetIcon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, palette.onBackground)
      }
    }
    Shown(expanded) {
        RemoteText(
          lines,
          color = palette.supporting,
          fontSize = 12.rsp,
          maxLines = 7,
          overflow = RemoteTextOverflow.Ellipsis,
        )
    }
  }
}

/**
 * Every icon a conditional layout draws, drawn first and outside them all, transparent in no space.
 * A document holds each icon's path once, where it is first drawn, and later uses refer to it: an
 * icon first drawn in a branch that instantiation drops would leave the others with no path.
 */
@Composable
@RemoteComposable
private fun IconDefinitions() {
  RemoteBox(RemoteModifier.size(0.rdp)) {
    for (icon in BranchIcons) WidgetIcon(icon, RemoteColor(Color.Transparent.toArgb()))
  }
}

private val BranchIcons =
  listOf(
    Icons.Default.Cloud,
    Icons.Default.ExpandLess,
    Icons.Default.ExpandMore,
    Icons.Default.Refresh,
    Icons.Default.Headphones,
  ) + StatusButtonIcons.map { it.second }

/** Width (and height) of each icon button: the 48dp minimum touch target. */
private const val IconButtonDp = 48

@Composable
@RemoteComposable
private fun IconButton(icon: ImageVector, label: String, color: RemoteColor, onTap: RemoteAction?) {
  RemoteBox(
    modifier =
      RemoteModifier.size(IconButtonDp.rdp)
        .clip(RemoteRoundedCornerShape((IconButtonDp / 2).rdp))
        .semantics {
          contentDescription = RemoteString(label)
          role = RemoteRole.Button
        }
        .let { if (onTap != null) it.clickable(onTap) else it },
    contentAlignment = RemoteAlignment.Center,
  ) {
    WidgetIcon(icon, color)
  }
}

/** A button slot: a filled pill for the step's main action, a text button for the others. */
@Composable
@RemoteComposable
private fun SlotButton(
  label: RemoteString,
  primary: RemoteBoolean,
  icon: RemoteInt,
  palette: CardPalette,
  onTap: RemoteAction?,
) {
  RemoteBox(
    modifier =
      RemoteModifier.height(48.rdp)
        .fillMaxWidth()
        .clip(RemoteRoundedCornerShape(24.rdp))
        .background(primary.select(palette.primary, palette.background))
        .semantics {
          contentDescription = label
          role = RemoteRole.Button
        }
        .let { if (onTap != null) it.clickable(onTap) else it }
        .padding(horizontal = 16.rdp),
    contentAlignment = RemoteAlignment.Center,
  ) {
    val content = primary.select(palette.onPrimary, palette.primary)
    // A known action reads as its icon; the label stays as what a screen reader says.
    RemoteStateLayout(icon, *IntArray(StatusButtonIcons.size + 1) { it }) { which ->
      if (which > 0) WidgetIcon(StatusButtonIcons[which - 1].second, content)
      else
        RemoteText(
          label,
          color = content,
          fontSize = 14.rsp,
          fontWeight = RemoteFontWeight.Medium,
          maxLines = 1,
        )
    }
  }
}
