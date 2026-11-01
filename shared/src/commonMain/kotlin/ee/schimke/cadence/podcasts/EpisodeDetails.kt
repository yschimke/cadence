package ee.schimke.cadence.podcasts

import kotlinx.serialization.json.*

data class EpisodeDetails(val summary: String = "", val transcriptUrl: String? = null)

fun plainShowNotes(text: String): String =
  text
    .replace(Regex("(?i)<br\\s*/?>|</p>"), "\n")
    .replace(Regex("<[^>]+>"), "")
    .replace("&amp;", "&")
    .replace("&nbsp;", " ")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")
    .trim()

/** Pocket Casts publishes show notes and transcript links separately from account episode data. */
fun pocketCastsDetails(body: String, episodeId: String): EpisodeDetails {
  val root = Json.parseToJsonElement(body) as? JsonObject ?: return EpisodeDetails()
  val podcast = root["podcast"] as? JsonObject ?: return EpisodeDetails()
  val episodes = podcast["episodes"] as? JsonArray ?: return EpisodeDetails()
  val episode =
    episodes
      .mapNotNull { it as? JsonObject }
      .firstOrNull { (it["uuid"] as? JsonPrimitive)?.contentOrNull == episodeId }
      ?: return EpisodeDetails()
  val notes = (episode["show_notes"] as? JsonPrimitive)?.contentOrNull.orEmpty()
  val transcripts =
    (episode["transcripts"] as? JsonArray).orEmpty() +
      (episode["pocket_casts_transcripts"] as? JsonArray).orEmpty()
  val transcript =
    transcripts
      .mapNotNull { entry ->
        when (entry) {
          is JsonObject -> (entry["url"] as? JsonPrimitive)?.contentOrNull
          is JsonPrimitive -> entry.contentOrNull
          else -> null
        }
      }
      .firstOrNull { it.startsWith("https://", ignoreCase = true) }
  return EpisodeDetails(plainShowNotes(notes), transcript)
}

suspend fun loadEpisodeDetails(platform: PodcastPlatform, episode: Episode): EpisodeDetails {
  if (episode.backend != "pocket-casts")
    return EpisodeDetails(
      plainShowNotes(episode.summary),
      episode.transcriptUrl.takeIf { it.startsWith("https://", ignoreCase = true) },
    )
  val podcastId = episode.podcastId.removePrefix("pocket-casts:")
  if (!podcastId.matches(Regex("[A-Za-z0-9-]{1,100}"))) return EpisodeDetails()
  return pocketCastsDetails(
    platform.publicText("https://podcast-api.pocketcasts.com/mobile/show_notes/full/$podcastId"),
    episode.id,
  )
}
