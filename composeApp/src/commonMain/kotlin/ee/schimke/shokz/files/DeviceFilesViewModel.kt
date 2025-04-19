@file:OptIn(ExperimentalCoroutinesApi::class)

package ee.schimke.shokz.files

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.navigation.toRoute
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import ee.schimke.shokz.DeviceFiles
import ee.schimke.shokz.data.DevicesRepo
import ee.schimke.shokz.datastore.proto.Device
import ee.schimke.shokz.metro.ViewModelCreator
import ee.schimke.shokz.metro.ViewModelKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

class DeviceFilesViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val devicesRepo: DevicesRepo,
    private val fileSystem: FileSystem,
) : ViewModel() {
    val route = savedStateHandle.toRoute<DeviceFiles>()

    val uiState: StateFlow<UiState> = flow<UiState> {
        val device = devicesRepo.getDevice(route.id)

        if (device == null) {
            emit(UiState.NotAvailable(route))
        } else {
            emit(UiState.Loaded(device, device.path.toPath(), fileSystem))
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

        data class Loaded(val device: Device, val root: Path, val fileSystem: FileSystem) :
            UiState() {
            override val name: String
                get() = device.name
        }
    }
}

@ContributesIntoMap(AppScope::class)
@ViewModelKey(DeviceFilesViewModel::class)
@Inject
class DeviceFilesViewModelCreator(
    private val devicesRepo: DevicesRepo,
    private val fileSystem: FileSystem,
) : ViewModelCreator {
    override fun create(extras: CreationExtras): DeviceFilesViewModel =
        DeviceFilesViewModel(
            extras.createSavedStateHandle(),
            devicesRepo,
            fileSystem,
        )
}

