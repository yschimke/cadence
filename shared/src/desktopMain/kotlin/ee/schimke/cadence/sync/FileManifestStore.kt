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
 * One JSON file per managed device under [root]. Writes are serialised through [lock] so recording
 * mid-sync doesn't race with another profile's writes, and go through a temp file + atomic rename
 * so a crash during a write leaves the previous version intact instead of a half-written sidecar.
 */
class FileManifestStore(private val root: Path, private val fs: FileSystem = FileSystem.SYSTEM) :
  ManifestStore {
  private val json = Json { prettyPrint = true }
  private val lock = Mutex()

  init {
    fs.createDirectories(root)
  }

  override suspend fun load(deviceId: String): SyncManifest =
    withContext(Dispatchers.IO) { lock.withLock { readUnlocked(deviceId) } }

  override suspend fun recordAll(deviceId: String, entries: Map<String, ManifestEntry>) {
    if (entries.isEmpty()) return
    withContext(Dispatchers.IO) {
      lock.withLock {
        val current = readUnlocked(deviceId)
        val merged = current.copy(entries = current.entries + entries)
        atomicWrite(pathFor(deviceId), json.encodeToString(merged))
      }
    }
  }

  /**
   * Reads the on-disk manifest, or returns an empty one when the file doesn't exist. A file that
   * exists but can't be parsed is renamed to a `.corrupt-<timestamp>` sibling for forensic recovery
   * and the failure surfaced — overwriting silently would wipe every entry we previously saved.
   */
  private fun readUnlocked(deviceId: String): SyncManifest {
    val file = pathFor(deviceId)
    if (!fs.exists(file)) return SyncManifest(deviceId)
    val text = fs.source(file).buffer().use { it.readUtf8() }
    return runCatching { json.decodeFromString<SyncManifest>(text) }
      .getOrElse { cause ->
        val quarantine = file.parent!! / (file.name + ".corrupt-${System.currentTimeMillis()}")
        runCatching { fs.atomicMove(file, quarantine) }
        throw IllegalStateException(
          "Manifest file $file could not be parsed and was moved to $quarantine",
          cause,
        )
      }
  }

  /** tmp + atomicMove so a crash mid-write never leaves [path] half-written. */
  private fun atomicWrite(path: Path, text: String) {
    val tmp = path.parent!! / ("." + path.name + ".cadence-manifest-part")
    try {
      fs.sink(tmp).buffer().use { it.writeUtf8(text) }
      fs.atomicMove(tmp, path)
    } finally {
      fs.delete(tmp, mustExist = false)
    }
  }

  private fun pathFor(deviceId: String): Path = root / "$deviceId.json"
}
