package ee.schimke.cadence.bluetooth

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BluetoothStateTest {
  private val openSwim = ConnectedDevice("Shokz OpenSwim Pro", "00:11", emptyList())

  @Test
  fun localAudioControlsNeedOpenSwimInMp3Mode() {
    assertTrue(
      BluetoothState(connectedDevice = openSwim, workingMode = WorkingMode.Mp3).localAudioControls
    )
    assertFalse(BluetoothState(connectedDevice = openSwim).localAudioControls)
    assertFalse(
      BluetoothState(connectedDevice = openSwim.copy(name = "Buds"), workingMode = WorkingMode.Mp3)
        .localAudioControls
    )
    assertFalse(BluetoothState(workingMode = WorkingMode.Mp3).localAudioControls)
  }

  @Test
  fun openSwimMatchIsCaseInsensitive() {
    assertTrue(
      BluetoothState(connectedDevice = openSwim.copy(name = "openswim pro")).openSwimConnected
    )
  }
}
