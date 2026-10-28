package ee.schimke.cadence.podcasts

import kotlin.math.abs
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okio.ByteString.Companion.encodeUtf8

/**
 * A listening position reported by another app. Sources such as gpodder and Overcast identify
 * episodes by feed and enclosure URL but do not carry everything a [PlaylistSnapshot] needs, so
 * [resolveProgress] completes them from the show's public RSS feed.
 */
data class RemoteProgress(
  val feedUrl: String,
  val audioUrl: String,
  val guid: String,
  val positionMs: Long,
  // The length the other app measured, when it reports one.
  val durationMs: Long?,
  val title: String,
  val revision: String,
)

data class ResolvedImport(val snapshot: PlaylistSnapshot, val skipped: Int)

/** Separates the feed and the enclosure URL the other app played; neither may contain a space. */
fun remoteEpisodeId(feedUrl: String, audioUrl: String) = "$feedUrl $audioUrl"

private fun lastSegment(url: String) = url.substringBefore('?').substringAfterLast('/')

/**
 * Matches each position to its feed item (guid, then exact enclosure URL, then file name, since
 * tracking prefixes change) and keeps it only when the feed still offers HTTPS audio of the same
 * length the other app measured. A different length means different media (for example re-inserted
 * ads), where the position would land in the wrong place.
 */
suspend fun resolveProgress(
  platform: PodcastPlatform,
  backend: String,
  name: String,
  progress: List<RemoteProgress>,
): ResolvedImport {
  val feeds = mutableMapOf<String, RssFeed?>()
  var skipped = 0
  val episodes = mutableListOf<Episode>()
  for (remote in progress) {
    val feed =
      if (!remote.feedUrl.startsWith("https://")) null
      else
        feeds.getOrPut(remote.feedUrl) {
          try {
            platform.rss(remote.feedUrl)
          } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
          } catch (_: Exception) {
            null
          }
        }
    val item =
      feed?.items?.let { items ->
        items.firstOrNull { remote.guid.isNotBlank() && it.guid == remote.guid }
          ?: items.firstOrNull { it.audioUrl == remote.audioUrl }
          ?: items.firstOrNull {
            lastSegment(it.audioUrl).isNotBlank() &&
              lastSegment(it.audioUrl) == lastSegment(remote.audioUrl)
          }
      }
    val episode = item?.let {
      runCatching {
        val duration = rssDurationMs(it.duration)
        remote.durationMs?.let { measured ->
          require(abs(measured - duration) <= maxOf(30_000L, duration / 50))
        }
        Episode(
            backend,
            remoteEpisodeId(remote.feedUrl, remote.audioUrl),
            it.title.ifBlank { remote.title }.ifBlank { "Untitled episode" },
            it.audioUrl,
            duration,
            remote.positionMs.coerceIn(0, duration),
            remote.revision,
            digest(it.audioUrl + "|" + it.length),
            rssExtension(it.type),
          )
          .also(Episode::validate)
      }
        .getOrNull()
    }
    if (episode == null || episodes.any { it.key == episode.key }) skipped++
    else episodes += episode
  }
  if (episodes.isEmpty())
    throw PodcastProblem(
      "None of the ${progress.size} in-progress episodes could be matched to downloadable audio in their feeds."
    )
  return ResolvedImport(
    PlaylistSnapshot(name = name, episodes = episodes).also { it.validate() },
    skipped,
  )
}

private val entity = Regex("&(#x[0-9a-fA-F]+|#[0-9]+|amp|lt|gt|quot|apos);")

private fun decodeXml(value: String) =
  entity.replace(value) {
    when (val name = it.groupValues[1]) {
      "amp" -> "&"
      "lt" -> "<"
      "gt" -> ">"
      "quot" -> "\""
      "apos" -> "'"
      else ->
        (if (name.startsWith("#x")) name.drop(2).toIntOrNull(16) else name.drop(1).toIntOrNull())
          ?.let { code -> if (code in 0..0xFFFF) code.toChar().toString() else "" }
          .orEmpty()
    }
  }

private val outlineTag = Regex("<(/?)outline\\b([^>]*?)(/?)>", RegexOption.IGNORE_CASE)
private val attribute = Regex("([A-Za-z_:][-A-Za-z0-9_:.]*)\\s*=\\s*(\"([^\"]*)\"|'([^']*)')")

/** Reads OPML `<outline>` attributes with their parent outline, without a platform XML parser. */
internal fun opmlOutlines(text: String): List<Pair<Map<String, String>, Map<String, String>?>> {
  require(!text.contains("<!DOCTYPE", ignoreCase = true)) { "DOCTYPE is not supported" }
  val stack = ArrayDeque<Map<String, String>>()
  val result = mutableListOf<Pair<Map<String, String>, Map<String, String>?>>()
  for (match in outlineTag.findAll(text)) {
    if (match.groupValues[1] == "/") {
      stack.removeLastOrNull()
      continue
    }
    val attrs =
      attribute.findAll(match.groupValues[2]).associate {
        it.groupValues[1] to decodeXml(it.groupValues[3].ifEmpty { it.groupValues[4] })
      }
    result += attrs to stack.lastOrNull()
    if (match.groupValues[3] != "/") stack.addLast(attrs)
  }
  return result
}

/**
 * Overcast's "All data" export (`/account/export_opml/extended`) lists every episode it knows with
 * `progress` in seconds and `played`, under its feed's outline. It has no queue, so the most
 * recently updated unfinished episodes come first.
 */
fun overcastProgress(text: String, limit: Int): List<RemoteProgress> {
  require(limit in 1..MAX_FEED_EPISODES)
  val episodes = opmlOutlines(text).filter { (attrs, _) -> attrs["type"] == "podcast-episode" }
  if (episodes.isEmpty())
    throw PodcastProblem(
      "This OPML lists subscriptions only. In Overcast choose Account → Export OPML (All data) to include listening progress."
    )
  return episodes
    .mapNotNull { (attrs, feed) ->
      val progress = attrs["progress"]?.toLongOrNull() ?: return@mapNotNull null
      if (progress <= 0 || attrs["played"] == "1" || attrs["userDeleted"] == "1")
        return@mapNotNull null
      val updated = attrs["userUpdatedDate"].orEmpty()
      RemoteProgress(
        feed?.get("xmlUrl").orEmpty(),
        attrs["enclosureUrl"].orEmpty(),
        "",
        progress * 1000,
        null,
        attrs["title"].orEmpty(),
        "overcast:$updated:$progress",
      ) to updated
    }
    .sortedByDescending { it.second }
    .take(limit)
    .map { it.first }
}

/** gpodder API v2 servers (gpodder.net, oPodSync) and the Nextcloud GPodder Sync app. */
@Serializable
enum class GpodderFlavor {
  Gpodder,
  Nextcloud,
}

@Serializable
data class GpodderAccount(
  val flavor: GpodderFlavor = GpodderFlavor.Gpodder,
  val server: String = "",
  val user: String = "",
) {
  fun validate() {
    require(server.startsWith("https://")) { "Use an HTTPS server URL" }
    require(server.substringAfter("https://").substringBefore('/').isNotBlank())
    require(user.matches(Regex("[A-Za-z0-9._@+-]{1,100}"))) { "Enter your sync user name" }
  }

  private val base
    get() = server.trimEnd('/')

  fun actionsUrl(since: Long = 0) =
    when (flavor) {
      GpodderFlavor.Gpodder -> "$base/api/2/episodes/$user.json?since=$since&aggregated=true"
      GpodderFlavor.Nextcloud -> "$base/index.php/apps/gpoddersync/episode_action?since=$since"
    }

  fun uploadUrl() =
    when (flavor) {
      GpodderFlavor.Gpodder -> "$base/api/2/episodes/$user.json"
      GpodderFlavor.Nextcloud -> "$base/index.php/apps/gpoddersync/episode_action/create"
    }

  fun headers(password: String) =
    mapOf(
      "Authorization" to "Basic " + "$user:$password".encodeUtf8().base64(),
      "Content-Type" to "application/json",
    )
}

/** The latest `play` action per episode, with position and length in seconds; -1 means unset. */
data class GpodderPlay(
  val podcast: String,
  val episode: String,
  val guid: String,
  val timestamp: String,
  val position: Long,
  val total: Long,
)

fun gpodderPlays(body: String): List<GpodderPlay> =
  Json.parseToJsonElement(body)
    .jsonObject["actions"]
    ?.jsonArray
    .orEmpty()
    .mapNotNull { node ->
      val action = node as? JsonObject ?: return@mapNotNull null
      fun text(name: String) = action[name]?.jsonPrimitive?.contentOrNull.orEmpty()
      fun number(name: String) = action[name]?.jsonPrimitive?.longOrNull ?: -1
      if (!text("action").equals("play", ignoreCase = true)) return@mapNotNull null
      GpodderPlay(
        text("podcast"),
        text("episode"),
        text("guid"),
        text("timestamp"),
        number("position"),
        number("total"),
      )
    }
    .groupBy { it.podcast to it.episode }
    .map { (_, plays) -> plays.maxBy { it.timestamp } }

/** Unfinished episodes, most recently played first. gpodder has no queue or playlist. */
fun gpodderProgress(plays: List<GpodderPlay>, limit: Int): List<RemoteProgress> {
  require(limit in 1..MAX_FEED_EPISODES)
  return plays
    .filter { it.position > 0 && (it.total <= 0 || it.position < it.total - 30) }
    .sortedByDescending { it.timestamp }
    .take(limit)
    .map {
      RemoteProgress(
        it.podcast,
        it.episode,
        it.guid,
        it.position * 1000,
        it.total.takeIf { total -> total > 0 }?.times(1000),
        "",
        "gpodder:${it.timestamp}:${it.position}",
      )
    }
}

/** gpodder timestamps are UTC `YYYY-MM-DDTHH:MM:SS`. */
fun gpodderTimestamp(instant: String) = instant.take(19)

fun gpodderUpload(episode: Episode, positionMs: Long, timestamp: String): String {
  val (podcast, audio) = episode.id.split(' ', limit = 2)
  return buildJsonArray {
    addJsonObject {
      put("podcast", podcast)
      put("episode", audio)
      put("action", "play")
      put("timestamp", timestamp)
      put("started", episode.positionMs / 1000)
      put("position", positionMs / 1000)
      put("total", episode.durationMs / 1000)
    }
  }
    .toString()
}
