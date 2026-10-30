package ee.schimke.cadence.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import okio.buffer

/**
 * One JSON file per managed device under [root]. Writes are serialized through [lock] so recording
 * mid-sync doesn't race with another profile's writes.
 */
class FileManifestStore(private val root: Path, private val fs: FileSystem = FileSystem.SYSTEM) :
  ManifestStore {
  private val json = Json { prettyPrint = true }
  private val lock = Mutex()

  init {
    fs.createDirectories(root)
  }

  override suspend fun load(deviceId: String): SyncManifest =
    withContext(Dispatchers.IO) {
      lock.withLock { readUnlocked(deviceId) }
    }

  override suspend fun record(deviceId: String, relPath: String, entry: ManifestEntry) {
    withContext(Dispatchers.IO) {
      lock.withLock {
        val current = readUnlocked(deviceId)
        val updated = current.copy(entries = current.entries + (relPath to entry))
        fs.sink(pathFor(deviceId)).buffer().use { it.writeUtf8(json.encodeToString(updated)) }
      }
    }
  }

  private fun readUnlocked(deviceId: String): SyncManifest {
    val file = pathFor(deviceId)
    if (!fs.exists(file)) return SyncManifest(deviceId)
    val text = fs.source(file).buffer().use { it.readUtf8() }
    return runCatching { json.decodeFromString<SyncManifest>(text) }
      .getOrDefault(SyncManifest(deviceId))
  }

  private fun pathFor(deviceId: String): Path = root / "$deviceId.json"
}
