package ee.schimke.cadence.auth

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CreateRestoreCredentialRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetRestoreCredentialOption
import androidx.credentials.RestoreCredential
import androidx.credentials.exceptions.NoCredentialException
import androidx.credentials.exceptions.restorecredential.E2eeUnavailableException
import java.security.SecureRandom
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * [RestoreKeyEscrow] on Credential Manager's Restore Credentials.
 *
 * A restore credential is a WebAuthn-shaped key pair that Play services keeps for this package and
 * signing certificate, backs up end-to-end encrypted, and returns to the same app on a new phone.
 * Nothing here talks to a server: the challenge is random and nobody checks the signature. What
 * matters is the WebAuthn `user.id`, an opaque buffer of up to 64 bytes (WebAuthn §5.4.3) that the
 * assertion returns verbatim as `userHandle`. The 32-byte key rides in it.
 *
 * Reading it back with [GetRestoreCredentialOption] is the "restore key retrieval" Play checks for.
 * Credential Manager deletes the credential when the app is uninstalled, so a reinstall on the same
 * phone signs in again rather than restoring silently.
 */
class CredentialManagerRestoreKeyEscrow(context: Context) : RestoreKeyEscrow {

  private val context = context.applicationContext
  private val credentialManager by lazy { CredentialManager.create(this.context) }
  private val json = Json { ignoreUnknownKeys = true }

  override suspend fun store(key: ByteArray): RestoreKeyEscrow.Stored {
    require(key.size == CredentialVault.DATA_KEY_BYTES) { "Expected a 32-byte key" }
    val requestJson = createRequestJson(key)
    return try {
      try {
        credentialManager.createCredential(
          context,
          CreateRestoreCredentialRequest(requestJson, isCloudBackupEnabled = true),
        )
        RestoreKeyEscrow.Stored.CLOUD
      } catch (_: E2eeUnavailableException) {
        // No screen lock, so no end-to-end encrypted cloud backup. Device-to-device transfer is
        // always end-to-end, so escrow for that alone; the vault asks for the cloud again later.
        credentialManager.createCredential(
          context,
          CreateRestoreCredentialRequest(requestJson, isCloudBackupEnabled = false),
        )
        RestoreKeyEscrow.Stored.DEVICE_ONLY
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      // No Play services, or one too old for Restore Credentials. The vault still works on this
      // device; a restored phone signs in again.
      Log.w(TAG, "Can't escrow the credential vault key; a restored phone will sign in again", e)
      RestoreKeyEscrow.Stored.FAILED
    }
  }

  override suspend fun recover(): RestoreKeyEscrow.Recovery {
    val requestJson =
      buildJsonObject {
          put("challenge", randomChallenge())
          put("rpId", RP_ID)
          putJsonArray("allowCredentials") {}
          put("userVerification", "discouraged")
        }
        .toString()
    return try {
      val response =
        credentialManager.getCredential(
          context,
          GetCredentialRequest(listOf(GetRestoreCredentialOption(requestJson))),
        )
      val credential =
        response.credential as? RestoreCredential ?: return RestoreKeyEscrow.Recovery.None
      val userHandle =
        json
          .parseToJsonElement(credential.authenticationResponseJson)
          .jsonObject["response"]
          ?.jsonObject
          ?.get("userHandle")
          ?.jsonPrimitive
          ?.content
      val key = userHandle?.let(::fromBase64Url)
      if (key == null || key.size != CredentialVault.DATA_KEY_BYTES) {
        Log.w(TAG, "The restore credential doesn't carry a credential vault key")
        RestoreKeyEscrow.Recovery.None
      } else {
        RestoreKeyEscrow.Recovery.Found(key)
      }
    } catch (_: NoCredentialException) {
      RestoreKeyEscrow.Recovery.None
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.w(TAG, "Can't read the restore credential", e)
      RestoreKeyEscrow.Recovery.Unavailable
    }
  }

  override suspend fun clear() {
    try {
      credentialManager.clearCredentialState(
        ClearCredentialStateRequest(ClearCredentialStateRequest.TYPE_CLEAR_RESTORE_CREDENTIAL)
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.w(TAG, "Can't clear the restore credential", e)
    }
  }

  private fun createRequestJson(key: ByteArray): String =
    buildJsonObject {
        put("challenge", randomChallenge())
        putJsonObject("rp") {
          put("id", RP_ID)
          put("name", "Cadence")
        }
        putJsonObject("user") {
          put("id", toBase64Url(key))
          put("name", "cadence-credential-vault")
          put("displayName", "Cadence sign-in")
        }
        putJsonArray("pubKeyCredParams") {
          for (alg in listOf(-7, -257)) {
            add(
              buildJsonObject {
                put("type", "public-key")
                put("alg", alg)
              }
            )
          }
        }
        put("timeout", 20_000)
        put("attestation", "none")
        put("excludeCredentials", buildJsonArray {})
        putJsonObject("authenticatorSelection") {
          put("residentKey", "required")
          put("requireResidentKey", JsonPrimitive(true))
          put("userVerification", "discouraged")
        }
      }
      .toString()

  private companion object {
    const val TAG = "RestoreKeyEscrow"

    /**
     * The relying party the credential is created for. The restore key is bound to this app's
     * package and signing certificate, not checked against a server, so this only has to be stable:
     * changing it orphans keys already escrowed. It is the host of the app's web pages.
     */
    const val RP_ID = "yschimke.github.io"

    const val BASE64_URL = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING

    fun randomChallenge(): String = toBase64Url(ByteArray(32).also(SecureRandom()::nextBytes))

    fun toBase64Url(bytes: ByteArray): String = Base64.encodeToString(bytes, BASE64_URL)

    fun fromBase64Url(text: String): ByteArray? =
      try {
        Base64.decode(text, BASE64_URL)
      } catch (_: IllegalArgumentException) {
        null
      }
  }
}
