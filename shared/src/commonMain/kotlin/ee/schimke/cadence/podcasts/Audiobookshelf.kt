package ee.schimke.cadence.podcasts

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/**
 * Audiobookshelf's documented REST API (https://api.audiobookshelf.org/) for self-hosted audiobooks
 * and podcasts. Supports password sign-in or an API token, DRM-free audio downloads, embedded
 * chapter markers, and conflict-checked listening progress updates.
 */
const val AUDIOBOOKSHELF_BACKEND = "audiobookshelf"

/** Special list id for Audiobookshelf's "Continue listening" shelf. */
const val AUDIOBOOKSHELF_IN_PROGRESS = "in-progress"

/** Prefix for a library id in [PodcastLibrary.sourceList]. */
const val AUDIOBOOKSHELF_LIBRARY_PREFIX = "library:"

/** Server URL and Bearer token, kept encrypted in [PodcastSecrets] on Android and desktop. */
@Serializable
data class AudiobookshelfSession(
  val server: String = "",
  val token: String = "",
  val username: String = "",
) {
  val signedIn
    get() = server.isNotBlank() && token.isNotBlank()

  val baseUrl
    get() = server.trimEnd('/')
}

data class AudiobookshelfLibrary(val id: String, val name: String, val mediaType: String)

data class AudiobookshelfAudioFile(
  val ino: String,
  val index: Int,
  val durationSeconds: Double,
  val mimeType: String,
  val ext: String,
)

data class AudiobookshelfProgress(
  val libraryItemId: String,
  val episodeId: String = "",
  val currentTimeSeconds: Double = 0.0,
  val durationSeconds: Double = 0.0,
  val isFinished: Boolean = false,
  val lastUpdateMs: Long = 0,
)

data class AudiobookshelfItem(
  val id: String,
  val episodeId: String = "",
  val mediaType: String,
  val title: String,
  val subtitle: String,
  val summary: String,
  val published: String,
  val durationSeconds: Double,
  val chaptersMs: List<Long>,
  val audioFiles: List<AudiobookshelfAudioFile>,
  val showTitle: String = "",
)

private fun JsonObject.str(name: String) = this[name]?.jsonPrimitive?.contentOrNull.orEmpty()

private fun JsonObject.dbl(name: String) = this[name]?.jsonPrimitive?.doubleOrNull ?: 0.0

private fun JsonObject.lng(name: String) = this[name]?.jsonPrimitive?.longOrNull ?: 0L

private fun JsonObject.bool(name: String) = this[name]?.jsonPrimitive?.booleanOrNull ?: false

internal fun validateHttpsServer(server: String, service: String): String {
  val normalized = server.trim().trimEnd('/')
  if (!normalized.startsWith("https://")) throw PodcastProblem("Enter an HTTPS $service server URL")
  val host = normalized.substringAfter("https://").substringBefore('/')
  if (host.isBlank() || '@' in host) throw PodcastProblem("Enter a valid HTTPS $service server URL")
  return normalized
}

/**
 * True when [url] has the exact same HTTPS origin (case-insensitive host and port, with default
 * `:443` normalized) as [serverBaseUrl], with no userinfo (`@`). Ensures credentials are never
 * attached to a crafted or stale-server URL.
 */
internal fun sameHttpsOrigin(url: String, serverBaseUrl: String): Boolean {
  fun origin(raw: String): Pair<String, Int>? {
    if (!raw.startsWith("https://")) return null
    val authority =
      raw.removePrefix("https://").substringBefore('/').substringBefore('?').substringBefore('#')
    if (authority.isBlank() || '@' in authority) return null
    val (host, port) =
      if (authority.startsWith("[")) {
        val close = authority.indexOf(']')
        if (close < 0) return null
        val h = authority.substring(0, close + 1).lowercase()
        val rest = authority.substring(close + 1)
        val p =
          when {
            rest.isEmpty() -> 443
            rest.startsWith(":") -> rest.drop(1).toIntOrNull() ?: return null
            else -> return null
          }
        h to p
      } else {
        val colon = authority.lastIndexOf(':')
        if (colon >= 0) {
          val h = authority.substring(0, colon).lowercase()
          val p = authority.substring(colon + 1).toIntOrNull() ?: return null
          h to p
        } else {
          authority.lowercase() to 443
        }
      }
    if (host.isBlank() || port !in 1..65535) return null
    return host to port
  }
  val a = origin(url) ?: return false
  val b = origin(serverBaseUrl) ?: return false
  return a == b
}

private fun absObject(body: String): JsonObject =
  runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
    ?: throw PodcastProblem("Audiobookshelf returned an unexpected response")

/** Parses `POST /login` response (`{user: {username, token|accessToken}}`). */
fun audiobookshelfLogin(body: String, server: String, fallbackUser: String): AudiobookshelfSession {
  val root = absObject(body)
  val user = root["user"] as? JsonObject ?: root
  val token = user.str("accessToken").ifBlank { user.str("token") }
  if (token.isBlank()) throw PodcastProblem("Audiobookshelf didn't return an access token")
  val username = user.str("username").ifBlank { fallbackUser }
  return AudiobookshelfSession(validateHttpsServer(server, "Audiobookshelf"), token, username)
}

/** Parses `GET /api/me` for the username and all saved media progress entries. */
fun audiobookshelfMe(body: String): Pair<String, List<AudiobookshelfProgress>> {
  val root = absObject(body)
  val username = root.str("username")
  val progress =
    (root["mediaProgress"] as? JsonArray).orEmpty().mapNotNull { node ->
      (node as? JsonObject)?.let(::audiobookshelfProgress)
    }
  return username to progress
}

fun audiobookshelfProgress(node: JsonObject): AudiobookshelfProgress =
  AudiobookshelfProgress(
    libraryItemId = node.str("libraryItemId"),
    episodeId = node.str("episodeId"),
    currentTimeSeconds = node.dbl("currentTime"),
    durationSeconds = node.dbl("duration"),
    isFinished = node.bool("isFinished"),
    lastUpdateMs = node.lng("lastUpdate"),
  )

/** Parses `GET /api/libraries`. */
fun audiobookshelfLibraries(body: String): List<AudiobookshelfLibrary> =
  (absObject(body)["libraries"] as? JsonArray).orEmpty().mapNotNull { node ->
    val obj = node as? JsonObject ?: return@mapNotNull null
    val id = obj.str("id")
    if (id.isBlank()) return@mapNotNull null
    AudiobookshelfLibrary(
      id = id,
      name = obj.str("name").ifBlank { "Library" },
      mediaType = obj.str("mediaType").ifBlank { "book" },
    )
  }

/** Extracts library item ids (and optional recent episode id) from in-progress or library lists. */
fun audiobookshelfListEntries(body: String): List<Pair<String, String>> {
  val root = absObject(body)
  val array = (root["libraryItems"] ?: root["results"]) as? JsonArray ?: return emptyList()
  return array.mapNotNull { node ->
    val obj = node as? JsonObject ?: return@mapNotNull null
    val id = obj.str("id")
    if (id.isBlank()) return@mapNotNull null
    val episodeId = (obj["recentEpisode"] as? JsonObject)?.str("id").orEmpty()
    id to episodeId
  }
}

private fun parseAudioFile(obj: JsonObject): AudiobookshelfAudioFile? {
  val ino = obj.str("ino")
  if (ino.isBlank()) return null
  val meta = obj["metadata"] as? JsonObject
  return AudiobookshelfAudioFile(
    ino = ino,
    index = obj["index"]?.jsonPrimitive?.intOrNull ?: 0,
    durationSeconds = obj.dbl("duration"),
    mimeType = obj.str("mimeType"),
    ext = meta?.str("ext").orEmpty(),
  )
}

private fun parseChapters(array: JsonArray?, durationMs: Long): List<Long> {
  val starts =
    array.orEmpty().mapNotNull { node ->
      (node as? JsonObject)?.get("start")?.jsonPrimitive?.doubleOrNull?.let { (it * 1000).toLong() }
    }
  return chapterStarts(starts, durationMs)
}

/**
 * Parses `GET /api/items/{id}?expanded=1`. For a book, returns one item; for a podcast, returns
 * either the matching [episodeId] or its newest episodes.
 */
fun audiobookshelfExpandedItems(body: String, episodeId: String = ""): List<AudiobookshelfItem> {
  val root = absObject(body)
  val id = root.str("id")
  if (id.isBlank()) return emptyList()
  val mediaType = root.str("mediaType").ifBlank { "book" }
  val media = root["media"] as? JsonObject ?: return emptyList()
  val metadata = media["metadata"] as? JsonObject ?: JsonObject(emptyMap())
  if (mediaType == "podcast") {
    val showTitle = metadata.str("title").ifBlank { "Podcast" }
    val episodes = (media["episodes"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
    val chosen =
      if (episodeId.isNotBlank()) episodes.filter { it.str("id") == episodeId }
      else episodes.sortedByDescending { it.lng("publishedAt") }
    return chosen.mapNotNull { ep ->
      val epId = ep.str("id")
      val audioFile =
        (ep["audioFile"] as? JsonObject)?.let(::parseAudioFile) ?: return@mapNotNull null
      val durationSec = ep.dbl("duration").takeIf { it > 0 } ?: audioFile.durationSeconds
      val durationMs = (durationSec * 1000).toLong()
      AudiobookshelfItem(
        id = id,
        episodeId = epId,
        mediaType = "podcast",
        title = ep.str("title").ifBlank { "Untitled episode" },
        subtitle = showTitle,
        summary = ep.str("description"),
        published = ep.str("pubDate"),
        durationSeconds = durationSec,
        chaptersMs = parseChapters(ep["chapters"] as? JsonArray, durationMs),
        audioFiles = listOf(audioFile),
        showTitle = showTitle,
      )
    }
  }
  val files =
    (media["audioFiles"] as? JsonArray)
      .orEmpty()
      .mapNotNull { (it as? JsonObject)?.let(::parseAudioFile) }
      .sortedBy { it.index }
  if (files.isEmpty()) return emptyList()
  val durationSec = media.dbl("duration").takeIf { it > 0 } ?: files.sumOf { it.durationSeconds }
  val durationMs = (durationSec * 1000).toLong()
  val author =
    metadata.str("authorName").ifBlank {
      (metadata["authors"] as? JsonArray)
        .orEmpty()
        .mapNotNull { (it as? JsonObject)?.str("name")?.takeIf(String::isNotBlank) }
        .joinToString(", ")
    }
  val narrator =
    metadata.str("narratorName").ifBlank {
      (metadata["narrators"] as? JsonArray)
        .orEmpty()
        .mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) }
        .joinToString(", ")
    }
  val summary =
    listOfNotNull(
        narrator.takeIf { it.isNotBlank() }?.let { "Narrated by $it" },
        metadata.str("description").takeIf { it.isNotBlank() },
      )
      .joinToString("\n\n")
  return listOf(
    AudiobookshelfItem(
      id = id,
      mediaType = "book",
      title = metadata.str("title").ifBlank { "Audiobook" },
      subtitle = author,
      summary = summary,
      published = metadata.str("publishedYear"),
      durationSeconds = durationSec,
      chaptersMs = parseChapters(media["chapters"] as? JsonArray, durationMs),
      audioFiles = files,
      showTitle = author,
    ),
  )
}

internal fun audiobookshelfExtension(file: AudiobookshelfAudioFile): String {
  val ext = file.ext.removePrefix(".").lowercase()
  return when (ext) {
    "m4b",
    "m4a",
    "aac",
    "mp4" -> "m4a"
    "mp3" -> "mp3"
    "ogg" -> "ogg"
    "opus" -> "opus"
    "flac" -> "flac"
    "wav" -> "wav"
    else -> rssExtension(file.mimeType)
  }
}

/**
 * Converts an Audiobookshelf book or podcast episode to a Cadence [Episode]. Multi-file MP3 books
 * store their file inos in [Episode.audioParts] only at download time, while single-file items
 * point directly to the credential-free download path.
 */
fun audiobookshelfToEpisode(
  server: String,
  item: AudiobookshelfItem,
  progress: AudiobookshelfProgress?,
): Episode? {
  val base = server.trimEnd('/')
  val durationMs = (item.durationSeconds * 1000).toLong()
  if (durationMs <= 0 || item.audioFiles.isEmpty()) return null
  val single = item.audioFiles.size == 1
  val allMp3 = item.audioFiles.all { audiobookshelfExtension(it) == "mp3" }
  if (!single && !allMp3) return null
  val primary = item.audioFiles.first()
  val extension = if (single) audiobookshelfExtension(primary) else "mp3"
  val episodeId = if (item.episodeId.isNotBlank()) "${item.id}/${item.episodeId}" else item.id
  val posMs =
    when {
      progress == null -> 0L
      progress.isFinished -> durationMs
      else -> (progress.currentTimeSeconds * 1000).toLong().coerceIn(0, durationMs)
    }
  val rev = if (progress != null) "abs:${progress.lastUpdateMs}:${posMs / 1000}" else "abs:0:0"
  val inos = item.audioFiles.joinToString(",") { it.ino }
  val audioUrl = "$base/api/items/${item.id}/file/${primary.ino}/download"
  val partUrls =
    if (single) emptyList()
    else item.audioFiles.map { "$base/api/items/${item.id}/file/${it.ino}/download" }
  return Episode(
      backend = AUDIOBOOKSHELF_BACKEND,
      id = episodeId,
      title = item.title,
      audioUrl = audioUrl,
      durationMs = durationMs,
      positionMs = posMs,
      revision = rev,
      audioVersion = digest("$AUDIOBOOKSHELF_BACKEND|$episodeId|$inos|$durationMs"),
      extension = extension,
      podcastId = "$AUDIOBOOKSHELF_BACKEND:${item.id}",
      podcastTitle = item.showTitle,
      summary = item.summary,
      published = item.published,
      audioParts = partUrls,
      chapterMs = item.chaptersMs,
    )
    .takeIf { runCatching { it.validate() }.isSuccess }
}

/** Calls the Audiobookshelf API with Bearer auth; [onSignedOut] runs on HTTP 401. */
class AudiobookshelfApi(
  private val platform: PodcastPlatform,
  private val session: AudiobookshelfSession,
  private val onSignedOut: suspend () -> Unit,
) {
  fun authHeaders(): Map<String, String> = mapOf("Authorization" to "Bearer ${session.token}")

  private suspend fun call(
    path: String,
    method: String = "GET",
    body: String? = null,
  ): String {
    if (!session.signedIn) throw PodcastProblem("Sign in to Audiobookshelf first")
    val headers = buildMap {
      put("Authorization", "Bearer ${session.token}")
      if (body != null) put("Content-Type", "application/json")
    }
    return try {
      platform.request(session.baseUrl + path, method, headers, body)
    } catch (e: PodcastProblem) {
      if (e.status != 401) throw e
      onSignedOut()
      throw PodcastProblem("Your Audiobookshelf sign-in expired. Sign in again.", e.status)
    }
  }

  suspend fun libraries(): List<AudiobookshelfLibrary> =
    audiobookshelfLibraries(call("/api/libraries"))

  suspend fun me(): Pair<String, List<AudiobookshelfProgress>> = audiobookshelfMe(call("/api/me"))

  suspend fun itemsInProgress(): List<Pair<String, String>> =
    audiobookshelfListEntries(call("/api/me/items-in-progress"))

  suspend fun libraryItems(libraryId: String, limit: Int = 50): List<Pair<String, String>> =
    audiobookshelfListEntries(
      call("/api/libraries/$libraryId/items?limit=$limit&sort=addedAt&desc=1"),
    )

  suspend fun expandedItem(itemId: String, episodeId: String = ""): List<AudiobookshelfItem> =
    audiobookshelfExpandedItems(call("/api/items/$itemId?expanded=1"), episodeId)

  suspend fun progress(itemId: String, episodeId: String = ""): AudiobookshelfProgress? {
    val path =
      if (episodeId.isNotBlank()) "/api/me/progress/$itemId/$episodeId"
      else "/api/me/progress/$itemId"
    return try {
      audiobookshelfProgress(absObject(call(path)))
    } catch (e: PodcastProblem) {
      if (e.status == 404) null else throw e
    }
  }

  suspend fun sendPosition(
    itemId: String,
    episodeId: String,
    positionMs: Long,
    durationMs: Long,
  ) {
    val path =
      if (episodeId.isNotBlank()) "/api/me/progress/$itemId/$episodeId"
      else "/api/me/progress/$itemId"
    val currentTime = positionMs / 1000.0
    val duration = durationMs / 1000.0
    val finished = durationMs > 0 && positionMs >= durationMs - 1000
    val ratio = if (duration > 0) (currentTime / duration).coerceIn(0.0, 1.0) else 0.0
    val payload = buildJsonObject {
      put("currentTime", currentTime)
      put("duration", duration)
      put("progress", ratio)
      put("isFinished", finished)
    }
    call(path, "PATCH", payload.toString())
  }

  companion object {
    suspend fun signIn(
      platform: PodcastPlatform,
      server: String,
      username: String,
      password: String,
    ): AudiobookshelfSession {
      val base = validateHttpsServer(server, "Audiobookshelf")
      val body = buildJsonObject {
        put("username", username)
        put("password", password)
      }
      val response =
        try {
          platform.request(
            "$base/login",
            "POST",
            mapOf("Content-Type" to "application/json"),
            body.toString(),
          )
        } catch (e: PodcastProblem) {
          if (e.status !in listOf(400, 401, 403)) throw e
          throw PodcastProblem(
            "Audiobookshelf refused the sign-in. Check your server URL, username and password.",
            e.status,
          )
        }
      return audiobookshelfLogin(response, base, username)
    }

    suspend fun signInWithToken(
      platform: PodcastPlatform,
      server: String,
      token: String,
    ): AudiobookshelfSession {
      val base = validateHttpsServer(server, "Audiobookshelf")
      val cleanToken = token.trim()
      if (cleanToken.isBlank()) throw PodcastProblem("Enter your Audiobookshelf API token")
      val response =
        try {
          platform.request("$base/api/me", headers = mapOf("Authorization" to "Bearer $cleanToken"))
        } catch (e: PodcastProblem) {
          if (e.status !in listOf(400, 401, 403)) throw e
          throw PodcastProblem(
            "Audiobookshelf refused the API token. Check your server URL and token.",
            e.status,
          )
        }
      val (username, _) = audiobookshelfMe(response)
      return AudiobookshelfSession(base, cleanToken, username)
    }
  }
}
