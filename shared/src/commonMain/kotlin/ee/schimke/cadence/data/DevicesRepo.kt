package ee.schimke.cadence.data

import dev.zacsweers.metro.Inject
import ee.schimke.cadence.datastore.proto.Device
import ee.schimke.cadence.datastore.proto.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

@Inject
class DevicesRepo(private val dataStore: SettingsStore) {
  suspend fun addDevice(device: Device) {
    dataStore.updateData { settings ->
      val existing = settings.devices.firstOrNull { it.path == device.path }
      if (existing == null) settings.copy(devices = settings.devices + device)
      else
        settings.copy(
          devices =
            settings.devices.map { if (it.id == existing.id) it.copy(name = device.name) else it }
        )
    }
  }

  suspend fun getDevice(id: String): Device? {
    return devices.first().devices.find { it.id == id }
  }

  val devices: Flow<Settings> = dataStore.data
}
