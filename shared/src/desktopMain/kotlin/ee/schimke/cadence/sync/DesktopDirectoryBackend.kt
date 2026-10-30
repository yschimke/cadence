package ee.schimke.cadence.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer

class DesktopDirectoryBackend(override val stagingRoot: String) : DirectoryBackend {
  private val fs = FileSystem.SYSTEM

  override suspend fun files(root: String): List<DirectoryFile> =
    withContext(Dispatchers.IO) {
      val base = root.toPath()
      if (!fs.exists(base)) {
        if (root.startsWith(stagingRoot)) return@withContext emptyList()
        else error("Directory is unavailable: $root")
      }
      fs
        .listRecursively(base, followSymlinks = false)
        .filter { fs.metadata(it).isRegularFile && fs.metadata(it).symlinkTarget == null }
        .map {
          DirectoryFile(
            it.toString(),
            it.relativeTo(base).toString().replace('\\', '/'),
            fs.metadata(it).size ?: 0,
          )
        }
        .toList()
    }

  override suspend fun copy(source: String, target: String) =
    withContext(Dispatchers.IO) {
      val path = target.toPath()
      fs.createDirectories(requireNotNull(path.parent))
      // A failed copy leaves the previous file intact; replace only after the temporary file is
      // complete.
      val temp = path.parent!! / ("." + path.name + ".cadence-part")
      try {
        fs.source(source.toPath()).buffer().use { input ->
          fs.sink(temp).buffer().use { it.writeAll(input) }
        }
        fs.atomicMove(temp, path)
      } finally {
        fs.delete(temp, mustExist = false)
      }
    }

  override suspend fun size(path: String): Long? =
    withContext(Dispatchers.IO) {
      val target = path.toPath()
      if (!fs.exists(target)) null else fs.metadata(target).size
    }

  override suspend fun clear(root: String) =
    withContext(Dispatchers.IO) {
      val path = root.toPath().normalized()
      val base = stagingRoot.toPath().normalized()
      require(path != base) { "Cannot clear the staging root" }
      safeRelativePath(path.relativeTo(base).toString().replace('\\', '/'))
      fs.deleteRecursively(path, mustExist = false)
      fs.createDirectories(path)
    }
}
