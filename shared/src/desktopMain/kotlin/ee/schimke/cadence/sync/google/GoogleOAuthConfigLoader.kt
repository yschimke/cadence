package ee.schimke.cadence.sync.google

import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import okio.buffer

/**
 * Reads the OAuth client id/secret from env vars (first) or a JSON file at [configFile] (second).
 * Returns null when neither source produced both values, so the UI can nudge the user to set them
 * up before the Sign in button lights up.
 */
class GoogleOAuthConfigLoader(
  private val configFile: Path,
  private val fs: FileSystem = FileSystem.SYSTEM,
  private val env: (String) -> String? = ::systemEnv,
  private val bundled: OAuthConfig? = null,
) {
  private val json = Json { ignoreUnknownKeys = true }

  /**
   * Resolve credentials in priority order: user-set env vars, then a user-provided JSON file, then
   * [bundled] credentials baked into the release build. Returns null when none produced both halves
   * — the UI nudges the user to set them up in that case.
   */
  fun load(): OAuthConfig? {
    val envClient = env("CADENCE_GOOGLE_CLIENT_ID")?.takeIf { it.isNotBlank() }
    val envSecret = env("CADENCE_GOOGLE_CLIENT_SECRET")?.takeIf { it.isNotBlank() }
    if (envClient != null && envSecret != null) {
      return OAuthConfig(clientId = envClient, clientSecret = envSecret)
    }
    if (fs.exists(configFile)) {
      val text = fs.source(configFile).buffer().use { it.readUtf8() }
      val parsed = runCatching { json.decodeFromString<GoogleOAuthFile>(text) }.getOrNull()
      if (parsed != null) {
        val id = parsed.clientId?.trim().orEmpty()
        val secret = parsed.clientSecret?.trim().orEmpty()
        if (id.isNotEmpty() && secret.isNotEmpty()) {
          return OAuthConfig(clientId = id, clientSecret = secret)
        }
      }
    }
    return bundled
  }

  @kotlinx.serialization.Serializable
  private data class GoogleOAuthFile(
    val clientId: String? = null,
    val clientSecret: String? = null,
  )
}

private fun systemEnv(name: String): String? = System.getenv(name)
