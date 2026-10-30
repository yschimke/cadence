package ee.schimke.cadence.sync

/** Paths are platform locators: desktop absolute paths, browser handle keys. */
interface DirectoryBackend {
  val stagingRoot: String

  suspend fun files(root: String): List<DirectoryFile>

  suspend fun copy(source: String, target: String)

  suspend fun clear(root: String)

  /**
   * Size + last-modified-millis for [path], or null if it doesn't exist. Used to skip copies when
   * the target already matches the source. Returning null falls back to always-copy.
   */
  suspend fun metadata(path: String): FileMetadata? = null
}

/** [lastModifiedMillis] is 0 when the backend can't read it. */
data class FileMetadata(val size: Long, val lastModifiedMillis: Long)

data class DirectoryFile(
  val location: String,
  val relativePath: String,
  val size: Long,
  val lastModifiedMillis: Long = 0,
)

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
