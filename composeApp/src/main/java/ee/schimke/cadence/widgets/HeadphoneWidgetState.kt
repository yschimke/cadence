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
          nowPlaying = bluetooth.mediaInfo?.title ?: "Connect headphones to control them here",
          settingsLine = null,
          transport = transport(playing = bluetooth.mediaInfo?.playing == true),
          modeSwitch = null,
        )
      }
      if (bluetooth.localAudioControls) {
        return HeadphoneWidgetState(
          headline = "${device.name} · MP3",
          nowPlaying = "Playing from headphone storage",
          settingsLine = settingsLine(bluetooth, includeLocalAudio = true),
          transport = transport(playing = bluetooth.headsetPlaying == true),
          modeSwitch = HeadphoneWidgetAction.SwitchToBluetooth,
        )
      }
      val media = bluetooth.mediaInfo
      return HeadphoneWidgetState(
        headline = if (bluetooth.openSwimConnected) "${device.name} · Bluetooth" else device.name,
        nowPlaying =
          media?.title?.let { title -> media.artist?.let { "$title · $it" } ?: title }
            ?: "Nothing playing",
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
