@file:OptIn(ExperimentalCoroutinesApi::class)

package ee.schimke.shokz.files

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import ee.schimke.shokz.DeviceFiles
import ee.schimke.shokz.data.DevicesRepo
import ee.schimke.shokz.datastore.proto.Device
import ee.schimke.shokz.metro.ViewModelKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okio.Path

@ContributesIntoMap(AppScope::class)
@ViewModelKey(DeviceFilesViewModel::class)
@Inject
class DeviceFilesViewModel(
//    private val savedStateHandle: SavedStateHandle,
    private val devicesRepo: DevicesRepo,
) : ViewModel() {
    val route = DeviceFiles("Unknown")//savedStateHandle.toRoute<DeviceFiles>()

    val uiState: StateFlow<UiState> = devicesRepo.devices.flatMapLatest {
        flowOf<UiState>(UiState.Loaded(route, listOf()))
    }.stateIn(
        viewModelScope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = UiState.Loading(route)
    )

    sealed class UiState {
        abstract val route: DeviceFiles

        data class Loading(override val route: DeviceFiles) : UiState()

        data class Loaded(override val route: DeviceFiles, val files: List<Path>): UiState()
    }
}

