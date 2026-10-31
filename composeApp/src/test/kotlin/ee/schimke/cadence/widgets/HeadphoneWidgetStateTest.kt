package ee.schimke.cadence.widgets

import ee.schimke.cadence.bluetooth.BluetoothState
import ee.schimke.cadence.bluetooth.ConnectedDevice
import ee.schimke.cadence.bluetooth.EqualizerPreset
import ee.schimke.cadence.bluetooth.MediaInfo
import ee.schimke.cadence.bluetooth.PlaybackOrder
import ee.schimke.cadence.bluetooth.WorkingMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HeadphoneWidgetStateTest {
  private val openSwim =
    ConnectedDevice("OpenSwim Pro", "00:11:22:33:44:55", listOf("A2DP"), batteryPercent = 78)
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

  private fun mp3(playing: Boolean? = null) =
    HeadphoneWidgetState.from(
      BluetoothState(
        connectedDevice = openSwim,
        workingMode = WorkingMode.Mp3,
        mediaInfo = phoneMedia,
        equalizer = EqualizerPreset.Swimming,
        playbackOrder = PlaybackOrder.Shuffle,
        headsetVolumePercent = 56,
        headsetPlaying = playing,
      )
    )

  @Test
  fun mp3TitleSaysWhatIsHappeningNotTheDeviceName() {
    assertEquals("Playing from headphones", mp3(playing = true).title)
    assertEquals("Paused", mp3(playing = false).title)
    assertEquals("Headphone MP3 player", mp3().title)
    assertEquals("Playing", mp3(playing = true).shortTitle)
    listOf(mp3(true), mp3(false), mp3()).forEach { state ->
      assertFalse("OpenSwim" in state.title)
      assertFalse("OpenSwim" in state.detail.orEmpty())
      assertFalse("OpenSwim" in state.extra.orEmpty())
    }
  }

  @Test
  fun mp3DetailIsBatteryAndOrderAndExtraIsSettings() {
    val state = mp3(playing = true)
    assertEquals("78% battery · Shuffle", state.detail)
    assertEquals("Swimming EQ · Volume 56%", state.extra)
  }

  @Test
  fun mp3NeverShowsThePhonesMediaSession() {
    val state = mp3(playing = true)
    listOf(state.title, state.detail, state.extra).forEach {
      assertFalse("Phone" in it.orEmpty(), "MP3 mode showed phone media: $it")
    }
  }

  @Test
  fun mp3ToggleFollowsAcknowledgedHeadsetPlayback() {
    assertEquals(
      listOf(
        HeadphoneWidgetAction.Previous,
        HeadphoneWidgetAction.Play,
        HeadphoneWidgetAction.Next,
      ),
      mp3().transport,
    )
    assertEquals(HeadphoneWidgetAction.Pause, mp3(playing = true).transport[1])
    assertEquals(HeadphoneWidgetAction.SwitchToBluetooth, mp3().modeSwitch)
  }

  @Test
  fun bluetoothLeadsWithTheTrack() {
    val state =
      HeadphoneWidgetState.from(BluetoothState(connectedDevice = openSwim, mediaInfo = phoneMedia))
    assertEquals("Phone episode", state.title)
    assertEquals("Phone podcast", state.detail)
    assertEquals("Bluetooth · 78% battery", state.extra)
    assertEquals(HeadphoneWidgetAction.Pause, state.transport[1])
    assertEquals(HeadphoneWidgetAction.SwitchToMp3, state.modeSwitch)
  }

  @Test
  fun anotherHeadsetIsNamedButGetsNoModeSwitch() {
    val state =
      HeadphoneWidgetState.from(
        BluetoothState(
          connectedDevice = openSwim.copy(name = "Other buds", batteryPercent = null),
          workingMode = WorkingMode.Mp3,
          equalizer = EqualizerPreset.Vocal,
        )
      )
    assertEquals("Nothing playing", state.title)
    assertEquals("Other buds", state.extra)
    assertNull(state.modeSwitch)
    assertEquals(HeadphoneWidgetAction.Play, state.transport[1])
  }

  @Test
  fun disconnectedSaysSoAndWhatTheButtonsControl() {
    val idle = HeadphoneWidgetState.from(BluetoothState())
    assertEquals("Headphones not connected", idle.title)
    assertNull(idle.detail)
    val phonePlaying = HeadphoneWidgetState.from(BluetoothState(mediaInfo = phoneMedia))
    assertEquals("On phone: Phone episode", phonePlaying.detail)
  }

  @Test
  fun wireNamesRoundTrip() {
    HeadphoneWidgetAction.entries.forEach {
      assertEquals(it, HeadphoneWidgetAction.fromWire(it.wireName))
    }
    assertNull(HeadphoneWidgetAction.fromWire("unknown"))
  }

  @Test
  fun twoByOneKeepsOnlyEssentialControls() {
    val layout = HeadphoneWidgetLayout.of(mp3(), widthDp = 144, heightDp = 84)
    assertTrue(layout.singleRow)
    assertFalse(layout.showTitle)
    assertEquals(listOf(HeadphoneWidgetAction.Play, HeadphoneWidgetAction.Next), layout.transport)
    assertFalse(layout.showModeSwitch)
  }

  @Test
  fun narrowSingleRowUsesShortTitle() {
    assertTrue(HeadphoneWidgetLayout.of(mp3(), widthDp = 232, heightDp = 72).shortTitle)
    assertFalse(HeadphoneWidgetLayout.of(mp3(), 392, 72).shortTitle)
  }

  @Test
  fun wideSingleRowShowsTitleBesideTransport() {
    val layout = HeadphoneWidgetLayout.of(mp3(), widthDp = 360, heightDp = 84)
    assertTrue(layout.showTitle)
    assertFalse(layout.showDetail)
    assertEquals(3, layout.transport.size)
  }

  @Test
  fun detailBeforeExtraAsHeightGrows() {
    val fourByTwo = HeadphoneWidgetLayout.of(mp3(), widthDp = 300, heightDp = 168)
    assertTrue(fourByTwo.showDetail)
    assertFalse(fourByTwo.showExtra)
    assertTrue(fourByTwo.showModeSwitch)
    assertTrue(HeadphoneWidgetLayout.of(mp3(), 300, 252).showExtra)
  }

  @Test
  fun narrowTallDropsModeSwitchBeforeTransport() {
    val layout = HeadphoneWidgetLayout.of(mp3(), widthDp = 200, heightDp = 168)
    assertFalse(layout.showModeSwitch)
    assertEquals(3, layout.transport.size)
  }
}
