package ee.schimke.cadence.data

import androidx.datastore.core.DataStore
import dev.zacsweers.metro.Inject
import ee.schimke.cadence.datastore.proto.Device
import ee.schimke.cadence.datastore.proto.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

@Inject
class DevicesRepo(private val dataStore: DataStore<Settings>) {
    suspend fun addDevice(device: Device) {
        dataStore.updateData { devices ->
            devices.copy(devices.devices + device)
        }
    }

    suspend fun getDevice(id: String): Device? {
        return devices.first().devices.find { it.id == id }
    }

    val devices: Flow<Settings> = dataStore.data
}