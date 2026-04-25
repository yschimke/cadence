package ee.schimke.shokz.sync

import okio.FileSystem
import okio.Path

interface StagingArea {
    val fileSystem: FileSystem

    /** Root directory holding all profile staging sub-folders. */
    fun rootDir(): Path

    /** Directory that should hold the materialized contents of [profileSubpath]. */
    fun profileDir(profileSubpath: String): Path

    /** Lists all files (recursive) under [profileSubpath], with paths relative to it. */
    fun listProfileFiles(profileSubpath: String): List<StagedEntry>

    /** Removes the entire profile staging directory if it exists. */
    fun clearProfile(profileSubpath: String)

    /** Total bytes used by [profileSubpath] on disk. */
    fun profileSize(profileSubpath: String): Long
}

data class StagedEntry(
    val absolutePath: Path,
    val relativePath: String,
    val sizeBytes: Long,
)
