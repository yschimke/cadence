package ee.schimke.cadence.sync.google

import java.security.MessageDigest
import kotlin.random.Random

/** Convenience that plugs the JVM SHA-256 digest into the common PKCE generator. */
fun generatePkce(random: Random = Random.Default): PkceCode =
  PkceCode.generate(random) { input -> MessageDigest.getInstance("SHA-256").digest(input) }
