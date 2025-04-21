@file:OptIn(ExperimentalUuidApi::class)

package ee.schimke.shokz.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import ee.schimke.shokz.data.DevicesRepo
import ee.schimke.shokz.datastore.proto.Device
import ee.schimke.shokz.metro.ViewModelCreator
import ee.schimke.shokz.metro.ViewModelKey
import ee.schimke.shokz.usb.UsbDevice
import ee.schimke.shokz.usb.UsbManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okio.Path
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

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

@ContributesIntoMap(AppScope::class)
@ViewModelKey(DevicesViewModel::class)
@Inject
class DevicesViewModelCreator(
    private val devicesRepo: DevicesRepo,
    private val usbManager: UsbManager
) : ViewModelCreator {
    override fun create(extras: CreationExtras): DevicesViewModel = DevicesViewModel(devicesRepo, usbManager)
}

