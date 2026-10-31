package ee.schimke.cadence.widgets

import ee.schimke.cadence.bluetooth.BluetoothState
import ee.schimke.cadence.bluetooth.ConnectedDevice
import ee.schimke.cadence.bluetooth.EqualizerPreset
import ee.schimke.cadence.bluetooth.MediaInfo
import ee.schimke.cadence.bluetooth.PlaybackOrder
import ee.schimke.cadence.bluetooth.WorkingMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HeadphoneWidgetStateTest {
  private val openSwim = ConnectedDevice("OpenSwim Pro", "00:11:22:33:44:55", listOf("A2DP"))
  private val phoneMedia =
    MediaInfo(
      title = "Phone episode",
      artist = "Phone podcast",
      album = null,
      playing = true,
      durationMs = null,
      positionMs = null,
      packageName = "player",
    )

  @Test
  fun mp3ModeUsesHeadsetTransportAndNeverPhoneMetadata() {
    val state =
      HeadphoneWidgetState.from(
        BluetoothState(
          connectedDevice = openSwim,
          workingMode = WorkingMode.Mp3,
          mediaInfo = phoneMedia,
          equalizer = EqualizerPreset.Swimming,
          playbackOrder = PlaybackOrder.Shuffle,
          headsetVolumePercent = 56,
        )
      )

    assertEquals("OpenSwim Pro · MP3", state.headline)
    assertEquals("Playing from headphone storage", state.nowPlaying)
    assertEquals("EQ Swimming · Shuffle · Volume 56%", state.settingsLine)
    // The phone player is playing, but that is not the headset: unknown headset state shows Play.
    assertEquals(
      listOf(
        HeadphoneWidgetAction.Previous,
        HeadphoneWidgetAction.Play,
        HeadphoneWidgetAction.Next,
      ),
      state.transport,
    )
    assertEquals(HeadphoneWidgetAction.SwitchToBluetooth, state.modeSwitch)
  }

  @Test
  fun mp3ModeToggleFollowsAcknowledgedHeadsetPlayback() {
    val playing =
      HeadphoneWidgetState.from(
        BluetoothState(
          connectedDevice = openSwim,
          workingMode = WorkingMode.Mp3,
          headsetPlaying = true,
        )
      )
    assertEquals(HeadphoneWidgetAction.Pause, playing.transport[1])
  }

  @Test
  fun bluetoothModeOffersMp3SwitchAndPhonePlayback() {
    val state =
      HeadphoneWidgetState.from(BluetoothState(connectedDevice = openSwim, mediaInfo = phoneMedia))

    assertEquals("OpenSwim Pro · Bluetooth", state.headline)
    assertEquals("Phone episode", state.nowPlaying)
    assertNull(state.settingsLine)
    assertEquals(HeadphoneWidgetAction.Pause, state.transport[1])
    assertEquals(HeadphoneWidgetAction.SwitchToMp3, state.modeSwitch)
  }

  @Test
  fun otherHeadphonesHaveNoModeSwitchEvenIfMp3WasSelected() {
    val state =
      HeadphoneWidgetState.from(
        BluetoothState(
          connectedDevice = openSwim.copy(name = "Other buds"),
          workingMode = WorkingMode.Mp3,
          equalizer = EqualizerPreset.Vocal,
        )
      )

    assertEquals("Other buds", state.headline)
    assertNull(state.modeSwitch)
    assertNull(state.settingsLine)
    assertEquals(HeadphoneWidgetAction.Play, state.transport[1])
  }

  @Test
  fun wireNamesRoundTrip() {
    HeadphoneWidgetAction.entries.forEach {
      assertEquals(it, HeadphoneWidgetAction.fromWire(it.wireName))
    }
    assertNull(HeadphoneWidgetAction.fromWire("unknown"))
  }

  private val mp3 =
    HeadphoneWidgetState.from(
      BluetoothState(
        connectedDevice = openSwim,
        workingMode = WorkingMode.Mp3,
        equalizer = EqualizerPreset.Vocal,
      )
    )

  @Test
  fun twoByOneKeepsOnlyEssentialControls() {
    val layout = HeadphoneWidgetLayout.of(mp3, widthDp = 144, heightDp = 84)
    assertEquals(true, layout.singleRow)
    assertEquals(false, layout.showHeadline)
    assertEquals(
      listOf(HeadphoneWidgetAction.Play, HeadphoneWidgetAction.Next),
      layout.transport,
    )
    assertEquals(false, layout.showModeSwitch)
  }

  @Test
  fun wideSingleRowShowsHeadlineBesideTransport() {
    val layout = HeadphoneWidgetLayout.of(mp3, widthDp = 360, heightDp = 84)
    assertEquals(true, layout.showHeadline)
    assertEquals(false, layout.showNowPlaying)
    assertEquals(3, layout.transport.size)
  }

  @Test
  fun narrowSingleRowUsesShortHeadline() {
    val layout = HeadphoneWidgetLayout.of(mp3, widthDp = 232, heightDp = 72)
    assertEquals(true, layout.shortHeadline)
    assertEquals("MP3", mp3.shortHeadline)
    assertEquals(false, HeadphoneWidgetLayout.of(mp3, 392, 72).shortHeadline)
  }

  @Test
  fun fourByTwoShowsDetailsAndModeSwitch() {
    val layout = HeadphoneWidgetLayout.of(mp3, widthDp = 300, heightDp = 168)
    assertEquals(false, layout.singleRow)
    assertEquals(true, layout.showNowPlaying)
    assertEquals(false, layout.showSettings)
    assertEquals(3, layout.transport.size)
    assertEquals(true, layout.showModeSwitch)
    assertEquals(true, HeadphoneWidgetLayout.of(mp3, 300, 252).showSettings)
  }

  @Test
  fun narrowTallDropsModeSwitchBeforeTransport() {
    val layout = HeadphoneWidgetLayout.of(mp3, widthDp = 200, heightDp = 168)
    assertEquals(false, layout.showModeSwitch)
    assertEquals(3, layout.transport.size)
  }
}
