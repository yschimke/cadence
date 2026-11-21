package ee.schimke.cadence.auth

import java.io.File
import java.nio.file.Files
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class CredentialVaultTest {
  private val root = Files.createTempDirectory("vault").toFile()
  private val oldPhone = Phone(File(root, "old"))
  private val escrow = FakeEscrow()

  @AfterTest fun cleanUp() = check(root.deleteRecursively())

  @Test
  fun storesAndReadsSlots() = runBlocking {
    val vault = oldPhone.vault(escrow)
    vault.put("google", "refresh-1")
    vault.put("podcasts", "{\"a\":1}")

    assertEquals("refresh-1", vault.get("google"))
    assertEquals("{\"a\":1}", vault.get("podcasts"))
    assertFalse(oldPhone.vaultFile.readText().contains("refresh-1"), "Stored in the clear")
    assertTrue(oldPhone.escrowedFile.exists())
    assertEquals(1, escrow.stores)
  }

  @Test
  fun unchangedPutDoesNotEscrowAgain() = runBlocking {
    val vault = oldPhone.vault(escrow)
    vault.put("google", "refresh-1")
    vault.put("google", "refresh-1")
    vault.put("google", "refresh-2")
    assertEquals(1, escrow.stores)
    assertEquals("refresh-2", vault.get("google"))
  }

  @Test
  fun restoredPhoneRecoversTheKeyFromTheEscrow() = runBlocking {
    oldPhone.vault(escrow).put("google", "refresh-1")

    val newPhone = oldPhone.restoreTo(File(root, "new"))
    val vault = newPhone.vault(escrow)

    assertEquals("refresh-1", vault.get("google"))
    assertTrue(newPhone.dataKeyFile.exists(), "Rewrapped under the new phone's key")
    // The recovered key is escrowed again from the new phone, once.
    assertTrue(newPhone.escrowedFile.exists())
    assertEquals("refresh-1", newPhone.vault(FakeEscrow()).get("google"))
  }

  @Test
  fun restoredPhoneWithoutEscrowSignsInAgain() = runBlocking {
    oldPhone.vault(RestoreKeyEscrow.Disabled).put("google", "refresh-1")

    val newPhone = oldPhone.restoreTo(File(root, "new"))
    val vault = newPhone.vault(RestoreKeyEscrow.Disabled)

    assertNull(vault.get("google"))
    vault.put("google", "refresh-2")
    assertEquals("refresh-2", vault.get("google"))
  }

  @Test
  fun unavailableEscrowKeepsSecretsForLater() = runBlocking {
    oldPhone.vault(escrow).put("google", "refresh-1")
    val newPhone = oldPhone.restoreTo(File(root, "new"))

    escrow.unavailable = true
    assertEquals(CredentialVault.Lookup.Pending, newPhone.vault(escrow).lookup("google"))
    assertEquals(CredentialVault.Lookup.Absent, newPhone.vault(escrow).lookup("podcasts"))
    escrow.unavailable = false
    assertEquals("refresh-1", newPhone.vault(escrow).get("google"))
  }

  @Test
  fun wrongEscrowedKeyDropsSecrets() = runBlocking {
    oldPhone.vault(escrow).put("google", "refresh-1")
    val newPhone = oldPhone.restoreTo(File(root, "new"))
    escrow.key = ByteArray(32) { 7 }

    assertNull(newPhone.vault(escrow).get("google"))
  }

  @Test
  fun clearingTheLastSlotForgetsTheKeyAndEscrow() = runBlocking {
    val vault = oldPhone.vault(escrow)
    vault.put("google", "refresh-1")
    vault.put("podcasts", "p")

    vault.clear("google")
    assertTrue(oldPhone.dataKeyFile.exists())
    assertEquals(0, escrow.clears)

    vault.clear("podcasts")
    assertFalse(oldPhone.dataKeyFile.exists())
    assertFalse(oldPhone.escrowedFile.exists())
    assertNull(escrow.key)
    assertEquals(1, escrow.clears)
  }

  @Test
  fun failedEscrowIsRetriedOnARead() = runBlocking {
    escrow.failStores = true
    oldPhone.vault(escrow).put("google", "refresh-1")
    assertFalse(oldPhone.escrowedFile.exists())

    escrow.failStores = false
    assertEquals("refresh-1", oldPhone.vault(escrow).get("google"))
    assertTrue(oldPhone.escrowedFile.exists())
  }

  @Test
  fun vaultSecretFileMovesTheLegacyPlaintextIn() = runBlocking {
    val legacy = File(root, "google-auth.json").apply { writeText("{\"email\":\"a\"}") }
    val file = VaultSecretFile(oldPhone.vault(escrow), "google", legacy)

    assertEquals("{\"email\":\"a\"}", file.read())
    assertFalse(legacy.exists())
    assertEquals("{\"email\":\"a\"}", file.read())

    file.delete()
    assertNull(file.read())
  }

  /** One phone's files, with a stand-in for its Keystore key, which is never backed up. */
  private class Phone(dir: File) {
    val vaultFile = File(dir, "files/credential_vault.json")
    val dataKeyFile = File(dir, "no_backup/dek.json")
    val escrowedFile = File(dir, "no_backup/dek.escrowed")
    private val deviceKey: SecretKey =
      KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    private val wrapper =
      object : CredentialVault.KeyWrapper {
        override fun wrap(raw: ByteArray): CredentialVault.Sealed {
          val cipher = Cipher.getInstance("AES/GCM/NoPadding")
          cipher.init(Cipher.ENCRYPT_MODE, deviceKey)
          return CredentialVault.Sealed(cipher.iv, cipher.doFinal(raw))
        }

        override fun unwrap(sealed: CredentialVault.Sealed): ByteArray? = runCatching {
          val cipher = Cipher.getInstance("AES/GCM/NoPadding")
          cipher.init(Cipher.DECRYPT_MODE, deviceKey, GCMParameterSpec(128, sealed.iv))
          cipher.doFinal(sealed.ciphertext)
        }
          .getOrNull()
      }

    fun vault(escrow: RestoreKeyEscrow) =
      CredentialVault(vaultFile, dataKeyFile, escrowedFile, wrapper, escrow)

    /** What a backup brings to a new phone: files/, not no_backup/ or the Keystore. */
    fun restoreTo(dir: File): Phone = Phone(dir).also { vaultFile.copyTo(it.vaultFile) }
  }

  private class FakeEscrow : RestoreKeyEscrow {
    var key: ByteArray? = null
    var unavailable = false
    var failStores = false
    var stores = 0
    var clears = 0

    override suspend fun store(key: ByteArray): Boolean {
      if (failStores) return false
      stores++
      this.key = key.copyOf()
      return true
    }

    override suspend fun recover(): RestoreKeyEscrow.Recovery =
      when {
        unavailable -> RestoreKeyEscrow.Recovery.Unavailable
        else -> key?.let { RestoreKeyEscrow.Recovery.Found(it) } ?: RestoreKeyEscrow.Recovery.None
      }

    override suspend fun clear() {
      clears++
      key = null
    }
  }
}
