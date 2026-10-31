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
 * What the headphone widget shows, derived only from [BluetoothState].
 *
 * In MP3 mode the headset plays from its own storage and reports no track details, so the phone's
 * media session is deliberately not shown as "now playing" — the same rule the Wear widgets follow.
 * EQ, playback order and volume are the last values the headset acknowledged to Cadence.
 */
internal data class HeadphoneWidgetState(
  val headline: String,
  /** For narrow one-row slots: the mode alone for OpenSwim Pro, otherwise the device. */
  val shortHeadline: String = headline,
  val nowPlaying: String,
  val settingsLine: String?,
  val transport: List<HeadphoneWidgetAction>,
  val modeSwitch: HeadphoneWidgetAction?,
) {
  companion object {
    fun from(bluetooth: BluetoothState): HeadphoneWidgetState {
      val device = bluetooth.connectedDevice
      if (device == null) {
        return HeadphoneWidgetState(
          headline = "No headphones connected",
          shortHeadline = "No headphones",
          nowPlaying = bluetooth.mediaInfo?.title ?: "Connect headphones to control them here",
          settingsLine = null,
          transport = transport(playing = bluetooth.mediaInfo?.playing == true),
          modeSwitch = null,
        )
      }
      if (bluetooth.localAudioControls) {
        return HeadphoneWidgetState(
          headline = "${device.name} · MP3",
          shortHeadline = "MP3",
          nowPlaying = "Playing from headphone storage",
          settingsLine = settingsLine(bluetooth, includeLocalAudio = true),
          transport = transport(playing = bluetooth.headsetPlaying == true),
          modeSwitch = HeadphoneWidgetAction.SwitchToBluetooth,
        )
      }
      val media = bluetooth.mediaInfo
      return HeadphoneWidgetState(
        headline = if (bluetooth.openSwimConnected) "${device.name} · Bluetooth" else device.name,
        shortHeadline = if (bluetooth.openSwimConnected) "Bluetooth" else device.name,
        // Title only: with the artist appended it ellipsizes mid-word at common widget widths.
        nowPlaying = media?.title ?: "Nothing playing",
        settingsLine =
          if (bluetooth.openSwimConnected) settingsLine(bluetooth, includeLocalAudio = false)
          else null,
        transport = transport(playing = bluetooth.mediaInfo?.playing == true),
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

    private fun settingsLine(bluetooth: BluetoothState, includeLocalAudio: Boolean): String? =
      listOfNotNull(
          bluetooth.equalizer?.let { "EQ ${it.label}" },
          bluetooth.playbackOrder?.label?.takeIf { includeLocalAudio },
          bluetooth.headsetVolumePercent?.let { "Volume $it%" }?.takeIf { includeLocalAudio },
        )
        .joinToString(" · ")
        .ifEmpty { null }
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
  val showHeadline: Boolean,
  val showNowPlaying: Boolean,
  val showSettings: Boolean,
  val transport: List<HeadphoneWidgetAction>,
  val showModeSwitch: Boolean,
  /**
   * One-row slots: the headline's width, what the buttons leave (Remote rows don't shrink text).
   */
  val headlineWidthDp: Int = 0,
) {
  /** A one-row headline column too narrow for the full headline shows the short one. */
  val shortHeadline: Boolean
    get() = singleRow && headlineWidthDp < 160

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
          headlineWidthDp = inner - buttonsWidth(transport.size) - ButtonGapDp,
          singleRow = true,
          showHeadline = showText,
          showNowPlaying = false,
          showSettings = false,
          transport = transport,
          showModeSwitch = false,
        )
      }
      return HeadphoneWidgetLayout(
        singleRow = false,
        showHeadline = true,
        showNowPlaying = heightDp >= 150,
        showSettings = heightDp >= 190 && state.settingsLine != null,
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
