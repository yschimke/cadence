package ee.schimke.cadence.sync.google

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random

/** PKCE verifier + S256 challenge pair for OAuth 2.0 authorization-code flows. */
@OptIn(ExperimentalEncodingApi::class)
data class PkceCode(val verifier: String, val challenge: String) {
  companion object {
    private val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

    /** Generates a fresh verifier (43-char base64url) and its S256 challenge. */
    fun generate(random: Random = Random.Default, sha256: (ByteArray) -> ByteArray): PkceCode {
      // RFC 7636 §4.1: verifier is 43–128 chars from the unreserved set. 32 random bytes → 43
      // chars.
      val verifierBytes = ByteArray(32).also { random.nextBytes(it) }
      val verifier = base64Url.encode(verifierBytes)
      val challenge = base64Url.encode(sha256(verifier.encodeToByteArray()))
      return PkceCode(verifier = verifier, challenge = challenge)
    }
  }
}
