package ee.schimke.cadence.podcasts

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/**
 * Pocket Casts' private web-player API (observed 2026-10-28, not a supported third-party API). It
 * can change without notice; every read is parsed leniently and unusable episodes are skipped.
 */
const val POCKET_CASTS_API = "https://api.pocketcasts.com"

const val POCKET_CASTS_UP_NEXT = "up-next"
const val POCKET_CASTS_IN_PROGRESS = "in-progress"

/**
 * Bearer and refresh tokens, kept with the local library so later imports don't ask again. With a
 * [PocketCastsServer] the tokens stay on the server and only [server] and [email] are kept.
 */
@Serializable
data class PocketCastsSession(
  val accessToken: String = "",
  val refreshToken: String = "",
  val expiresAtMs: Long = 0,
  val email: String = "",
  val server: Boolean = false,
) {
  val signedIn
    get() = accessToken.isNotBlank() || server
}

/**
 * Pocket Casts through a server that keeps the sign-in and makes the calls: the browser build on a
 * Wasmo, where Pocket Casts refuses the browser's own cross-origin requests.
 */
interface PocketCastsServer {
  /** The signed-in email, blank if unknown, or null when signed out. */
  suspend fun session(): String?

  /** Signs in and returns the account's email. */
  suspend fun signIn(email: String, password: String): String

  /** One of the account reads; a [PodcastProblem] with status 401 means sign in again. */
  suspend fun call(path: String, method: String, body: String?): String

  suspend fun signOut()
}

/** Reads the sign-in, refresh and `auth` cookie responses, which share one shape. */
fun pocketCastsSession(
  body: String,
  nowMs: Long,
  previous: PocketCastsSession = PocketCastsSession(),
): PocketCastsSession {
  val json =
    runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
      ?: throw PodcastProblem("Pocket Casts returned an unexpected sign-in response")
  fun text(name: String) = json[name]?.jsonPrimitive?.contentOrNull.orEmpty()
  val access = text("accessToken").ifBlank { text("token") }
  if (access.isBlank()) throw PodcastProblem("Pocket Casts didn't return a sign-in token")
  val expiresIn = json["expiresIn"]?.jsonPrimitive?.longOrNull ?: 3600
  return PocketCastsSession(
    access,
    text("refreshToken").ifBlank { previous.refreshToken },
    nowMs + expiresIn * 1000,
    text("email").ifBlank { previous.email },
  )
}

data class PocketCastsList(val id: String, val title: String)

data class PocketCastsPlaylist(
  val uuid: String,
  val title: String,
  val manual: Boolean,
  val showArchived: Boolean,
  val episodeOrder: List<String>,
)

data class PocketCastsEpisode(
  val uuid: String,
  val title: String,
  val url: String,
  val durationSeconds: Double,
  val playedUpToSeconds: Double,
  val fileType: String,
  val archived: Boolean,
  val podcastUuid: String = "",
  val playingStatus: Int = 0,
  val podcastTitle: String = "",
  val published: String = "",
)

private fun JsonElement?.objects() = (this as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }

private fun JsonObject.text(name: String) = this[name]?.jsonPrimitive?.contentOrNull.orEmpty()

private fun JsonObject.number(name: String) = this[name]?.jsonPrimitive?.doubleOrNull ?: 0.0

private fun JsonObject.flag(name: String) = this[name]?.jsonPrimitive?.booleanOrNull == true

private fun parseObject(body: String): JsonObject =
  runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
    ?: throw PodcastProblem("Pocket Casts returned an unexpected response")

fun pocketCastsPlaylists(body: String): List<PocketCastsPlaylist> =
  parseObject(body)["playlists"].objects().mapNotNull { node ->
    if (node.flag("isDeleted")) return@mapNotNull null
    PocketCastsPlaylist(
      node.text("uuid"),
      node.text("title").ifBlank { "Pocket Casts playlist" },
      node.flag("manual"),
      node.flag("showArchived"),
      (node["episodeOrder"] as? JsonArray).orEmpty().mapNotNull { it.jsonPrimitive.contentOrNull },
    )
  }

fun pocketCastsEpisode(node: JsonObject) =
  PocketCastsEpisode(
    node.text("uuid"),
    node.text("title"),
    node.text("url"),
    node.number("duration"),
    node.number("playedUpTo"),
    node.text("fileType"),
    node.flag("isDeleted"),
    node.text("podcastUuid"),
    node.number("playingStatus").toInt(),
    node.text("podcastTitle"),
    node.text("published"),
  )

/** `/user/episode` returns one episode; `/user/in_progress` a list under `episodes`. */
fun pocketCastsEpisodes(body: String): List<PocketCastsEpisode> {
  val json = parseObject(body)
  return if ("episodes" in json) json["episodes"].objects().map(::pocketCastsEpisode)
  else listOf(pocketCastsEpisode(json))
}

/** Up Next in play order. Lengths and positions come from `episodeSync`, when it has them. */
fun pocketCastsUpNext(body: String): List<PocketCastsEpisode> {
  val json = parseObject(body)
  val sync = json["episodeSync"].objects().associateBy { it.text("uuid") }
  return json["episodes"].objects().mapNotNull { node ->
    val uuid =
      node.text("uuid").ifBlank {
        return@mapNotNull null
      }
    pocketCastsEpisode(node)
      .copy(
        durationSeconds = sync[uuid]?.number("duration") ?: 0.0,
        playedUpToSeconds = sync[uuid]?.number("playedUpTo") ?: 0.0,
      )
  }
}

private val pocketCastsTypes =
  mapOf(
    "audio/mpeg" to "mp3",
    "audio/mp3" to "mp3",
    "audio/mp4" to "m4a",
    "audio/x-m4a" to "m4a",
    "audio/m4a" to "m4a",
    "audio/aac" to "m4a",
    "audio/ogg" to "ogg",
    "audio/opus" to "opus",
    "audio/flac" to "flac",
    "audio/wav" to "wav",
  )

private fun pocketCastsExtension(episode: PocketCastsEpisode): String? =
  pocketCastsTypes[episode.fileType.substringBefore(';').trim().lowercase()]
    ?: episode.url
      .substringBefore('?')
      .substringAfterLast('/')
      .substringAfterLast('.', "")
      .lowercase()
      .takeIf { it in listOf("mp3", "m4a", "ogg", "opus", "flac", "wav") }

internal fun pocketCastsUsable(episode: PocketCastsEpisode) =
  episode.uuid.isNotBlank() &&
    episode.url.startsWith("https://") &&
    episode.durationSeconds > 0 &&
    pocketCastsExtension(episode) != null

/** Keeps playable episodes in order, up to [limit]; the second value counts skipped ones. */
fun pocketCastsSnapshot(
  name: String,
  episodes: List<PocketCastsEpisode>,
  limit: Int,
): Pair<PlaylistSnapshot, Int> {
  var skipped = 0
  val usable =
    episodes
      .distinctBy { it.uuid }
      .mapNotNull { episode ->
        val durationMs = (episode.durationSeconds * 1000).toLong()
        val extension = pocketCastsExtension(episode)
        if (!pocketCastsUsable(episode) || durationMs <= 0 || extension == null) {
          skipped++
          return@mapNotNull null
        }
        val positionMs = (episode.playedUpToSeconds * 1000).toLong().coerceIn(0, durationMs)
        Episode(
          "pocket-casts",
          episode.uuid,
          episode.title.ifBlank { "Episode" },
          episode.url,
          durationMs,
          positionMs,
          "pocketcasts:$positionMs",
          digest("${episode.url}|$durationMs"),
          extension,
          podcastId =
            episode.podcastUuid.takeIf { it.isNotBlank() }?.let { "pocket-casts:$it" }.orEmpty(),
          podcastTitle = episode.podcastTitle,
          published = episode.published,
        )
      }
      .take(limit)
  if (usable.isEmpty())
    throw PodcastProblem(
      if (episodes.isEmpty()) "$name is empty in Pocket Casts"
      else "None of $name has HTTPS audio with a known length"
    )
  return PlaylistSnapshot(name = name, episodes = usable) to skipped
}

/** Where account data comes from: the API with a token, or responses the web player received. */
interface PocketCastsReader {
  /** True when only what the web player happened to load is available. */
  val captured: Boolean
    get() = false

  /** Null when this reader can't see the list. */
  suspend fun upNext(): String?

  suspend fun inProgress(): String?

  suspend fun playlists(): String?

  suspend fun episode(uuid: String): String?
}

suspend fun readPocketCastsLists(reader: PocketCastsReader): List<PocketCastsList> = buildList {
  if (!reader.captured || reader.upNext() != null)
    add(PocketCastsList(POCKET_CASTS_UP_NEXT, "Up Next"))
  if (!reader.captured || reader.inProgress() != null)
    add(PocketCastsList(POCKET_CASTS_IN_PROGRESS, "In progress"))
  reader.playlists()?.let { body ->
    // Smart playlists come back without members; only manual ones can be imported.
    pocketCastsPlaylists(body)
      .filter { it.manual }
      .forEach { add(PocketCastsList(it.uuid, it.title)) }
  }
}

suspend fun readPocketCasts(
  reader: PocketCastsReader,
  list: String,
  limit: Int,
): Pair<PlaylistSnapshot, Int> {
  require(limit in 1..MAX_FEED_EPISODES)
  suspend fun details(uuid: String) =
    reader.episode(uuid)?.let(::pocketCastsEpisodes)?.firstOrNull()
  // Reads in order until enough are usable, so skipped and archived episodes don't use up the
  // count. Unusable ones are kept so the import can say how many were skipped.
  suspend fun gather(
    count: Int,
    episode: suspend (Int) -> PocketCastsEpisode?,
  ): List<PocketCastsEpisode> = buildList {
    var usable = 0
    for (index in 0 until minOf(count, 1000)) {
      if (usable >= limit) break
      val next = episode(index) ?: continue
      add(next)
      if (pocketCastsUsable(next)) usable++
    }
  }
  suspend fun members(uuids: List<String>, showArchived: Boolean) =
    gather(uuids.size) { index -> details(uuids[index])?.takeIf { showArchived || !it.archived } }
  return when (list) {
    POCKET_CASTS_UP_NEXT -> {
      val body =
        reader.upNext() ?: throw PodcastProblem("Up Next isn't available from this sign-in")
      // Fill in what Up Next leaves out, such as the length of an episode not started yet.
      val upNext = pocketCastsUpNext(body)
      val episodes =
        gather(upNext.size) { index ->
          val entry = upNext[index]
          if (pocketCastsUsable(entry)) entry else details(entry.uuid) ?: entry
        }
      pocketCastsSnapshot("Pocket Casts Up Next", episodes, limit)
    }
    POCKET_CASTS_IN_PROGRESS -> {
      val body =
        reader.inProgress() ?: throw PodcastProblem("In progress isn't available from this sign-in")
      pocketCastsSnapshot("Pocket Casts in progress", pocketCastsEpisodes(body), limit)
    }
    else -> {
      val playlist =
        reader.playlists()?.let(::pocketCastsPlaylists)?.firstOrNull { it.uuid == list }
          ?: throw PodcastProblem("That playlist is no longer in Pocket Casts")
      pocketCastsSnapshot(
        playlist.title,
        members(playlist.episodeOrder, playlist.showArchived),
        limit,
      )
    }
  }
}

/** The account reads, over whatever sends one: the API itself or a [PocketCastsServer]. */
abstract class PocketCastsCalls : PocketCastsReader {
  protected abstract suspend fun call(
    path: String,
    method: String = "POST",
    body: String? = "{}",
  ): String

  override suspend fun upNext() =
    call(
      "/up_next/list",
      body = buildJsonObject {
          put("version", 2)
          put("model", "webplayer")
          put("showPlayStatus", true)
          put("serverModified", 0)
        }
          .toString(),
    )

  override suspend fun inProgress() = call("/user/in_progress")

  override suspend fun playlists() = call("/user/playlists", "GET", null)

  override suspend fun episode(uuid: String) =
    call("/user/episode", body = buildJsonObject { put("uuid", uuid) }.toString())
}

/** Reads through a [PocketCastsServer]; [onSignedOut] runs when it has lost the sign-in. */
class PocketCastsServerReader(
  private val server: PocketCastsServer,
  private val onSignedOut: suspend () -> Unit,
) : PocketCastsCalls() {
  override suspend fun call(path: String, method: String, body: String?): String =
    try {
      server.call(path, method, body)
    } catch (e: PodcastProblem) {
      if (e.status != 401) throw e
      onSignedOut()
      throw PodcastProblem("Your Pocket Casts sign-in expired. Sign in again.", e.status)
    }
}

/** Calls the API with the bearer token, refreshing it when it expires. */
class PocketCastsApi(
  private val platform: PodcastPlatform,
  private var session: PocketCastsSession,
  private val now: () -> Long,
  private val onSession: suspend (PocketCastsSession) -> Unit,
) : PocketCastsCalls() {
  /**
   * The official Android client sends this request; the service is not a public third-party API.
   */
  suspend fun sendPosition(uuid: String, podcast: String, positionMs: Long, durationMs: Long) {
    call(
      "/sync/update_episode",
      body = buildJsonObject {
          put("uuid", uuid)
          put("podcast", podcast)
          put("position", positionMs / 1000)
          put("duration", durationMs / 1000)
          put("status", if (positionMs >= durationMs) 3 else 2)
        }
          .toString(),
    )
  }

  private suspend fun send(path: String, method: String, body: String?) =
    platform.request(
      POCKET_CASTS_API + path,
      method,
      mapOf(
        "Authorization" to "Bearer ${session.accessToken}",
        "Content-Type" to "application/json",
      ),
      body,
    )

  override suspend fun call(path: String, method: String, body: String?): String {
    if (!session.signedIn) throw PodcastProblem("Sign in to Pocket Casts first")
    if (now() > session.expiresAtMs - 60_000) refresh()
    return try {
      send(path, method, body)
    } catch (e: PodcastProblem) {
      // The access token lasts an hour and may be revoked early; refresh once and retry.
      if (e.status !in listOf(401, 403) || session.refreshToken.isBlank()) throw e
      refresh()
      send(path, method, body)
    }
  }

  private suspend fun refresh() {
    if (session.refreshToken.isBlank())
      throw PodcastProblem("Your Pocket Casts sign-in expired. Sign in again.")
    val body = buildJsonObject {
      put("grant_type", "refresh_token")
      put("refresh_token", session.refreshToken)
      put("scope", "webplayer")
    }
    val response =
      try {
        platform.request(
          "$POCKET_CASTS_API/user/token",
          "POST",
          mapOf("Content-Type" to "application/json"),
          body.toString(),
        )
      } catch (e: PodcastProblem) {
        // Only a refusal ends the session; an outage keeps it for the next attempt.
        if (e.status !in 400..499) throw e
        onSession(PocketCastsSession())
        throw PodcastProblem("Your Pocket Casts sign-in expired. Sign in again.", e.status)
      }
    session = pocketCastsSession(response, now(), session)
    onSession(session)
  }

  companion object {
    suspend fun signIn(
      platform: PodcastPlatform,
      email: String,
      password: String,
      nowMs: Long,
    ): PocketCastsSession {
      val body = buildJsonObject {
        put("email", email)
        put("password", password)
        put("scope", "webplayer")
      }
      val response =
        platform.request(
          "$POCKET_CASTS_API/user/login_pocket_casts",
          "POST",
          mapOf("Content-Type" to "application/json"),
          body.toString(),
        )
      return pocketCastsSession(response, nowMs).let { it.copy(email = it.email.ifBlank { email }) }
    }
  }
}

/**
 * Responses the web player itself received, captured in the in-app web view when its token can't be
 * read. Only what the player loaded is available, so Up Next or In progress may be missing.
 */
class PocketCastsCapture(json: String) : PocketCastsReader {
  override val captured = true

  private val responses: Map<String, List<String>> = runCatching {
    Json.parseToJsonElement(json).jsonArray
  }
    .getOrNull()
    .objects()
    .groupBy({ it.text("path") }, { it.text("body") })

  private val episodes: Map<String, String> =
    responses["/user/episode"]
      .orEmpty()
      .mapNotNull { body ->
        runCatching { pocketCastsEpisodes(body).single().uuid to body }.getOrNull()
      }
      .toMap()

  val empty
    get() = responses.isEmpty()

  override suspend fun upNext() = responses["/up_next/list"]?.lastOrNull()

  override suspend fun inProgress() = responses["/user/in_progress"]?.lastOrNull()

  override suspend fun playlists() = responses["/user/playlists"]?.lastOrNull()

  override suspend fun episode(uuid: String) = episodes[uuid]
}
