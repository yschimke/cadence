package ee.schimke.shokz.data

import androidx.datastore.core.DataStore
import dev.zacsweers.metro.Inject
import ee.schimke.shokz.datastore.proto.Device
import ee.schimke.shokz.datastore.proto.Devices
import kotlinx.coroutines.flow.Flow

@Inject
class DevicesRepo(private val dataStore: DataStore<Devices>) {
    suspend fun addDevice(device: Device) {
        dataStore.updateData { devices -> devices.copy(devices.devices + device).also {
            println(it)
        } }
    }

    val devices: Flow<Devices> = dataStore.data
}