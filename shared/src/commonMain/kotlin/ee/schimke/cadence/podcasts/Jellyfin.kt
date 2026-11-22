package ee.schimke.cadence.podcasts

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/**
 * Jellyfin's official REST API (https://api.jellyfin.org/) for self-hosted music and audiobooks.
 * Uses `POST /Users/AuthenticateByName` and the modern `Authorization: MediaBrowser ...
 * Token="..."` header for browsing playlists, albums, favorites and audiobooks, and DRM-free
 * downloads.
 */
const val JELLYFIN_BACKEND = "jellyfin"

const val JELLYFIN_AUDIOBOOKS = "audiobooks"
const val JELLYFIN_FAVORITES = "favorites"
const val JELLYFIN_PLAYLIST_PREFIX = "playlist:"
const val JELLYFIN_ALBUM_PREFIX = "album:"

private const val JELLYFIN_CLIENT_HEADER =
  """MediaBrowser Client="Cadence", Device="Cadence", DeviceId="cadence", Version="1.0""""

/** Server URL, access token and user id, kept encrypted in [PodcastSecrets]. */
@Serializable
data class JellyfinSession(
  val server: String = "",
  val accessToken: String = "",
  val userId: String = "",
  val username: String = "",
) {
  val signedIn
    get() = server.isNotBlank() && accessToken.isNotBlank() && userId.isNotBlank()

  val baseUrl
    get() = server.trimEnd('/')
}

data class JellyfinCollection(
  val id: String,
  val name: String,
  val subtitle: String = "",
  val kind: String = "",
)

data class JellyfinItem(
  val id: String,
  val name: String,
  val type: String,
  val album: String,
  val albumId: String,
  val artist: String,
  val overview: String,
  val year: String,
  val container: String,
  val runTimeTicks: Long,
  val playbackPositionTicks: Long,
  val size: Long,
)

fun jellyfinAuthHeader(accessToken: String = ""): String =
  if (accessToken.isBlank()) JELLYFIN_CLIENT_HEADER
  else """$JELLYFIN_CLIENT_HEADER, Token="$accessToken""""

private fun JsonObject.str(name: String) = this[name]?.jsonPrimitive?.contentOrNull.orEmpty()

private fun JsonObject.lng(name: String) = this[name]?.jsonPrimitive?.longOrNull ?: 0L

private fun jellyfinObject(body: String): JsonObject =
  runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
    ?: throw PodcastProblem("Jellyfin returned an unexpected response")

/** Parses `POST /Users/AuthenticateByName`. */
fun jellyfinLogin(body: String, server: String, fallbackUser: String): JellyfinSession {
  val root = jellyfinObject(body)
  val token = root.str("AccessToken")
  val user = root["User"] as? JsonObject
  val userId = user?.str("Id").orEmpty()
  val username = user?.str("Name").orEmpty().ifBlank { fallbackUser }
  if (token.isBlank() || userId.isBlank())
    throw PodcastProblem("Jellyfin didn't return an access token and user ID")
  return JellyfinSession(validateHttpsServer(server, "Jellyfin"), token, userId, username)
}

/** Parses `Items` arrays from Jellyfin's `/Users/{id}/Items` or `/Playlists/{id}/Items`. */
fun jellyfinCollections(body: String, kind: String): List<JellyfinCollection> =
  (jellyfinObject(body)["Items"] as? JsonArray).orEmpty().mapNotNull { node ->
    val obj = node as? JsonObject ?: return@mapNotNull null
    val id = obj.str("Id")
    if (id.isBlank()) return@mapNotNull null
    val artist =
      obj.str("AlbumArtist").ifBlank {
        (obj["Artists"] as? JsonArray)
          .orEmpty()
          .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
          .joinToString(", ")
      }
    JellyfinCollection(id, obj.str("Name").ifBlank { "Untitled" }, artist, kind)
  }

fun jellyfinItems(body: String): List<JellyfinItem> =
  (jellyfinObject(body)["Items"] as? JsonArray).orEmpty().mapNotNull { node ->
    val obj = node as? JsonObject ?: return@mapNotNull null
    val id = obj.str("Id")
    if (id.isBlank()) return@mapNotNull null
    val mediaSource = (obj["MediaSources"] as? JsonArray)?.firstOrNull() as? JsonObject
    val container = mediaSource?.str("Container").orEmpty().ifBlank { obj.str("Container") }
    val size = mediaSource?.lng("Size") ?: 0L
    val userData = obj["UserData"] as? JsonObject
    val artist =
      obj.str("AlbumArtist").ifBlank {
        (obj["Artists"] as? JsonArray)
          .orEmpty()
          .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
          .joinToString(", ")
      }
    JellyfinItem(
      id = id,
      name = obj.str("Name").ifBlank { "Untitled audio" },
      type = obj.str("Type"),
      album = obj.str("Album"),
      albumId = obj.str("AlbumId"),
      artist = artist,
      overview = obj.str("Overview"),
      year = obj.str("ProductionYear"),
      container = container,
      runTimeTicks = obj.lng("RunTimeTicks"),
      playbackPositionTicks = userData?.lng("PlaybackPositionTicks") ?: 0L,
      size = size,
    )
  }

internal fun jellyfinExtension(container: String): String =
  when (container.substringBefore(',').trim().lowercase()) {
    "mp3" -> "mp3"
    "m4a",
    "m4b",
    "aac",
    "mp4" -> "m4a"
    "ogg",
    "oga" -> "ogg"
    "opus" -> "opus"
    "flac" -> "flac"
    "wav" -> "wav"
    else -> "mp3"
  }

/**
 * Converts a Jellyfin audio track or audiobook to a Cadence [Episode]. Jellyfin ticks are 100 ns
 * (10,000 ticks per millisecond). [Episode.audioUrl] is credential-free; the `MediaBrowser`
 * `Authorization` header is attached right before downloading.
 */
fun jellyfinToEpisode(server: String, item: JellyfinItem): Episode? {
  val durationMs = item.runTimeTicks / 10_000L
  if (durationMs <= 0) return null
  val base = server.trimEnd('/')
  val ext = jellyfinExtension(item.container)
  val posMs = (item.playbackPositionTicks / 10_000L).coerceIn(0, durationMs)
  val show = listOf(item.artist, item.album).filter { it.isNotBlank() }.joinToString(" — ")
  return Episode(
      backend = JELLYFIN_BACKEND,
      id = item.id,
      title = item.name,
      audioUrl = "$base/Items/${encodeQueryComponent(item.id)}/Download",
      durationMs = durationMs,
      positionMs = posMs,
      revision = "jellyfin:${posMs / 1000}",
      audioVersion = digest("$JELLYFIN_BACKEND|${item.id}|${item.size}|$ext|$durationMs"),
      extension = ext,
      podcastId = "$JELLYFIN_BACKEND:${item.albumId.ifBlank { item.id }}",
      podcastTitle = show,
      summary = item.overview,
      published = item.year,
    )
    .takeIf { runCatching { it.validate() }.isSuccess }
}

/** Calls the Jellyfin REST API; [onSignedOut] runs on HTTP 401. */
class JellyfinApi(
  private val platform: PodcastPlatform,
  private val session: JellyfinSession,
  private val onSignedOut: suspend () -> Unit,
) {
  fun authHeaders(): Map<String, String> =
    mapOf("Authorization" to jellyfinAuthHeader(session.accessToken))

  private suspend fun get(path: String): String {
    if (!session.signedIn) throw PodcastProblem("Sign in to Jellyfin first")
    return try {
      platform.request(session.baseUrl + path, headers = authHeaders())
    } catch (e: PodcastProblem) {
      if (e.status != 401) throw e
      onSignedOut()
      throw PodcastProblem("Your Jellyfin sign-in expired. Sign in again.", e.status)
    }
  }

  suspend fun playlists(): List<JellyfinCollection> =
    jellyfinCollections(
      get("/Users/${session.userId}/Items?IncludeItemTypes=Playlist&Recursive=true"),
      "playlist",
    )

  suspend fun newestAlbums(limit: Int = 20): List<JellyfinCollection> =
    jellyfinCollections(
      get(
        "/Users/${session.userId}/Items?IncludeItemTypes=MusicAlbum&Recursive=true&SortBy=DateCreated&SortOrder=Descending&Limit=$limit",
      ),
      "album",
    )

  suspend fun audiobooks(limit: Int = 100): List<JellyfinItem> =
    jellyfinItems(
      get(
        "/Users/${session.userId}/Items?IncludeItemTypes=AudioBook&Recursive=true&Fields=MediaSources,Overview&SortBy=DateCreated&SortOrder=Descending&Limit=$limit",
      ),
    )

  suspend fun favorites(limit: Int = 100): List<JellyfinItem> =
    jellyfinItems(
      get(
        "/Users/${session.userId}/Items?IncludeItemTypes=Audio,AudioBook&Filters=IsFavorite&Recursive=true&Fields=MediaSources,Overview&Limit=$limit",
      ),
    )

  suspend fun playlistItems(playlistId: String): List<JellyfinItem> =
    jellyfinItems(
      get(
        "/Playlists/${encodeQueryComponent(playlistId)}/Items?UserId=${session.userId}&Fields=MediaSources,Overview",
      ),
    )

  suspend fun albumItems(albumId: String): List<JellyfinItem> =
    jellyfinItems(
      get(
        "/Users/${session.userId}/Items?ParentId=${encodeQueryComponent(albumId)}&IncludeItemTypes=Audio&SortBy=ParentIndexNumber,IndexNumber&Fields=MediaSources,Overview",
      ),
    )

  companion object {
    suspend fun signIn(
      platform: PodcastPlatform,
      server: String,
      username: String,
      password: String,
    ): JellyfinSession {
      val base = validateHttpsServer(server, "Jellyfin")
      val cleanUser = username.trim()
      if (cleanUser.isBlank()) throw PodcastProblem("Enter your Jellyfin username")
      val body = buildJsonObject {
        put("Username", cleanUser)
        put("Pw", password)
      }
      val response =
        try {
          platform.request(
            "$base/Users/AuthenticateByName",
            "POST",
            mapOf(
              "Content-Type" to "application/json",
              "Authorization" to jellyfinAuthHeader(),
            ),
            body.toString(),
          )
        } catch (e: PodcastProblem) {
          if (e.status !in listOf(400, 401, 403)) throw e
          throw PodcastProblem(
            "Jellyfin refused the sign-in. Check your server URL, username and password.",
            e.status,
          )
        }
      return jellyfinLogin(response, base, cleanUser)
    }
  }
}
