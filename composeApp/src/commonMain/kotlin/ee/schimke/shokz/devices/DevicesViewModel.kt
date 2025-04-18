package ee.schimke.shokz.devices

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import ee.schimke.shokz.data.DevicesRepo
import ee.schimke.shokz.datastore.proto.Device
import ee.schimke.shokz.metro.ViewModelKey
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@ContributesIntoMap(AppScope::class)
@ViewModelKey(DevicesViewModel::class)
@Inject
class DevicesViewModel(
    private val devicesRepo: DevicesRepo
) : ViewModel() {
    fun addDevice(uri: String) {
        viewModelScope.launch {
            devicesRepo.addDevice(Device(name = "Unknown", uri = uri))
        }
    }

    val uiState: StateFlow<UiState> = devicesRepo.devices.map {
        UiState.Devices(it.devices)
    }.stateIn(
        viewModelScope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = UiState.Loading
    )

    sealed interface UiState {
        data object Loading : UiState

        data class Devices(val devices: List<Device>): UiState
    }
}

