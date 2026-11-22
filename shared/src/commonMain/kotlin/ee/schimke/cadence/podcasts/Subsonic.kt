package ee.schimke.cadence.podcasts

import kotlin.random.Random
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okio.ByteString.Companion.encodeUtf8

/**
 * Subsonic / OpenSubsonic REST API (https://opensubsonic.github.io/) for self-hosted music servers
 * such as Navidrome, Airsonic, Gonic, Ampache and Funkwhale. Supports standard salted-MD5 token
 * auth and OpenSubsonic API keys, playlists, starred tracks, newest albums, and DRM-free downloads.
 */
const val SUBSONIC_BACKEND = "subsonic"

const val SUBSONIC_STARRED = "starred"
const val SUBSONIC_PLAYLIST_PREFIX = "playlist:"
const val SUBSONIC_ALBUM_PREFIX = "album:"

private const val SUBSONIC_API_VERSION = "1.16.1"
private const val SUBSONIC_CLIENT = "Cadence"

/**
 * Server credentials, kept encrypted in [PodcastSecrets] on Android and desktop. Either [apiKey]
 * (OpenSubsonic API key) or [username] + [password] (standard Subsonic salted-MD5 auth) is set.
 */
@Serializable
data class SubsonicSession(
  val server: String = "",
  val username: String = "",
  val password: String = "",
  val apiKey: String = "",
) {
  val signedIn
    get() =
      server.isNotBlank() &&
        (apiKey.isNotBlank() || (username.isNotBlank() && password.isNotBlank()))

  val baseUrl
    get() = server.trimEnd('/')
}

data class SubsonicPlaylist(val id: String, val name: String, val songCount: Int)

data class SubsonicAlbum(val id: String, val name: String, val artist: String, val songCount: Int)

data class SubsonicSong(
  val id: String,
  val title: String,
  val artist: String,
  val album: String,
  val albumId: String,
  val durationSeconds: Long,
  val size: Long,
  val suffix: String,
  val contentType: String,
  val year: String = "",
)

internal fun encodeQueryComponent(value: String): String {
  val bytes = value.encodeToByteArray()
  val out = StringBuilder(bytes.size)
  for (b in bytes) {
    val c = b.toInt() and 0xFF
    val unreserved =
      (c in 'a'.code..'z'.code) ||
        (c in 'A'.code..'Z'.code) ||
        (c in '0'.code..'9'.code) ||
        c == '-'.code ||
        c == '_'.code ||
        c == '.'.code ||
        c == '~'.code
    if (unreserved) out.append(c.toChar())
    else {
      out.append('%')
      out.append("0123456789ABCDEF"[c shr 4])
      out.append("0123456789ABCDEF"[c and 0x0F])
    }
  }
  return out.toString()
}

private fun randomSalt(): String {
  val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
  return buildString(12) { repeat(12) { append(chars[Random.nextInt(chars.length)]) } }
}

/** Builds the Subsonic auth query string (excluding leading `?`). */
fun subsonicAuthQuery(
  session: SubsonicSession,
  json: Boolean = true,
  salt: String = randomSalt(),
): String {
  val base =
    if (session.apiKey.isNotBlank()) {
      "apiKey=${encodeQueryComponent(session.apiKey)}"
    } else {
      val token = (session.password + salt).encodeUtf8().md5().hex()
      "u=${encodeQueryComponent(session.username)}&t=$token&s=${encodeQueryComponent(salt)}"
    }
  val format = if (json) "&f=json" else ""
  return "$base&v=$SUBSONIC_API_VERSION&c=$SUBSONIC_CLIENT$format"
}

private fun JsonObject.str(name: String) = this[name]?.jsonPrimitive?.contentOrNull.orEmpty()

private fun JsonObject.lng(name: String) = this[name]?.jsonPrimitive?.longOrNull ?: 0L

private fun JsonObject.num(name: String) = this[name]?.jsonPrimitive?.intOrNull ?: 0

/**
 * Unwraps the `subsonic-response` envelope and throws [PodcastProblem] when `status == "failed"`.
 * Auth errors (code 40/41) carry status 401 so callers can sign out cleanly.
 */
fun subsonicEnvelope(body: String): JsonObject {
  val root =
    runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
      ?: throw PodcastProblem("Subsonic server returned an unexpected response")
  val response =
    root["subsonic-response"] as? JsonObject
      ?: throw PodcastProblem("Server didn't return a Subsonic response. Check the server URL.")
  val status = response.str("status")
  if (status != "ok") {
    val err = response["error"] as? JsonObject
    val code = err?.num("code") ?: 0
    val message = err?.str("message").orEmpty().ifBlank { "Subsonic request failed" }
    val httpLike = if (code == 40 || code == 41) 401 else 0
    throw PodcastProblem(message, httpLike)
  }
  return response
}

fun subsonicPlaylists(body: String): List<SubsonicPlaylist> =
  ((subsonicEnvelope(body)["playlists"] as? JsonObject)?.get("playlist") as? JsonArray)
    .orEmpty()
    .mapNotNull { node ->
      val obj = node as? JsonObject ?: return@mapNotNull null
      val id = obj.str("id")
      if (id.isBlank()) return@mapNotNull null
      SubsonicPlaylist(id, obj.str("name").ifBlank { "Playlist" }, obj.num("songCount"))
    }

fun subsonicAlbums(body: String): List<SubsonicAlbum> =
  ((subsonicEnvelope(body)["albumList2"] as? JsonObject)?.get("album") as? JsonArray)
    .orEmpty()
    .mapNotNull { node ->
      val obj = node as? JsonObject ?: return@mapNotNull null
      val id = obj.str("id")
      if (id.isBlank()) return@mapNotNull null
      SubsonicAlbum(
        id = id,
        name = obj.str("name").ifBlank { obj.str("title").ifBlank { "Album" } },
        artist = obj.str("artist"),
        songCount = obj.num("songCount"),
      )
    }

private fun parseSong(node: JsonElement): SubsonicSong? {
  val obj = node as? JsonObject ?: return null
  val id = obj.str("id")
  if (id.isBlank()) return null
  return SubsonicSong(
    id = id,
    title = obj.str("title").ifBlank { "Untitled track" },
    artist = obj.str("artist"),
    album = obj.str("album"),
    albumId = obj.str("albumId"),
    durationSeconds = obj.lng("duration"),
    size = obj.lng("size"),
    suffix = obj.str("suffix"),
    contentType = obj.str("contentType"),
    year = obj.str("year"),
  )
}

fun subsonicPlaylistSongs(body: String): Pair<String, List<SubsonicSong>> {
  val playlist = subsonicEnvelope(body)["playlist"] as? JsonObject
  val name = playlist?.str("name").orEmpty().ifBlank { "Subsonic playlist" }
  val songs = (playlist?.get("entry") as? JsonArray).orEmpty().mapNotNull(::parseSong)
  return name to songs
}

fun subsonicAlbumSongs(body: String): Pair<String, List<SubsonicSong>> {
  val album = subsonicEnvelope(body)["album"] as? JsonObject
  val name = album?.str("name").orEmpty().ifBlank { "Subsonic album" }
  val songs = (album?.get("song") as? JsonArray).orEmpty().mapNotNull(::parseSong)
  return name to songs
}

fun subsonicStarredSongs(body: String): List<SubsonicSong> =
  ((subsonicEnvelope(body)["starred2"] as? JsonObject)?.get("song") as? JsonArray)
    .orEmpty()
    .mapNotNull(::parseSong)

internal fun subsonicExtension(suffix: String, contentType: String): String {
  val ext = suffix.removePrefix(".").lowercase()
  return when (ext) {
    "mp3" -> "mp3"
    "m4a",
    "m4b",
    "aac",
    "mp4" -> "m4a"
    "ogg" -> "ogg"
    "opus" -> "opus"
    "flac" -> "flac"
    "wav" -> "wav"
    else -> rssExtension(contentType)
  }
}

/**
 * Converts a Subsonic track into a Cadence [Episode]. [Episode.audioUrl] holds only the clean
 * credential-free download path; auth query params are attached right before downloading.
 */
fun subsonicToEpisode(server: String, song: SubsonicSong): Episode? {
  if (song.durationSeconds <= 0) return null
  val base = server.trimEnd('/')
  val durationMs = song.durationSeconds * 1000
  val ext = subsonicExtension(song.suffix, song.contentType)
  val show = listOf(song.artist, song.album).filter { it.isNotBlank() }.joinToString(" — ")
  return Episode(
      backend = SUBSONIC_BACKEND,
      id = song.id,
      title = song.title,
      audioUrl = "$base/rest/download?id=${encodeQueryComponent(song.id)}",
      durationMs = durationMs,
      positionMs = 0,
      revision = "subsonic:0",
      audioVersion = digest("$SUBSONIC_BACKEND|${song.id}|${song.size}|$ext|$durationMs"),
      extension = ext,
      podcastId =
        "$SUBSONIC_BACKEND:${song.albumId.ifBlank { song.album.ifBlank { song.artist } }}",
      podcastTitle = show,
      published = song.year,
    )
    .takeIf { runCatching { it.validate() }.isSuccess }
}

/** Calls the Subsonic / OpenSubsonic API; [onSignedOut] runs on auth refusal. */
class SubsonicApi(
  private val platform: PodcastPlatform,
  private val session: SubsonicSession,
  private val onSignedOut: suspend () -> Unit,
) {
  private suspend fun get(endpoint: String, params: String = ""): String {
    if (!session.signedIn) throw PodcastProblem("Sign in to your Subsonic server first")
    val query =
      if (params.isBlank()) subsonicAuthQuery(session) else "$params&${subsonicAuthQuery(session)}"
    return try {
      platform.request("${session.baseUrl}/rest/$endpoint?$query")
    } catch (e: PodcastProblem) {
      if (e.status != 401) throw e
      onSignedOut()
      throw PodcastProblem("Your Subsonic sign-in failed. Sign in again.", e.status)
    }
  }

  suspend fun playlists(): List<SubsonicPlaylist> = subsonicPlaylists(get("getPlaylists"))

  suspend fun newestAlbums(size: Int = 20): List<SubsonicAlbum> =
    subsonicAlbums(get("getAlbumList2", "type=newest&size=$size"))

  suspend fun playlist(id: String): Pair<String, List<SubsonicSong>> =
    subsonicPlaylistSongs(get("getPlaylist", "id=${encodeQueryComponent(id)}"))

  suspend fun album(id: String): Pair<String, List<SubsonicSong>> =
    subsonicAlbumSongs(get("getAlbum", "id=${encodeQueryComponent(id)}"))

  suspend fun starred(): List<SubsonicSong> = subsonicStarredSongs(get("getStarred2"))

  /** Builds a one-time authenticated download URL for [songId] (never persisted). */
  fun downloadUrl(songId: String): String {
    if (!session.signedIn) throw PodcastProblem("Sign in to your Subsonic server first")
    return "${session.baseUrl}/rest/download?id=${encodeQueryComponent(songId)}&${subsonicAuthQuery(session, json = false)}"
  }

  companion object {
    suspend fun signIn(
      platform: PodcastPlatform,
      server: String,
      username: String,
      password: String,
      apiKey: String = "",
    ): SubsonicSession {
      val base = validateHttpsServer(server, "Subsonic")
      val cleanKey = apiKey.trim()
      val cleanUser = username.trim()
      if (cleanKey.isBlank() && (cleanUser.isBlank() || password.isBlank()))
        throw PodcastProblem("Enter a username and password, or an OpenSubsonic API key")
      val session =
        if (cleanKey.isNotBlank()) SubsonicSession(base, apiKey = cleanKey)
        else SubsonicSession(base, username = cleanUser, password = password)
      val response =
        try {
          platform.request("$base/rest/ping?${subsonicAuthQuery(session)}")
        } catch (e: PodcastProblem) {
          if (e.status !in listOf(400, 401, 403)) throw e
          throw PodcastProblem(
            "Subsonic server refused the sign-in. Check your server URL and credentials.",
            e.status,
          )
        }
      subsonicEnvelope(response)
      return session
    }
  }
}
