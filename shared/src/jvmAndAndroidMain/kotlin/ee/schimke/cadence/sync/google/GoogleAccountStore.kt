package ee.schimke.cadence.sync.google

import ee.schimke.cadence.auth.PlainSecretFile
import ee.schimke.cadence.auth.SecretFile
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path

/**
 * Persists the signed-in Google account. On Android it lives in the encrypted credential vault, so
 * it is restored with the app on a new phone (see `CredentialVault`). On desktop it is a plain JSON
 * file in the user's home directory: the refresh token is only useful to someone who already owns
 * the machine.
 */
class GoogleAccountStore(private val file: SecretFile) {
  constructor(path: Path, fs: FileSystem = FileSystem.SYSTEM) : this(PlainSecretFile(path, fs))

  private val json = Json { ignoreUnknownKeys = true }

  suspend fun load(): GoogleAccount? =
    file.read()?.let { runCatching { json.decodeFromString<GoogleAccount>(it) }.getOrNull() }

  suspend fun save(account: GoogleAccount) {
    file.write(json.encodeToString(account))
  }

  suspend fun clear() {
    file.delete()
  }
}
