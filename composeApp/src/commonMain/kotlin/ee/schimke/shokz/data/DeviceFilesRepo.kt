package ee.schimke.shokz.data

import dev.zacsweers.metro.Inject
import ee.schimke.shokz.datastore.proto.Device
import ee.schimke.shokz.platform.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

@Inject
class DeviceFilesRepo(private val fileSystem: FileSystem, private val platform: Platform) {
    suspend fun listFiles(device: Device): List<Path> {
        return withContext(Dispatchers.Default) {
            fileSystem.listRecursively(device.path.toPath()).toList()
        }
    }
}