package ee.schimke.cadence.widgets

import ee.schimke.cadence.bluetooth.BluetoothState
import ee.schimke.cadence.bluetooth.WorkingMode

/** Controls a headphone widget can fire; [wireName] is the host-action payload. */
internal enum class HeadphoneWidgetAction(val wireName: String, val label: String) {
  Previous("previous", "Previous"),
  Play("play", "Play"),
  Pause("pause", "Pause"),
  Next("next", "Next"),
  SwitchToMp3("mode_mp3", "Switch to MP3"),
  SwitchToBluetooth("mode_bluetooth", "Switch to Bluetooth");

  companion object {
    fun fromWire(value: String?): HeadphoneWidgetAction? = entries.firstOrNull {
      it.wireName == value
    }
  }
}

/**
 * What the headphone widget shows, ranked by what it tells the user, derived only from
 * [BluetoothState].
 *
 * - [title], the largest text, is what is happening now: the track playing, whether the headset is
 *   playing from its own storage, or that nothing is connected.
 * - [detail] is what the user checks at a glance: the artist, or battery and playback order.
 * - [extra], shown only at the largest sizes, holds the settings that rarely change.
 *
 * The device's name is not a heading: for the OpenSwim Pro the widget exists for, it is constant
 * and says nothing. It appears only when some other headset is connected. The mode is carried by
 * the mode switch and by the title's wording, not a label.
 *
 * In MP3 mode the headset plays from its own storage and reports no track details, so the phone's
 * media session is deliberately not shown as "now playing", the same rule the Wear widgets follow.
 * Playing/paused, EQ, playback order and volume are the last values the headset acknowledged.
 */
internal data class HeadphoneWidgetState(
  val title: String,
  /** For narrow one-row slots: the same meaning in a word or two. */
  val shortTitle: String = title,
  val detail: String?,
  val extra: String?,
  val transport: List<HeadphoneWidgetAction>,
  val modeSwitch: HeadphoneWidgetAction?,
) {
  companion object {
    fun from(bluetooth: BluetoothState): HeadphoneWidgetState {
      val device = bluetooth.connectedDevice
      val media = bluetooth.mediaInfo
      val phoneTransport = transport(playing = media?.playing == true)
      if (device == null) {
        return HeadphoneWidgetState(
          title = "Headphones not connected",
          shortTitle = "Not connected",
          // The buttons still drive the phone's player, so say what they would control.
          detail = media?.title?.let { "On phone: $it" },
          extra = null,
          transport = phoneTransport,
          modeSwitch = null,
        )
      }
      val battery = device.batteryPercent?.let { "$it% battery" }
      if (bluetooth.localAudioControls) {
        val (title, short) =
          when (bluetooth.headsetPlaying) {
            true -> "Playing from headphones" to "Playing"
            false -> "Paused" to "Paused"
            null -> "Headphone MP3 player" to "MP3"
          }
        return HeadphoneWidgetState(
          title = title,
          shortTitle = short,
          detail = joined(battery, bluetooth.playbackOrder?.label),
          extra = joined(eq(bluetooth), bluetooth.headsetVolumePercent?.let { "Volume $it%" }),
          transport = transport(playing = bluetooth.headsetPlaying == true),
          modeSwitch = HeadphoneWidgetAction.SwitchToBluetooth,
        )
      }
      return HeadphoneWidgetState(
        title = media?.title ?: "Nothing playing",
        detail = media?.artist ?: battery,
        extra =
          joined(
            // A headset other than the one the widget is built for is worth naming.
            if (bluetooth.openSwimConnected) "Bluetooth" else device.name,
            battery.takeIf { media?.artist != null },
            eq(bluetooth),
          ),
        transport = phoneTransport,
        modeSwitch =
          HeadphoneWidgetAction.SwitchToMp3.takeIf {
            bluetooth.openSwimConnected && bluetooth.workingMode == WorkingMode.Bluetooth
          },
      )
    }

    /**
     * One play/pause control. In Bluetooth mode [playing] is the phone player's state; in MP3 mode
     * the headset reports none, so it is the last Play/Pause the headset acknowledged.
     */
    private fun transport(playing: Boolean) =
      listOf(
        HeadphoneWidgetAction.Previous,
        if (playing) HeadphoneWidgetAction.Pause else HeadphoneWidgetAction.Play,
        HeadphoneWidgetAction.Next,
      )

    /** Only an OpenSwim Pro takes Cadence's EQ; any other headset's value would be stale. */
    private fun eq(bluetooth: BluetoothState) =
      bluetooth.equalizer?.takeIf { bluetooth.openSwimConnected }?.let { "${it.label} EQ" }

    private fun joined(vararg parts: String?): String? =
      parts.filterNotNull().joinToString(" · ").ifEmpty { null }
  }
}

/**
 * What fits in a widget slot of [widthDp] × [heightDp]. Smaller slots drop information instead of
 * squeezing it: a one-row slot is a single line with the essential controls, a narrow slot keeps
 * play/pause first, then next, then previous, and the settings line and mode switch only appear
 * when there is room for them at full size.
 */
internal data class HeadphoneWidgetLayout(
  val singleRow: Boolean,
  val showTitle: Boolean,
  val showDetail: Boolean,
  val showExtra: Boolean,
  val transport: List<HeadphoneWidgetAction>,
  val showModeSwitch: Boolean,
  /**
   * One-row slots: the headline's width, what the buttons leave (Remote rows don't shrink text).
   */
  val titleWidthDp: Int = 0,
) {
  /** A one-row title column too narrow for the full title shows the short one. */
  val shortTitle: Boolean
    get() = singleRow && titleWidthDp < 160

  companion object {
    /** Every control is a 48dp touch target. */
    const val ButtonDp = 48
    const val ButtonGapDp = 8
    const val PaddingDp = 12

    /** Below this a slot is one launcher row (cells are about 84dp tall). */
    const val SingleRowMaxHeightDp = 120

    /** Narrowest text column worth showing beside the buttons in a one-row slot. */
    const val MinTextDp = 96

    fun of(state: HeadphoneWidgetState, widthDp: Int, heightDp: Int): HeadphoneWidgetLayout {
      val inner = widthDp - 2 * PaddingDp
      val singleRow = heightDp < SingleRowMaxHeightDp
      val modeSwitch = state.modeSwitch != null && !singleRow && inner >= buttonsWidth(4)
      // The mode switch takes a button's room from the transport when it is shown.
      val transportRoom = if (modeSwitch) inner - ButtonDp - ButtonGapDp else inner
      if (singleRow) {
        // Text first when it fits beside play/pause; the remaining width decides more buttons.
        val withText = inner - MinTextDp - ButtonGapDp
        val showText = withText >= buttonsWidth(1)
        val transport = fit(state.transport, if (showText) withText else inner)
        return HeadphoneWidgetLayout(
          titleWidthDp = inner - buttonsWidth(transport.size) - ButtonGapDp,
          singleRow = true,
          showTitle = showText,
          showDetail = false,
          showExtra = false,
          transport = transport,
          showModeSwitch = false,
        )
      }
      return HeadphoneWidgetLayout(
        singleRow = false,
        showTitle = true,
        showDetail = heightDp >= 150 && state.detail != null,
        showExtra = heightDp >= 190 && state.extra != null,
        transport = fit(state.transport, transportRoom),
        showModeSwitch = modeSwitch,
      )
    }

    private fun buttonsWidth(count: Int) = count * ButtonDp + (count - 1) * ButtonGapDp

    /** Keeps play/pause, then next, then previous, as many as [room] holds. */
    private fun fit(
      transport: List<HeadphoneWidgetAction>,
      room: Int,
    ): List<HeadphoneWidgetAction> {
      val priority = transport.sortedBy {
        when (it) {
          HeadphoneWidgetAction.Play,
          HeadphoneWidgetAction.Pause -> 0
          HeadphoneWidgetAction.Next -> 1
          else -> 2
        }
      }
      var count = 1
      while (count < priority.size && buttonsWidth(count + 1) <= room) count++
      val kept = priority.take(count).toSet()
      return transport.filter { it in kept }
    }
  }
}
