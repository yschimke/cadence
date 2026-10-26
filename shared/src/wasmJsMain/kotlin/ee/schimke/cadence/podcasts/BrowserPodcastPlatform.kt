package ee.schimke.cadence.podcasts

import androidx.compose.runtime.*
import ee.schimke.cadence.browser.browserCall
import ee.schimke.cadence.sync.browserArgs
import kotlinx.serialization.json.*

@Composable
actual fun rememberPodcastPlatform(): PodcastPlatform = remember { BrowserPodcastPlatform() }

class BrowserPodcastPlatform : PodcastPlatform {
  override val splitFormats = emptySet<String>()
  override val description =
    "Web: unsplit downloads to browser storage. Feed/audio servers must allow CORS. USB copies require HTTPS and Chrome/Edge desktop's writable directory picker. Safari, Firefox and mobile browsers do not support this USB workflow."

  override suspend fun readState(): String? =
    Json.parseToJsonElement(browserCall("podcastRead", "{}")).jsonPrimitive.contentOrNull

  override suspend fun saveState(state: String) {
    browserCall("podcastSave", browserArgs("state" to state))
  }

  override suspend fun request(
    url: String,
    method: String,
    headers: Map<String, String>,
    body: String?,
  ): String {
    val args = buildJsonObject {
      put("url", url)
      put("method", method)
      put("headers", buildJsonObject { headers.forEach { (k, v) -> put(k, v) } })
      body?.let { put("body", it) }
    }
    val response =
      try {
        browserCall("podcastRequest", args.toString())
      } catch (e: Exception) {
        if (e.message?.endsWith("Podcast HTTP 409") == true)
          throw PodcastProblem(
            "Cloud revision conflict. Restore cloud configuration, reapply local settings, and save again."
          )
        if (
          e.message?.endsWith("Podcast HTTP 401") == true ||
            e.message?.endsWith("Podcast HTTP 403") == true
        )
          throw PodcastProblem(
            "Sign in again or check the cloud project's authentication and row policies."
          )
        throw e
      }
    return Json.parseToJsonElement(response).jsonPrimitive.content
  }

  override suspend fun rss(url: String): PlaylistSnapshot {
    val raw =
      Json.parseToJsonElement(browserCall("podcastRss", browserArgs("url" to url))).jsonObject
    val episodes =
      raw.getValue("episodes").jsonArray.map { node ->
        val item = node.jsonObject
        fun field(name: String) = item.getValue(name).jsonPrimitive.content
        val audio = field("audioUrl")
        Episode(
          "rss",
          "$url#${field("id")}",
          field("title"),
          audio,
          rssDurationMs(field("duration")),
          revision = "rss",
          audioVersion = digest(audio + "|" + field("length")),
          extension = field("extension"),
        )
      }
    return PlaylistSnapshot(name = raw.getValue("name").jsonPrimitive.content, episodes = episodes)
      .also { it.validate() }
  }

  override suspend fun prepare(episode: Episode, sections: List<Section>) {
    require(
      sections.size == 1 && sections[0].startMs == 0L && sections[0].endMs == episode.durationMs
    )
    browserCall(
      "podcastDownload",
      browserArgs("url" to episode.audioUrl, "fileName" to sections.single().fileName),
    )
  }

  override suspend fun available(section: Section): Boolean =
    Json.parseToJsonElement(
        browserCall("podcastAvailable", browserArgs("fileName" to section.fileName))
      )
      .jsonPrimitive
      .boolean

  override suspend fun export(sections: List<Section>, target: String, manifest: String) {
    browserCall(
      "podcastExport",
      buildJsonObject {
        put("target", target)
        put("manifest", manifest)
        put("files", JsonArray(sections.map { JsonPrimitive(it.fileName) }))
      }
        .toString(),
    )
  }
}
