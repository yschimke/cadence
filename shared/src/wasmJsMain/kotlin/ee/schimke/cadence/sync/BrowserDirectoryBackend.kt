package ee.schimke.cadence.sync

import ee.schimke.cadence.browser.browserCall
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable
data class BrowserFile(
  val location: String,
  val relativePath: String,
  val size: Long,
  val directory: Boolean,
)

fun browserArgs(vararg pairs: Pair<String, String>) = buildJsonObject {
  pairs.forEach { put(it.first, it.second) }
}
  .toString()

class BrowserDirectoryBackend : DirectoryBackend {
  override val stagingRoot = "opfs:staging"

  override suspend fun files(root: String) =
    Json.decodeFromString<List<BrowserFile>>(browserCall("files", browserArgs("root" to root)))
      .map { DirectoryFile(it.location, it.relativePath, it.size) }

  override suspend fun copy(source: String, target: String) {
    browserCall("copy", browserArgs("source" to source, "target" to target))
  }

  override suspend fun clear(root: String) {
    browserCall("clear", browserArgs("root" to root))
  }
}
