@file:OptIn(ExperimentalCoroutinesApi::class)

package ee.schimke.cadence.files

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.navigation.toRoute
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactory
import dev.zacsweers.metrox.viewmodel.ViewModelAssistedFactoryKey
import ee.schimke.cadence.DeviceFiles
import ee.schimke.cadence.data.DevicesRepo
import ee.schimke.cadence.data.StorageManager
import ee.schimke.cadence.data.Volume
import ee.schimke.cadence.datastore.proto.Device
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

@AssistedInject
class DeviceFilesViewModel(
  @Assisted extras: CreationExtras,
  private val devicesRepo: DevicesRepo,
  private val fileSystem: FileSystem?,
  private val storageManager: StorageManager,
) : ViewModel() {
  private val savedStateHandle: SavedStateHandle = extras.createSavedStateHandle()
  val route = savedStateHandle.toRoute<DeviceFiles>()

  val uiState: StateFlow<UiState> =
    flow<UiState> {
        val device = devicesRepo.getDevice(route.id)

        if (device == null) {
          emit(UiState.NotAvailable(route))
        } else {
          val volume = storageManager.getVolume(device.path.toPath())

          emit(UiState.Loaded(device, device.path.toPath(), fileSystem, volume))
        }
      }
      .stateIn(
        viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UiState.Loading(route),
      )

  @AssistedFactory
  @ViewModelAssistedFactoryKey(DeviceFilesViewModel::class)
  @ContributesIntoMap(AppScope::class)
  fun interface Factory : ViewModelAssistedFactory {
    override fun create(extras: CreationExtras): DeviceFilesViewModel
  }

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

    data class Loaded(
      val device: Device,
      val root: Path,
      val fileSystem: FileSystem?,
      val volume: Volume?,
    ) : UiState() {
      override val name: String
        get() = device.name
    }
  }
}
