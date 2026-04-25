@file:OptIn(ExperimentalUuidApi::class)

package ee.schimke.cadence.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import ee.schimke.cadence.browser.BrowserViewModel
import ee.schimke.cadence.data.DevicesRepo
import ee.schimke.cadence.datastore.proto.Device
import ee.schimke.cadence.metro.ViewModelKey
import ee.schimke.cadence.metro.ViewModelScope
import ee.schimke.cadence.usb.UsbDevice
import ee.schimke.cadence.usb.UsbManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okio.Path
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@ContributesIntoMap(ViewModelScope::class)
@ViewModelKey(DevicesViewModel::class)
@Inject
class DevicesViewModel(
    private val devicesRepo: DevicesRepo,
    private val usbManager: UsbManager,
) : ViewModel() {
    fun addDevice(path: Path) {
        viewModelScope.launch {
            devicesRepo.addDevice(
                Device(
                    id = Uuid.random().toString(),
                    name = "Unknown",
                    path = path.toString()
                )
            )
        }
    }

    val uiState: StateFlow<UiState> =
        combine(devicesRepo.devices, usbManager.listDevices()) { devices, usbDevices ->
            UiState.Devices(devices.devices, usbDevices)
        }.stateIn(
            viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = UiState.Loading
        )

    sealed interface UiState {
        data object Loading : UiState

        data class Devices(val devices: List<Device>, val usbDevices: List<UsbDevice>) : UiState
    }
}

