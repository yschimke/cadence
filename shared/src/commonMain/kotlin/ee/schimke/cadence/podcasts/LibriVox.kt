package ee.schimke.cadence.podcasts

import kotlinx.serialization.json.*

/**
 * LibriVox's public audiobook catalog API (https://librivox.org/api/info). Free public-domain
 * audiobooks with DRM-free MP3 chapters served through standard public RSS feeds.
 */
const val LIBRIVOX_API = "https://librivox.org/api/feed/audiobooks/"

data class LibriVoxBook(
  val id: String,
  val title: String,
  val authors: String,
  val totalTime: String,
  val language: String,
  val rssUrl: String,
)

private fun JsonObject.str(name: String) = this[name]?.jsonPrimitive?.contentOrNull.orEmpty()

/** Parses `GET https://librivox.org/api/feed/audiobooks/?format=json&...`. */
fun libriVoxBooks(body: String): List<LibriVoxBook> {
  val root =
    runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
      ?: throw PodcastProblem("LibriVox returned an unexpected response")
  val books = root["books"] as? JsonArray ?: return emptyList()
  return books.mapNotNull { node ->
    val obj = node as? JsonObject ?: return@mapNotNull null
    val id = obj.str("id")
    val rawRss = obj.str("url_rss")
    val rss =
      when {
        rawRss.startsWith("https://") -> rawRss
        rawRss.startsWith("http://librivox.org/") -> "https://" + rawRss.removePrefix("http://")
        id.matches(Regex("[0-9]+")) -> "https://librivox.org/rss/$id"
        else -> return@mapNotNull null
      }
    val authors =
      (obj["authors"] as? JsonArray)
        .orEmpty()
        .mapNotNull { author ->
          val a = author as? JsonObject ?: return@mapNotNull null
          listOf(a.str("first_name"), a.str("last_name"))
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .takeIf { it.isNotBlank() }
        }
        .joinToString(", ")
    LibriVoxBook(
      id = id,
      title = obj.str("title").ifBlank { "Untitled audiobook" },
      authors = authors,
      totalTime = obj.str("totaltime"),
      language = obj.str("language"),
      rssUrl = rss,
    )
  }
}

/** Searches the LibriVox catalog by title (or lists recent releases when [query] is blank). */
suspend fun searchLibriVox(
  platform: PodcastPlatform,
  query: String,
  limit: Int = 20,
): List<LibriVoxBook> {
  val trimmed = query.trim()
  val url =
    if (trimmed.isBlank()) "$LIBRIVOX_API?format=json&limit=$limit"
    else "$LIBRIVOX_API?format=json&title=${encodeQueryComponent("^$trimmed")}&limit=$limit"
  return try {
    libriVoxBooks(platform.publicText(url))
  } catch (e: PodcastProblem) {
    if (e.status == 404) emptyList() else throw e
  }
}
