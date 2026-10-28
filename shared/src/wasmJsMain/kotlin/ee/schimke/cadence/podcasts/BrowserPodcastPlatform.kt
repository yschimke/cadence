package ee.schimke.cadence.podcasts

import androidx.compose.runtime.*
import ee.schimke.cadence.browser.browserCall
import ee.schimke.cadence.sync.browserArgs
import kotlinx.serialization.json.*

@Composable
actual fun rememberPodcastPlatform(): PodcastPlatform = remember { BrowserPodcastPlatform() }

class BrowserPodcastPlatform : PodcastPlatform {
  private suspend fun call(op: String, args: String): String =
    try {
      browserCall(op, args)
    } catch (e: Exception) {
      if (e.message?.endsWith("Podcast blocked") == true)
        throw PodcastProblem(
          "This server doesn't let web apps read it (CORS), or the network is offline. Use the desktop or Android app for this source."
        )
      if (e.message?.endsWith("Podcast HTTP 404") == true)
        throw PodcastProblem("Not found (HTTP 404). Check the server URL and server type.")
      throw e
    }

  override val splitFormats = emptySet<String>()
  override val pocketCastsApi = false
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
        call("podcastRequest", args.toString())
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
            "The server refused the sign-in. Check the user name and password (or the cloud project's row policies) and sign in again."
          )
        throw e
      }
    return Json.parseToJsonElement(response).jsonPrimitive.content
  }

  override suspend fun rss(url: String): RssFeed {
    val raw = Json.parseToJsonElement(call("podcastRss", browserArgs("url" to url))).jsonObject
    return RssFeed(
      raw.getValue("name").jsonPrimitive.content,
      raw.getValue("items").jsonArray.map { node ->
        val item = node.jsonObject
        fun field(name: String) = item.getValue(name).jsonPrimitive.content
        RssItem(
          field("guid"),
          field("title"),
          field("audioUrl"),
          field("duration"),
          field("length"),
          field("type"),
        )
      },
    )
  }

  override suspend fun prepare(episode: Episode, sections: List<Section>) {
    require(
      sections.size == 1 && sections[0].startMs == 0L && sections[0].endMs == episode.durationMs
    )
    call(
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
