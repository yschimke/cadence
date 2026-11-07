@file:Suppress("RestrictedApi", "RestrictedApiAndroidX")

package ee.schimke.cadence.widgets

import androidx.compose.remote.creation.compose.action.RemoteAction
import androidx.compose.remote.creation.compose.layout.RemoteBox
import androidx.compose.remote.creation.compose.layout.RemoteComposable
import androidx.compose.remote.creation.compose.layout.RemoteStateLayout
import androidx.compose.remote.creation.compose.modifier.RemoteModifier
import androidx.compose.remote.creation.compose.modifier.background
import androidx.compose.remote.creation.compose.modifier.fillMaxSize
import androidx.compose.remote.creation.compose.state.RemoteBoolean
import androidx.compose.remote.creation.compose.state.RemoteColor
import androidx.compose.remote.creation.compose.state.RemoteInt
import androidx.compose.remote.creation.compose.state.RemoteState
import androidx.compose.remote.creation.compose.state.RemoteString
import androidx.compose.remote.creation.compose.state.rf
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import ee.schimke.cadence.swim.LocalSwimClock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The headphone widget as a Livery template (contract [CONTRACT]): one values-free document per
 * [Tier], whose text, colours and state are named values the widget host writes in.
 *
 * What the widget shows changes with its state, not just its text: Play or Pause, the mode switch
 * and its water styling, and whether the detail and settings rows appear. Those are conditional
 * layouts (`RemoteStateLayout`) on [MODE], [HAS_DETAIL], [HAS_EXTRA] and [PLAYING]; instantiation
 * writes the values in and the player draws the live branch. Each branch lays out exactly as
 * [HeadphoneWidgetLayout] does for that state and tier.
 */
object HeadphoneControlsTemplate {
  const val CONTRACT = "cadence/headphone-controls@1"

  /**
   * The minor of [CONTRACT] this app implements (`composeApp/livery/contracts`): Livery never picks
   * a template that needs a newer one, which a later catalog may carry for newer app versions.
   */
  const val CONTRACT_MINOR = 0

  const val TITLE = "title"
  const val SHORT_TITLE = "short_title"
  const val DETAIL = "detail"
  const val EXTRA = "extra"
  const val HAS_DETAIL = "has_detail"
  const val HAS_EXTRA = "has_extra"
  const val PLAYING = "playing"
  const val MODE = "mode"

  /** [MODE]: which mode switch shows, which also decides the water look (MP3 mode). */
  const val MODE_NONE = 0
  const val MODE_TO_MP3 = 1
  const val MODE_TO_BLUETOOTH = 2

  /** The opening tap's action, beside [HeadphoneWidgetAction]'s wire names. */
  const val OPEN = "open"

  /** Colour roles, as `theme.*` inputs: the system's dynamic colours, written in per render. */
  val COLORS: Map<String, (WidgetColors) -> Color> =
    linkedMapOf(
      "theme.background" to { it.background },
      "theme.on_background" to { it.onBackground },
      "theme.supporting" to { it.supporting },
      "theme.tonal" to { it.tonal },
      "theme.on_tonal" to { it.onTonal },
      "theme.primary" to { it.primary },
      "theme.on_primary" to { it.onPrimary },
    )

  /**
   * A slot size range with one layout: every state lays out the same everywhere in it.
   * [maxWidthDp]/[maxHeightDp] are inclusive; null is unbounded.
   */
  data class Tier(
    val minWidthDp: Int,
    val maxWidthDp: Int?,
    val minHeightDp: Int,
    val maxHeightDp: Int?,
  ) {
    /** A size inside the tier, to lay out its branches with. */
    val widthDp: Int
      get() = minWidthDp

    val heightDp: Int
      get() = minHeightDp

    val name: String
      get() = "launcher-w$minWidthDp-h$minHeightDp"
  }

  /** The states a branch is laid out for: every combination the conditional layouts pick from. */
  internal fun variantState(mode: Int, hasDetail: Boolean, hasExtra: Boolean) =
    HeadphoneWidgetState(
      title = "",
      detail = if (hasDetail) "" else null,
      extra = if (hasExtra) "" else null,
      transport =
        listOf(
          HeadphoneWidgetAction.Previous,
          HeadphoneWidgetAction.Play,
          HeadphoneWidgetAction.Next,
        ),
      modeSwitch =
        when (mode) {
          MODE_TO_MP3 -> HeadphoneWidgetAction.SwitchToMp3
          MODE_TO_BLUETOOTH -> HeadphoneWidgetAction.SwitchToBluetooth
          else -> null
        },
      showWater = mode == MODE_TO_BLUETOOTH,
    )

  private val variants =
    (MODE_NONE..MODE_TO_BLUETOOTH).flatMap { m ->
      listOf(false, true).flatMap { d -> listOf(false, true).map { e -> Triple(m, d, e) } }
    }

  /** Everything about a size's layout that a template fixes (not the single-row title width). */
  private fun signature(widthDp: Int, heightDp: Int) = variants.map { (m, d, e) ->
    HeadphoneWidgetLayout.of(variantState(m, d, e), widthDp, heightDp).let {
      listOf(
        it.singleRow,
        it.showTitle,
        it.shortTitle,
        it.showDetail,
        it.showExtra,
        it.transport,
        it.showModeSwitch,
      )
    }
  }

  /**
   * The tiers launcher widgets fall in, from [HeadphoneWidgetLayout]'s own breakpoints: a size
   * starts a new tier wherever any state's layout changes along either axis.
   */
  fun tiers(maxDp: Int = 1_000): List<Tier> {
    val probe = listOf(60, 100, 140, 170, 200, 260, 400)
    val widths =
      (MIN_DP..maxDp).filter { w ->
        w == MIN_DP || probe.any { h -> signature(w, h) != signature(w - 1, h) }
      }
    val heights =
      (MIN_DP..maxDp).filter { h ->
        h == MIN_DP ||
          listOf(100, 200, 260, 320, 500).any { w -> signature(w, h) != signature(w, h - 1) }
      }
    return widths.indices.flatMap { i ->
      heights.indices.map { j ->
        Tier(
          widths[i],
          widths.getOrNull(i + 1)?.minus(1),
          heights[j],
          heights.getOrNull(j + 1)?.minus(1),
        )
      }
    }
  }

  /** The smallest slot a launcher gives a widget. */
  const val MIN_DP = 40

  /**
   * [state] in [colors] as the contract's data: what the widget host binds and writes into the
   * template ([HeadphoneWidgetState] already decided what each line says).
   */
  fun data(state: HeadphoneWidgetState, colors: WidgetColors): JsonObject = buildJsonObject {
    put(TITLE, state.title)
    put(SHORT_TITLE, state.shortTitle)
    put(DETAIL, state.detail.orEmpty())
    put(HAS_DETAIL, state.detail != null)
    put(EXTRA, state.extra.orEmpty())
    put(HAS_EXTRA, state.extra != null)
    put(PLAYING, HeadphoneWidgetAction.Pause in state.transport)
    put(
      MODE,
      when (state.modeSwitch) {
        HeadphoneWidgetAction.SwitchToMp3 -> MODE_TO_MP3
        HeadphoneWidgetAction.SwitchToBluetooth -> MODE_TO_BLUETOOTH
        else -> MODE_NONE
      },
    )
    COLORS.forEach { (role, of) -> put(role, "#%08X".format(of(colors).toArgb())) }
  }
}

/**
 * The template for [tier] in [look]: [HeadphoneControlsBody] with every value named. [onOpen] and
 * [onAction] are the host actions its taps fire (the contract's action names).
 *
 * [HeadphoneLook.Default] reads the system's colours (`theme.*`); a seasonal look draws its own
 * palette over its backdrop, which moves while audio plays.
 */
@Composable
@RemoteComposable
fun HeadphoneControlsTemplateContent(
  tier: HeadphoneControlsTemplate.Tier,
  onOpen: RemoteAction?,
  onAction: @Composable (HeadphoneWidgetAction) -> RemoteAction?,
  look: HeadphoneLook = HeadphoneLook.Default,
) {
  val t = HeadphoneControlsTemplate
  val user = RemoteState.Domain.User
  fun named(name: String) = RemoteString.createNamedRemoteString(name, "", user)
  val texts =
    HeadphoneWidgetTexts(
      title = named(t.TITLE),
      shortTitle = named(t.SHORT_TITLE),
      detail = named(t.DETAIL),
      extra = named(t.EXTRA),
    )
  val defaults = WidgetColors.Dark
  fun color(role: String) =
    RemoteColor.createNamedRemoteColor(role, t.COLORS.getValue(role)(defaults).toArgb(), user)
  val palette =
    look.colors?.let { RemotePalette.of(it) }
      ?: RemotePalette(
        background = color("theme.background"),
        onBackground = color("theme.on_background"),
        supporting = color("theme.supporting"),
        tonal = color("theme.tonal"),
        onTonal = color("theme.on_tonal"),
        primary = color("theme.primary"),
        onPrimary = color("theme.on_primary"),
      )
  val mode = RemoteInt.createNamedRemoteInt(t.MODE, t.MODE_NONE, user)
  val hasDetail = RemoteBoolean.createNamedRemoteBoolean(t.HAS_DETAIL, false, user)
  val hasExtra = RemoteBoolean.createNamedRemoteBoolean(t.HAS_EXTRA, false, user)
  val playing = RemoteBoolean.createNamedRemoteBoolean(t.PLAYING, false, user)
  val branches: @Composable @RemoteComposable () -> Unit = {
    RemoteStateLayout(mode, t.MODE_NONE, t.MODE_TO_MP3, t.MODE_TO_BLUETOOTH) { m: Int ->
      RemoteStateLayout(hasDetail) { d ->
        RemoteStateLayout(hasExtra) { e ->
          val state = t.variantState(m, d, e)
          HeadphoneControlsBody(
            texts = texts,
            palette = palette,
            layout = HeadphoneWidgetLayout.of(state, tier.widthDp, tier.heightDp),
            modeSwitch = state.modeSwitch,
            showWater = state.showWater,
            onOpen = onOpen,
            onAction = onAction,
            drawBackground = look.backdrop == null,
            transportButton = { action, button ->
              if (action == HeadphoneWidgetAction.Play || action == HeadphoneWidgetAction.Pause) {
                RemoteStateLayout(playing) { p ->
                  button(if (p) HeadphoneWidgetAction.Pause else HeadphoneWidgetAction.Play)
                }
              } else {
                button(action)
              }
            },
          )
        }
      }
    }
  }
  val backdrop = look.backdrop
  if (backdrop == null) {
    branches()
  } else {
    // One backdrop under every state's branch, not one per branch: it is the same for all of them.
    RemoteBox(RemoteModifier.fillMaxSize().background(palette.background)) {
      // Not a conditional layout on [playing]: Android 16's platform player evaluates a hidden
      // branch's drawing too, before it has a size. The clock itself picks: still while paused.
      backdrop(playing.select(LocalSwimClock.current, 7.5f.rf))
      branches()
    }
  }
}
