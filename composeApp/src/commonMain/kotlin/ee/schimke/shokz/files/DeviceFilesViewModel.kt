@file:OptIn(ExperimentalCoroutinesApi::class)

package ee.schimke.shokz.files

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.navigation.toRoute
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import ee.schimke.shokz.DeviceFiles
import ee.schimke.shokz.browser.BrowserViewModel
import ee.schimke.shokz.data.DevicesRepo
import ee.schimke.shokz.data.StorageManager
import ee.schimke.shokz.data.Volume
import ee.schimke.shokz.datastore.proto.Device
import ee.schimke.shokz.metro.ViewModelKey
import ee.schimke.shokz.metro.ViewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

@ContributesIntoMap(ViewModelScope::class)
@ViewModelKey(DeviceFilesViewModel::class)
@Inject
class DeviceFilesViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val devicesRepo: DevicesRepo,
    private val fileSystem: FileSystem,
    private val storageManager: StorageManager,
) : ViewModel() {
    val route = savedStateHandle.toRoute<DeviceFiles>()

    val uiState: StateFlow<UiState> = flow<UiState> {
        val device = devicesRepo.getDevice(route.id)

        if (device == null) {
            emit(UiState.NotAvailable(route))
        } else {
            val volume = storageManager.getVolume(device.path.toPath())

            emit(UiState.Loaded(device, device.path.toPath(), fileSystem, volume))
        }
    }.stateIn(
        viewModelScope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = UiState.Loading(route)
    )

    sealed class UiState {
        abstract val name: String

        data class Loading(val route: DeviceFiles) : UiState() {
            override val name: String
                get() = route.id
        }

        data class NotAvailable(val route: DeviceFiles) : UiState() {
            override val name: String
                get() = route.id
        }

        data class Loaded(val device: Device, val root: Path, val fileSystem: FileSystem, val volume: Volume?) :
            UiState() {
            override val name: String
                get() = device.name
        }
    }
}

