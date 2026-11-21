package ee.schimke.cadence.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Log
import java.io.File
import java.security.KeyStore
import java.security.UnrecoverableKeyException
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The app's one [CredentialVault]: the vault in `filesDir` (backed up), its data key wrapped by the
 * AndroidKeyStore in `noBackupFilesDir`, escrowed in Credential Manager's Restore Credentials.
 *
 * `res/xml/data_extraction_rules.xml` in composeApp keeps the vault in the backup and the rest of
 * these files out of it.
 */
object AndroidCredentialVault {
  /** In `filesDir`: backed up and transferred, readable only with the data key. */
  const val VAULT_FILE_NAME = "credential_vault.json"

  /** In `noBackupFilesDir`: the data key, wrapped by the Keystore key. */
  const val DATA_KEY_FILE_NAME = "credential_vault_dek.json"

  /** In `noBackupFilesDir`: marks [DATA_KEY_FILE_NAME]'s key as escrowed. */
  const val ESCROWED_FILE_NAME = "credential_vault_dek.escrowed"

  const val GOOGLE_SLOT = "google"
  const val PODCASTS_SLOT = "podcasts"

  private const val TAG = "CredentialVault"

  @Volatile private var singleton: CredentialVault? = null

  fun instance(context: Context): CredentialVault =
    singleton
      ?: synchronized(this) {
        singleton
          ?: create(context.applicationContext, CredentialManagerRestoreKeyEscrow(context)).also {
            singleton = it
          }
      }

  fun create(context: Context, escrow: RestoreKeyEscrow): CredentialVault =
    CredentialVault(
      vaultFile = File(context.filesDir, VAULT_FILE_NAME),
      dataKeyFile = File(context.noBackupFilesDir, DATA_KEY_FILE_NAME),
      escrowedFile = File(context.noBackupFilesDir, ESCROWED_FILE_NAME),
      keyWrapper = AndroidKeystoreKeyWrapper,
      escrow = escrow,
      log = { message, error -> Log.w(TAG, message, error) },
    )
}

/** Wraps the vault's data key under an AndroidKeyStore AES key, which never leaves this device. */
internal object AndroidKeystoreKeyWrapper : CredentialVault.KeyWrapper {
  private const val KEYSTORE = "AndroidKeyStore"
  private const val KEY_ALIAS = "cadence-credential-vault-v1"
  private const val TRANSFORMATION = "AES/GCM/NoPadding"

  override fun wrap(raw: ByteArray): CredentialVault.Sealed {
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
    val ciphertext = cipher.doFinal(raw)
    return CredentialVault.Sealed(cipher.iv, ciphertext)
  }

  override fun unwrap(sealed: CredentialVault.Sealed): ByteArray? =
    try {
      val cipher = Cipher.getInstance(TRANSFORMATION)
      cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, sealed.iv))
      cipher.doFinal(sealed.ciphertext)
    } catch (e: Exception) {
      // On a restored phone the Keystore key is a new one, so the tag doesn't verify; the OS can
      // also invalidate or drop a key. Anything else (a busy Keystore) propagates and is retried.
      if (
        e is BadPaddingException ||
          e is KeyPermanentlyInvalidatedException ||
          e is UnrecoverableKeyException ||
          e is IllegalArgumentException
      ) {
        null
      } else {
        throw e
      }
    }

  private fun getOrCreateKey(): SecretKey {
    val keystore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
    keystore.getKey(KEY_ALIAS, null)?.let {
      return it as SecretKey
    }
    val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
    generator.init(
      KeyGenParameterSpec.Builder(
          KEY_ALIAS,
          KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(256)
        .build()
    )
    return generator.generateKey()
  }
}
