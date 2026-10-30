package ee.schimke.cadence.sync.google

import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.random.Random
import kotlin.random.asKotlinRandom

/**
 * Convenience that plugs the JVM SHA-256 digest into the common PKCE generator. Defaults to a
 * [SecureRandom]-backed source for the verifier because RFC 7636 §7.1 requires a cryptographic RNG
 * — [Random.Default] is only a PRNG. Tests may still pass a seeded [Random] to assert determinism.
 */
fun generatePkce(random: Random = SecureRandom().asKotlinRandom()): PkceCode =
  PkceCode.generate(random) { input -> MessageDigest.getInstance("SHA-256").digest(input) }
