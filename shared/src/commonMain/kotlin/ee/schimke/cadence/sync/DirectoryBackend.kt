package ee.schimke.cadence.sync

/** Paths are platform locators: desktop absolute paths, browser handle keys. */
interface DirectoryBackend {
  val stagingRoot: String

  suspend fun files(root: String): List<DirectoryFile>

  suspend fun copy(source: String, target: String)

  suspend fun clear(root: String)

  /** Size in bytes if the file exists at [path], else null. Used to skip unchanged copies. */
  suspend fun size(path: String): Long? = null
}

data class DirectoryFile(val location: String, val relativePath: String, val size: Long)

/** Reject traversal before joining data from settings or external directory listings. */
fun safeRelativePath(path: String): String {
  require(
    path.isNotBlank() && !path.startsWith('/') && !path.contains('\\') && !path.contains(':')
  ) {
    "Invalid relative path: $path"
  }
  val parts = path.split('/')
  require(parts.none { it.isEmpty() || it == "." || it == ".." }) { "Invalid relative path: $path" }
  return path
}

fun joinDirectory(root: String, relative: String): String =
  root.trimEnd('/', '\\') + "/" + safeRelativePath(relative)
