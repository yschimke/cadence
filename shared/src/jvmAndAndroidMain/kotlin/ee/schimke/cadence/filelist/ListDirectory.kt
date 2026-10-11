package ee.schimke.cadence.filelist

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path

actual suspend fun listDirectory(fileSystem: FileSystem?, path: Path): List<DirectoryEntry> =
  withContext(Dispatchers.IO) {
    requireNotNull(fileSystem).list(path).map {
      DirectoryEntry(it, fileSystem.metadata(it).isDirectory)
    }
  }
