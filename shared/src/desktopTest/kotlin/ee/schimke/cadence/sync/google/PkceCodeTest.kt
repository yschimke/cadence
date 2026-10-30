package ee.schimke.cadence.sync.google

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalEncodingApi::class)
class PkceCodeTest {
  @Test
  fun verifierIsExpectedLengthAndCharset() {
    val code = generatePkce()
    // 32 random bytes → 43 chars base64url (no padding).
    assertEquals(43, code.verifier.length)
    assertTrue(
      code.verifier.all { it.isLetterOrDigit() || it == '-' || it == '_' },
      "verifier must be base64url: ${code.verifier}",
    )
  }

  @Test
  fun differentCallsReturnDifferentCodes() {
    val a = generatePkce()
    val b = generatePkce()
    assertTrue(a.verifier != b.verifier)
    assertTrue(a.challenge != b.challenge)
  }

  @Test
  fun challengeMatchesS256OfVerifier() {
    val code = generatePkce(Random(42))
    val expected =
      Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
        .encode(
          java.security.MessageDigest.getInstance("SHA-256")
            .digest(code.verifier.encodeToByteArray())
        )
    assertEquals(expected, code.challenge)
  }
}
