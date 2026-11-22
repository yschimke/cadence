package ee.schimke.cadence.podcasts

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/**
 * Splits the sign-in sessions out of the stored library, so a platform can keep them encrypted
 * apart from it, and merges them back.
 */
object PodcastSecrets {
  private const val POCKET_CASTS = "pocketCasts"
  private const val LIBRO_FM = "libroFm"
  private const val AUDIOBOOKSHELF = "audiobookshelf"
  private const val SUBSONIC = "subsonic"
  private const val JELLYFIN = "jellyfin"
  private const val PLEX = "plex"

  private val defaults by lazy {
    mapOf(
      POCKET_CASTS to
        podcastJson.encodeToJsonElement(PocketCastsSession.serializer(), PocketCastsSession()),
      LIBRO_FM to podcastJson.encodeToJsonElement(LibroFmSession.serializer(), LibroFmSession()),
      AUDIOBOOKSHELF to
        podcastJson.encodeToJsonElement(
          AudiobookshelfSession.serializer(),
          AudiobookshelfSession(),
        ),
      SUBSONIC to podcastJson.encodeToJsonElement(SubsonicSession.serializer(), SubsonicSession()),
      JELLYFIN to podcastJson.encodeToJsonElement(JellyfinSession.serializer(), JellyfinSession()),
      PLEX to podcastJson.encodeToJsonElement(PlexSession.serializer(), PlexSession()),
    )
  }

  class Split(
    /** The library without its sessions. */
    val library: String,
    /** The signed-in sessions, or null when there are none. */
    val secrets: String?,
    /** Whether [library] differs from the input: it carried sessions, even signed-out ones. */
    val changed: Boolean,
  )

  fun split(state: String): Split {
    val stored = podcastJson.parseToJsonElement(state).jsonObject
    val sessions = stored.filterKeys { it in defaults }
    val signedIn = sessions.filter { (key, value) -> value != defaults[key] }
    return Split(
      library = JsonObject(stored - defaults.keys).toString(),
      secrets = signedIn.takeIf { it.isNotEmpty() }?.let { JsonObject(it).toString() },
      changed = sessions.isNotEmpty(),
    )
  }

  fun merge(library: String, secrets: String?): String {
    if (secrets == null) return library
    val sessions = podcastJson.parseToJsonElement(secrets).jsonObject.filterKeys { it in defaults }
    return JsonObject(podcastJson.parseToJsonElement(library).jsonObject + sessions).toString()
  }
}
