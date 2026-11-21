package ee.schimke.cadence.auth

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import okio.buffer

/** One secret document: a plain file on desktop, a [CredentialVault] slot on Android. */
interface SecretFile {
  suspend fun read(): String?

  suspend fun write(text: String)

  suspend fun delete()
}

/** A [SecretFile] kept in the clear at [path]. */
class PlainSecretFile(private val path: Path, private val fs: FileSystem = FileSystem.SYSTEM) :
  SecretFile {
  private val lock = Mutex()

  override suspend fun read(): String? =
    withContext(Dispatchers.IO) {
      lock.withLock {
        if (!fs.exists(path)) null else fs.source(path).buffer().use { it.readUtf8() }
      }
    }

  override suspend fun write(text: String) {
    withContext(Dispatchers.IO) {
      lock.withLock {
        path.parent?.let { fs.createDirectories(it) }
        fs.sink(path).buffer().use { it.writeUtf8(text) }
      }
    }
  }

  override suspend fun delete() {
    withContext(Dispatchers.IO) { lock.withLock { fs.delete(path, mustExist = false) } }
  }
}

/**
 * A [SecretFile] in [vault]'s [slot]. A [legacy] plaintext file, from before the vault, is moved in
 * on first read and deleted.
 */
class VaultSecretFile(
  private val vault: CredentialVault,
  private val slot: String,
  private val legacy: File? = null,
) : SecretFile {

  override suspend fun read(): String? {
    vault.get(slot)?.let {
      return it
    }
    val text = withContext(Dispatchers.IO) { legacy?.takeIf { it.isFile }?.readText() }
    if (text != null) {
      vault.put(slot, text)
      withContext(Dispatchers.IO) { legacy?.delete() }
    }
    return text
  }

  override suspend fun write(text: String) {
    vault.put(slot, text)
    withContext(Dispatchers.IO) { legacy?.delete() }
  }

  override suspend fun delete() {
    vault.clear(slot)
    withContext(Dispatchers.IO) { legacy?.delete() }
  }
}
