package ee.schimke.shokz.data

import androidx.datastore.core.DataStore
import dev.zacsweers.metro.Inject
import ee.schimke.shokz.datastore.proto.Device
import ee.schimke.shokz.datastore.proto.Devices
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

@Inject
class DevicesRepo(private val dataStore: DataStore<Devices>) {
    suspend fun addDevice(device: Device) {
        dataStore.updateData { devices ->
            devices.copy(devices.devices + device)
        }
    }

    suspend fun getDevice(id: String): Device? {
        return devices.first().devices.find { it.id == id }
    }

    val devices: Flow<Devices> = dataStore.data
}