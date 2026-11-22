package ee.schimke.cadence.podcasts

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/**
 * Plex Media Server's official REST API (https://developer.plex.tv/pms/) for self-hosted music and
 * audiobooks. Uses `X-Plex-Token` header auth (obtained via plex.tv PIN sign-in or entered
 * directly), reads playlists and audio libraries, downloads DRM-free original files, and supports
 * conflict-checked playback progress updates.
 */
const val PLEX_BACKEND = "plex"

const val PLEX_PLAYLIST_PREFIX = "playlist:"
const val PLEX_SECTION_PREFIX = "section:"
const val PLEX_ALBUM_PREFIX = "album:"

private const val PLEX_TV_API = "https://plex.tv/api/v2"
private const val PLEX_CLIENT_ID = "cadence-audio-sync"

/** Server URL and X-Plex-Token, kept encrypted in [PodcastSecrets]. */
@Serializable
data class PlexSession(
  val server: String = "",
  val token: String = "",
  val serverName: String = "",
) {
  val signedIn
    get() = server.isNotBlank() && token.isNotBlank()

  val baseUrl
    get() = server.trimEnd('/')
}

data class PlexPin(val id: Long, val code: String, val authUrl: String)

data class PlexServerChoice(val name: String, val url: String, val token: String)

data class PlexSection(val key: String, val title: String, val type: String)

data class PlexPlaylist(val ratingKey: String, val title: String, val leafCount: Int)

data class PlexTrack(
  val ratingKey: String,
  val title: String,
  val artist: String,
  val album: String,
  val parentKey: String,
  val summary: String,
  val year: String,
  val durationMs: Long,
  val viewOffsetMs: Long,
  val partKey: String,
  val container: String,
  val size: Long,
)

fun plexHeaders(token: String = ""): Map<String, String> = buildMap {
  put("Accept", "application/json")
  put("X-Plex-Product", "Cadence")
  put("X-Plex-Version", "1.0")
  put("X-Plex-Client-Identifier", PLEX_CLIENT_ID)
  if (token.isNotBlank()) put("X-Plex-Token", token)
}

private fun JsonObject.str(name: String) = this[name]?.jsonPrimitive?.contentOrNull.orEmpty()

private fun JsonObject.lng(name: String) = this[name]?.jsonPrimitive?.longOrNull ?: 0L

private fun JsonObject.num(name: String) = this[name]?.jsonPrimitive?.intOrNull ?: 0

private fun plexContainer(body: String): JsonObject {
  val root =
    runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
      ?: throw PodcastProblem("Plex returned an unexpected response")
  return root["MediaContainer"] as? JsonObject ?: root
}

/** Parses `POST https://plex.tv/api/v2/pins?strong=true`. */
fun plexPin(body: String): PlexPin {
  val root =
    runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
      ?: throw PodcastProblem("Plex.tv returned an unexpected response")
  val id = root.lng("id")
  val code = root.str("code")
  if (id <= 0 || code.isBlank()) throw PodcastProblem("Plex.tv didn't return a sign-in code")
  val authUrl =
    "https://app.plex.tv/auth#?clientID=${encodeQueryComponent(PLEX_CLIENT_ID)}&code=${encodeQueryComponent(code)}&context%5Bdevice%5D%5Bproduct%5D=Cadence"
  return PlexPin(id, code, authUrl)
}

/** Parses `GET https://plex.tv/api/v2/pins/{id}` and returns `authToken` once approved, or null. */
fun plexPinToken(body: String): String? {
  val root =
    runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
      ?: throw PodcastProblem("Plex.tv returned an unexpected response")
  return root.str("authToken").takeIf { it.isNotBlank() }
}

/** Parses `GET https://plex.tv/api/v2/resources?includeHttps=1` for HTTPS Plex Media Servers. */
fun plexServers(body: String, fallbackToken: String): List<PlexServerChoice> {
  val array =
    runCatching { Json.parseToJsonElement(body).jsonArray }.getOrNull() ?: return emptyList()
  return array.mapNotNull { node ->
    val obj = node as? JsonObject ?: return@mapNotNull null
    if (!obj.str("provides").split(',').any { it.trim() == "server" }) return@mapNotNull null
    val name = obj.str("name").ifBlank { "Plex Server" }
    val token = obj.str("accessToken").ifBlank { fallbackToken }
    val connections = (obj["connections"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
    val uri =
      connections
        .sortedBy { it["relay"]?.jsonPrimitive?.booleanOrNull == true }
        .firstOrNull { it.str("uri").startsWith("https://") }
        ?.str("uri") ?: return@mapNotNull null
    PlexServerChoice(name, uri.trimEnd('/'), token)
  }
}

/** Parses `GET /library/sections`, keeping music/audiobook (`type == "artist"`) sections. */
fun plexAudioSections(body: String): List<PlexSection> =
  (plexContainer(body)["Directory"] as? JsonArray).orEmpty().mapNotNull { node ->
    val obj = node as? JsonObject ?: return@mapNotNull null
    val type = obj.str("type")
    if (type != "artist") return@mapNotNull null
    val key = obj.str("key")
    if (key.isBlank()) return@mapNotNull null
    PlexSection(key, obj.str("title").ifBlank { "Music" }, type)
  }

/** Parses `GET /playlists?playlistType=audio`. */
fun plexPlaylists(body: String): List<PlexPlaylist> =
  (plexContainer(body)["Metadata"] as? JsonArray).orEmpty().mapNotNull { node ->
    val obj = node as? JsonObject ?: return@mapNotNull null
    val key = obj.str("ratingKey")
    if (key.isBlank()) return@mapNotNull null
    PlexPlaylist(key, obj.str("title").ifBlank { "Playlist" }, obj.num("leafCount"))
  }

/**
 * Parses `Metadata[]` from `/playlists/{id}/items`, `/library/sections/{id}/all`, or
 * `/library/metadata/{id}`.
 */
fun plexTracks(body: String): Pair<String, List<PlexTrack>> {
  val container = plexContainer(body)
  val title = container.str("title").ifBlank { "Plex" }
  val tracks =
    (container["Metadata"] as? JsonArray).orEmpty().mapNotNull { node ->
      val obj = node as? JsonObject ?: return@mapNotNull null
      val ratingKey = obj.str("ratingKey")
      val media = (obj["Media"] as? JsonArray)?.firstOrNull() as? JsonObject
      val part = (media?.get("Part") as? JsonArray)?.firstOrNull() as? JsonObject
      val partKey = part?.str("key").orEmpty()
      if (ratingKey.isBlank() || partKey.isBlank()) return@mapNotNull null
      val duration = obj.lng("duration").takeIf { it > 0 } ?: part?.lng("duration") ?: 0L
      PlexTrack(
        ratingKey = ratingKey,
        title = obj.str("title").ifBlank { "Untitled track" },
        artist = obj.str("grandparentTitle").ifBlank { obj.str("originalTitle") },
        album = obj.str("parentTitle"),
        parentKey = obj.str("parentRatingKey"),
        summary = obj.str("summary"),
        year = obj.str("parentYear").ifBlank { obj.str("year") },
        durationMs = duration,
        viewOffsetMs = obj.lng("viewOffset"),
        partKey = partKey,
        container = part?.str("container").orEmpty().ifBlank { media?.str("container").orEmpty() },
        size = part?.lng("size") ?: 0L,
      )
    }
  return title to tracks
}

/**
 * Converts a Plex audio track or audiobook to a Cadence [Episode]. [Episode.audioUrl] is
 * credential-free; `X-Plex-Token` is attached in [Episode.downloadHeaders] right before download.
 */
fun plexToEpisode(server: String, track: PlexTrack): Episode? {
  if (track.durationMs <= 0 || !track.partKey.startsWith("/")) return null
  val base = server.trimEnd('/')
  val ext = jellyfinExtension(track.container)
  val posMs = track.viewOffsetMs.coerceIn(0, track.durationMs)
  val show = listOf(track.artist, track.album).filter { it.isNotBlank() }.joinToString(" — ")
  return Episode(
      backend = PLEX_BACKEND,
      id = track.ratingKey,
      title = track.title,
      audioUrl = "$base${track.partKey}?download=1",
      durationMs = track.durationMs,
      positionMs = posMs,
      revision = "plex:${posMs / 1000}",
      audioVersion =
        digest("$PLEX_BACKEND|${track.ratingKey}|${track.size}|$ext|${track.durationMs}"),
      extension = ext,
      podcastId = "$PLEX_BACKEND:${track.parentKey.ifBlank { track.ratingKey }}",
      podcastTitle = show,
      summary = track.summary,
      published = track.year,
    )
    .takeIf { runCatching { it.validate() }.isSuccess }
}

/** Calls the Plex Media Server API; [onSignedOut] runs on HTTP 401. */
class PlexApi(
  private val platform: PodcastPlatform,
  private val session: PlexSession,
  private val onSignedOut: suspend () -> Unit,
) {
  fun authHeaders(): Map<String, String> = plexHeaders(session.token)

  private suspend fun get(path: String): String {
    if (!session.signedIn) throw PodcastProblem("Sign in to Plex first")
    return try {
      platform.request(session.baseUrl + path, headers = authHeaders())
    } catch (e: PodcastProblem) {
      if (e.status != 401) throw e
      onSignedOut()
      throw PodcastProblem("Your Plex token was refused. Sign in again.", e.status)
    }
  }

  suspend fun audioSections(): List<PlexSection> = plexAudioSections(get("/library/sections"))

  suspend fun playlists(): List<PlexPlaylist> = plexPlaylists(get("/playlists?playlistType=audio"))

  suspend fun playlistItems(ratingKey: String): Pair<String, List<PlexTrack>> =
    plexTracks(get("/playlists/${encodeQueryComponent(ratingKey)}/items"))

  suspend fun sectionTracks(sectionKey: String, limit: Int = 50): Pair<String, List<PlexTrack>> =
    plexTracks(
      get(
        "/library/sections/${encodeQueryComponent(sectionKey)}/all?type=10&sort=addedAt:desc&X-Plex-Container-Start=0&X-Plex-Container-Size=$limit",
      ),
    )

  suspend fun track(ratingKey: String): PlexTrack? =
    plexTracks(get("/library/metadata/${encodeQueryComponent(ratingKey)}")).second.firstOrNull()

  suspend fun sendPosition(ratingKey: String, positionMs: Long, durationMs: Long) {
    val state = if (durationMs > 0 && positionMs >= durationMs - 1000) "stopped" else "paused"
    get(
      "/:/progress?key=${encodeQueryComponent(ratingKey)}&identifier=com.plexapp.plugins.library&time=$positionMs&state=$state",
    )
  }

  companion object {
    suspend fun signInWithToken(
      platform: PodcastPlatform,
      server: String,
      token: String,
      serverName: String = "",
    ): PlexSession {
      val base = validateHttpsServer(server, "Plex")
      val cleanToken = token.trim()
      if (cleanToken.isBlank()) throw PodcastProblem("Enter your X-Plex-Token")
      try {
        platform.request("$base/library/sections", headers = plexHeaders(cleanToken))
      } catch (e: PodcastProblem) {
        if (e.status !in listOf(400, 401, 403)) throw e
        throw PodcastProblem(
          "Plex refused the token. Check your server URL and X-Plex-Token.",
          e.status,
        )
      }
      return PlexSession(base, cleanToken, serverName)
    }

    suspend fun startPin(platform: PodcastPlatform): PlexPin =
      plexPin(platform.request("$PLEX_TV_API/pins?strong=true", "POST", plexHeaders()))

    suspend fun pollPin(platform: PodcastPlatform, pinId: Long): List<PlexServerChoice>? {
      val token =
        plexPinToken(platform.request("$PLEX_TV_API/pins/$pinId", headers = plexHeaders()))
          ?: return null
      val servers =
        plexServers(
          platform.request("$PLEX_TV_API/resources?includeHttps=1", headers = plexHeaders(token)),
          token,
        )
      if (servers.isEmpty())
        throw PodcastProblem("No HTTPS Plex Media Servers were found on this Plex account")
      return servers
    }
  }
}
