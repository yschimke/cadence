package ee.schimke.cadence.sync.google

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import okio.buffer

/**
 * Persists the signed-in Google account to a JSON file. Not encrypted — the file is scoped to the
 * user's home directory and the refresh token is only useful to someone who already owns the
 * machine. A platform secret store is a follow-up.
 */
class GoogleAccountStore(private val path: Path, private val fs: FileSystem = FileSystem.SYSTEM) {
  private val json = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
  }
  private val lock = Mutex()

  suspend fun load(): GoogleAccount? =
    withContext(Dispatchers.IO) {
      lock.withLock {
        if (!fs.exists(path)) return@withLock null
        val text = fs.source(path).buffer().use { it.readUtf8() }
        runCatching { json.decodeFromString<GoogleAccount>(text) }.getOrNull()
      }
    }

  suspend fun save(account: GoogleAccount) {
    withContext(Dispatchers.IO) {
      lock.withLock {
        path.parent?.let { fs.createDirectories(it) }
        fs.sink(path).buffer().use { it.writeUtf8(json.encodeToString(account)) }
      }
    }
  }

  suspend fun clear() {
    withContext(Dispatchers.IO) {
      lock.withLock { fs.delete(path, mustExist = false) }
    }
  }
}
