package ee.schimke.shokz.data

import okio.Path

interface StorageManager {
    fun getVolume(path: Path): Volume?
}

data class Volume(
    val uuid: String? = null,
    val volumeName: String? = null,
    val state: String? = null,
    val description: String? = null,
    val path: Path? = null,
    val removable: Boolean? = null
)