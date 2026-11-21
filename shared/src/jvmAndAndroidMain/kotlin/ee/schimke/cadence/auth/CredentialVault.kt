package ee.schimke.cadence.auth

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Keeps sign-in secrets (the Google Drive refresh token, Pocket Casts and Libro.fm sessions)
 * encrypted, so they survive a move to a new phone without the backup alone being enough to read
 * them: Google Play's zero-tap sign-in restoration.
 *
 * Envelope encryption, with two files and two keys:
 * - The **vault** ([vaultFile], backed up and transferred) holds each slot's secret as AES-256-GCM
 *   ciphertext under a random 32-byte data key, bound to the slot name. Without that key the
 *   backed-up copy is unreadable.
 * - The **data key** reaches a device two ways. On this device it is wrapped by [keyWrapper] (an
 *   AndroidKeyStore key, hardware-backed on most phones) in [dataKeyFile], which is never backed
 *   up. For a new phone it is escrowed by [escrow] (Credential Manager's Restore Credentials),
 *   which Play services backs up end-to-end encrypted, separately from the app's backup.
 *
 * On a restored phone the vault comes back but the wrapped data key doesn't. The first read
 * recovers the key from the escrow, wraps it under this device's Keystore and reads on, so the app
 * is still signed in. With nothing escrowed (no Play services, or the app was uninstalled, which
 * deletes its restore credential) the unreadable secrets are dropped and the user signs in again;
 * the rest of the restored library is kept.
 *
 * Clearing the last slot forgets the data key, here and in the escrow, so a signed-out user isn't
 * signed back in by a later restore.
 */
class CredentialVault(
  private val vaultFile: File,
  private val dataKeyFile: File,
  /** Present once the current data key is escrowed; missing means escrow is still owed. */
  private val escrowedFile: File,
  private val keyWrapper: KeyWrapper,
  private val escrow: RestoreKeyEscrow,
  private val log: (message: String, error: Throwable?) -> Unit = { _, _ -> },
) {

  /** Wraps the data key for this device only. */
  interface KeyWrapper {
    fun wrap(raw: ByteArray): Sealed

    /**
     * The unwrapped key, or null when this device can't unwrap it (a restored phone, a lost key).
     */
    fun unwrap(sealed: Sealed): ByteArray?
  }

  /** An AES-GCM ciphertext and its IV. */
  class Sealed(val iv: ByteArray, val ciphertext: ByteArray)

  private val mutex = Mutex()
  private val json = Json { ignoreUnknownKeys = true }
  private val random = SecureRandom()

  /** Whether this process already tried to escrow a key that wasn't marked escrowed. */
  private var escrowRetried = false

  /** The secret stored in [slot], or null when there is none (or it can't be read any more). */
  suspend fun get(slot: String): String? = locked {
    val entry = readVaultLocked().entries[slot] ?: return@locked null
    val dataKey = dataKeyLocked(create = false) ?: return@locked null
    val plaintext = dataKey.open(slot, entry)
    if (plaintext == null) {
      log("The stored secret for $slot can't be decrypted; signing in again", null)
      clearLocked(slot)
      return@locked null
    }
    // A read whose key isn't escrowed yet (it failed at sign-in, or the key was just recovered on
    // a new phone) escrows it, once per process, so a secret that is never rewritten doesn't leave
    // the vault unrecoverable on the next phone.
    if (!escrowRetried && !escrowedFile.exists()) {
      escrowRetried = true
      escrowLocked(dataKey)
    }
    plaintext.decodeToString()
  }

  /** Stores [secret] in [slot], replacing what was there. */
  suspend fun put(slot: String, secret: String): Unit = locked {
    val vault = readVaultLocked()
    val dataKey = dataKeyLocked(create = true)!!
    val current = vault.entries[slot]
    if (current != null && dataKey.open(slot, current)?.decodeToString() == secret) {
      // Unchanged: the library saves often, so don't rewrite the vault or escrow again.
      if (!escrowedFile.exists()) escrowLocked(dataKey)
      return@locked
    }
    val sealed = dataKey.seal(slot, secret.encodeToByteArray())
    // Read again: dataKeyLocked may have dropped secrets no key opens.
    writeVaultLocked(
      VaultData(
        readVaultLocked().entries + (slot to Entry(sealed.iv.b64(), sealed.ciphertext.b64())),
      ),
    )
    if (!escrowedFile.exists()) escrowLocked(dataKey)
  }

  /** Forgets [slot]. Forgetting the last one forgets the data key too. */
  suspend fun clear(slot: String): Unit = locked { clearLocked(slot) }

  private suspend fun <T> locked(block: suspend () -> T): T = mutex.withLock {
    withContext(Dispatchers.IO) { block() }
  }

  private suspend fun clearLocked(slot: String) {
    val vault = readVaultLocked()
    val remaining = vault.entries - slot
    if (remaining.size != vault.entries.size || !vaultFile.exists()) {
      writeVaultLocked(VaultData(remaining))
    }
    if (remaining.isEmpty() && (dataKeyFile.exists() || escrowedFile.exists())) {
      dataKeyFile.delete()
      escrowedFile.delete()
      escrow.clear()
    }
  }

  /**
   * The vault's data key, or null when there is none and [create] is false.
   *
   * Unwrapped from [dataKeyFile] on this device. When that is missing or unreadable (a restored
   * phone, or a Keystore key the OS dropped) and the vault holds secrets, it is recovered from the
   * escrow and wrapped under this device's key. Secrets no key can read are dropped; a new key is
   * made only when [create] asks for one.
   */
  private suspend fun dataKeyLocked(create: Boolean): DataKey? {
    unwrapStoredKeyLocked()?.let {
      return it
    }
    val vault = readVaultLocked()
    val sample = vault.entries.entries.firstOrNull()
    if (sample != null) {
      when (val recovery = escrow.recover()) {
        is RestoreKeyEscrow.Recovery.Found -> {
          val recovered = DataKey(recovery.key)
          if (
            recovery.key.size == DATA_KEY_BYTES && recovered.open(sample.key, sample.value) != null
          ) {
            log("Recovered the credential vault key from the restore credential", null)
            wrapAndStoreLocked(recovered)
            return recovered
          }
          log("The escrowed key doesn't open this vault; signing in again", null)
        }
        RestoreKeyEscrow.Recovery.None ->
          log("No escrowed key for the restored credential vault; signing in again", null)
        RestoreKeyEscrow.Recovery.Unavailable -> {
          // Maybe Play services isn't ready yet: keep the secrets for a later read, unless a
          // sign-in needs a key now.
          if (!create) return null
        }
      }
      writeVaultLocked(VaultData())
    }
    if (!create) return null
    return DataKey(ByteArray(DATA_KEY_BYTES).also(random::nextBytes)).also(::wrapAndStoreLocked)
  }

  private fun unwrapStoredKeyLocked(): DataKey? {
    val file = dataKeyFile
    if (!file.exists()) return null
    val wrapped =
      try {
        json.decodeFromString(WrappedKey.serializer(), file.readText())
      } catch (e: IllegalArgumentException) {
        log("The credential vault key file can't be parsed", e)
        null
      }
    val raw = wrapped?.let { keyWrapper.unwrap(Sealed(it.iv.unb64(), it.ciphertext.unb64())) }
    if (raw == null || raw.size != DATA_KEY_BYTES) {
      log("The credential vault key can't be unwrapped on this install", null)
      file.delete()
      escrowedFile.delete()
      return null
    }
    return DataKey(raw)
  }

  private suspend fun escrowLocked(dataKey: DataKey) {
    if (escrow.store(dataKey.raw)) {
      escrowedFile.parentFile?.mkdirs()
      escrowedFile.createNewFile()
    } else {
      escrowedFile.delete()
    }
  }

  private fun wrapAndStoreLocked(dataKey: DataKey) {
    // A newly made or recovered key: not known to be escrowed until escrowLocked says so.
    escrowedFile.delete()
    escrowRetried = false
    val sealed = keyWrapper.wrap(dataKey.raw)
    writeAtomically(
      dataKeyFile,
      json.encodeToString(
        WrappedKey.serializer(),
        WrappedKey(sealed.iv.b64(), sealed.ciphertext.b64()),
      ),
    )
  }

  private fun readVaultLocked(): VaultData {
    val file = vaultFile
    if (!file.exists()) return VaultData()
    // An I/O error propagates: reading it as an empty vault would sign the user out, and the next
    // write would put that over a file that may read fine on a retry. Only content that can't be
    // parsed (a SerializationException is an IllegalArgumentException) reads as empty.
    val text = file.readText()
    return try {
      json.decodeFromString(VaultData.serializer(), text)
    } catch (e: IllegalArgumentException) {
      log("Credential vault can't be parsed; starting empty", e)
      VaultData()
    }
  }

  private fun writeVaultLocked(data: VaultData) {
    writeAtomically(vaultFile, json.encodeToString(VaultData.serializer(), data))
  }

  private fun DataKey.seal(slot: String, plaintext: ByteArray): Sealed {
    val iv = ByteArray(GCM_IV_BYTES).also(random::nextBytes)
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
    cipher.updateAAD(slot.encodeToByteArray())
    return Sealed(iv, cipher.doFinal(plaintext))
  }

  /** The plaintext, or null when this key doesn't open [entry] (another key, or tampering). */
  private fun DataKey.open(slot: String, entry: Entry): ByteArray? =
    try {
      val cipher = Cipher.getInstance(TRANSFORMATION)
      cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, entry.iv.unb64()))
      cipher.updateAAD(slot.encodeToByteArray())
      cipher.doFinal(entry.ciphertext.unb64())
    } catch (_: GeneralSecurityException) {
      null
    } catch (_: IllegalArgumentException) {
      null
    }

  private class DataKey(val raw: ByteArray) {
    val key = SecretKeySpec(raw, "AES")
  }

  @Serializable private data class WrappedKey(val iv: String, val ciphertext: String)

  @Serializable private data class Entry(val iv: String, val ciphertext: String)

  @Serializable private data class VaultData(val entries: Map<String, Entry> = emptyMap())

  companion object {
    const val DATA_KEY_BYTES = 32
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val GCM_IV_BYTES = 12

    private fun ByteArray.b64(): String = Base64.getEncoder().encodeToString(this)

    private fun String.unb64(): ByteArray = Base64.getDecoder().decode(this)

    internal fun writeAtomically(file: File, text: String) {
      val dir = file.absoluteFile.parentFile
      dir.mkdirs()
      val temp = File.createTempFile(file.name, ".tmp", dir)
      try {
        temp.outputStream().use {
          it.write(text.encodeToByteArray())
          it.fd.sync()
        }
        try {
          Files.move(
            temp.toPath(),
            file.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE,
          )
        } catch (_: AtomicMoveNotSupportedException) {
          Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
      } finally {
        temp.delete()
      }
    }
  }
}

/**
 * Carries [CredentialVault]'s data key to a new phone. The vault's ciphertext travels in the app's
 * backup; its key must not, or the backup alone would hold the secrets.
 */
interface RestoreKeyEscrow {

  /** Escrows [key] (32 bytes), replacing any key escrowed before. False when it can't. */
  suspend fun store(key: ByteArray): Boolean

  /** The escrowed key, read back on a restored phone. */
  suspend fun recover(): Recovery

  /** Forgets the escrowed key, so a signed-out user isn't signed back in by a later restore. */
  suspend fun clear()

  sealed interface Recovery {
    /** The key [store] escrowed. */
    class Found(val key: ByteArray) : Recovery

    /** Nothing is escrowed: none was stored, it was cleared, or the app was uninstalled. */
    data object None : Recovery

    /** The escrow couldn't answer (no Play services, a transient error). Try again later. */
    data object Unavailable : Recovery
  }

  /** No escrow: the key stays on this device, and a restored phone signs in again. */
  object Disabled : RestoreKeyEscrow {
    override suspend fun store(key: ByteArray): Boolean = false

    override suspend fun recover(): Recovery = Recovery.None

    override suspend fun clear() {}
  }
}
