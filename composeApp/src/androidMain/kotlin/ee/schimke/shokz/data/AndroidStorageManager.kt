package ee.schimke.shokz.data

import android.content.Context
import com.google.modernstorage.storage.toUri
import dev.zacsweers.metro.Inject
import okio.Path
import okio.Path.Companion.toOkioPath

@Inject
class AndroidStorageManager(
    val applicationContext: Context,
) : StorageManager {
    val storageManager: android.os.storage.StorageManager =
        applicationContext.getSystemService(android.os.storage.StorageManager::class.java)!!

    override fun getVolume(path: Path): Volume? {
        return runCatching {
            val volume = storageManager.getStorageVolume(path.toUri())
            Volume(
                uuid = volume.uuid,
                volumeName = volume.mediaStoreVolumeName,
                state = volume.state,
                description = volume.getDescription(applicationContext),
                path = volume.directory?.toOkioPath(),
                removable = volume.isRemovable
            )
        }.getOrNull()
    }
}