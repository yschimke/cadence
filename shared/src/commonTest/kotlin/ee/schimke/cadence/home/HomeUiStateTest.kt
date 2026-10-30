package ee.schimke.cadence.home

import androidx.appstate.transform.testing.transformTest
import androidx.appstate.transform.transform
import ee.schimke.cadence.bluetooth.BluetoothState
import ee.schimke.cadence.datastore.proto.SyncPreferences
import ee.schimke.cadence.datastore.proto.SyncProfile
import ee.schimke.cadence.datastore.proto.SyncSource
import ee.schimke.cadence.sync.RefreshProgress
import ee.schimke.cadence.usb.UsbDevice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

class HomeUiStateTest {
  @Test
  fun loadedOnceProfilesAndSourcesAreRead() = transformTest {
    val profiles = MutableSharedFlow<List<SyncProfile>>(replay = 1)
    val sources = MutableSharedFlow<List<SyncSource>>(replay = 1)
    val state =
      transform(HomeViewModel.UiState(), backgroundScope, testDispatcher) {
        homeUiState(
          profiles = profiles,
          sources = sources,
          preferences = flowOf(null),
          contents = emptyMap(),
          refresh = flowOf(RefreshProgress()),
          bluetooth = flowOf(BluetoothState()),
          bluetoothSupported = true,
          usbDevices = flowOf(emptyList()),
        )
      }
    advance()
    assertFalse(state.value.loaded)

    sources.emit(emptyList())
    advance()
    assertFalse(state.value.loaded, "profiles not read yet")

    profiles.emit(emptyList())
    advance()
    assertTrue(state.value.loaded)
    assertTrue(state.value.bluetoothSupported)
  }

  @Test
  fun attachedDeviceFollowsUsbMatch() = transformTest {
    val shokz = UsbDevice(1, "a", 0, 0x1234, null, 0xABCD, null)
    val other = UsbDevice(2, "b", 0, 0x0001, null, 0x0002, null)
    val preferences = MutableStateFlow<SyncPreferences?>(null)
    val state =
      transform(HomeViewModel.UiState(), backgroundScope, testDispatcher) {
        homeUiState(
          profiles = flowOf(emptyList()),
          sources = flowOf(emptyList()),
          preferences = preferences,
          contents = emptyMap(),
          refresh = flowOf(RefreshProgress()),
          bluetooth = flowOf(BluetoothState()),
          bluetoothSupported = false,
          usbDevices = flowOf(listOf(other, shokz)),
        )
      }
    advance()
    assertEquals(other, state.value.attachedDevice, "blank match picks the first device")

    preferences.value = SyncPreferences(usb_match = "1234:abcd")
    advance()
    assertEquals(shokz, state.value.attachedDevice)
  }
}
