package ee.schimke.cadence.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import ee.schimke.cadence.bluetooth.BluetoothController
import ee.schimke.cadence.bluetooth.BluetoothState
import ee.schimke.cadence.datastore.proto.SyncPreferences
import ee.schimke.cadence.datastore.proto.SyncProfile
import ee.schimke.cadence.datastore.proto.SyncSource
import ee.schimke.cadence.metro.ViewModelKey
import ee.schimke.cadence.metro.ViewModelScope
import ee.schimke.cadence.sync.RefreshScheduler
import ee.schimke.cadence.sync.SyncRepo
import ee.schimke.cadence.usb.UsbDevice
import ee.schimke.cadence.usb.UsbManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@ContributesIntoMap(ViewModelScope::class)
@ViewModelKey(HomeViewModel::class)
@Inject
class HomeViewModel(
  private val syncRepo: SyncRepo,
  private val refreshScheduler: RefreshScheduler,
  bluetoothController: BluetoothController,
  usbManager: UsbManager,
) : ViewModel() {

  val state: StateFlow<UiState> =
    combine(
        syncRepo.profiles,
        syncRepo.sources,
        syncRepo.preferences,
        bluetoothController.state,
        usbManager.listDevices(),
      ) { profiles, sources, prefs, bt, usbDevices ->
        val effectivePrefs = prefs ?: SyncPreferences()
        UiState(
          profiles = profiles,
          sources = sources,
          preferences = effectivePrefs,
          bluetooth = bt,
          attachedDevice = usbDevices.firstMatchOrNull(effectivePrefs.usb_match),
        )
      }
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

  fun refreshNow(profileId: String) {
    refreshScheduler.runNow(profileId)
  }

  fun toggleAutoRefresh(profileId: String, enabled: Boolean) {
    viewModelScope.launch { syncRepo.setAutoRefresh(profileId, enabled) }
  }

  data class UiState(
    val profiles: List<SyncProfile> = emptyList(),
    val sources: List<SyncSource> = emptyList(),
    val preferences: SyncPreferences = SyncPreferences(),
    val bluetooth: BluetoothState = BluetoothState(),
    val attachedDevice: UsbDevice? = null,
  )
}

private fun List<UsbDevice>.firstMatchOrNull(match: String): UsbDevice? {
  if (isEmpty()) return null
  if (match.isBlank()) return firstOrNull()
  val target = match.trim().uppercase()
  return firstOrNull { device -> device.vidPidHex().equals(target, ignoreCase = true) }
}

private fun UsbDevice.vidPidHex(): String = "${vendorId.toHex4()}:${productId.toHex4()}"

private fun Int.toHex4(): String = (this and 0xFFFF).toString(16).uppercase().padStart(4, '0')
