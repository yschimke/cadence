package ee.schimke.cadence.podcasts

import android.util.Xml
import org.xmlpull.v1.XmlPullParser

/** Android's pull parser avoids JAXP features that are unavailable on some Android releases. */
internal fun parseAndroidRss(text: String, url: String): PlaylistSnapshot {
  require(!text.contains("<!DOCTYPE", ignoreCase = true)) { "DOCTYPE is not supported" }
  val parser = Xml.newPullParser()
  parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
  parser.setInput(text.reader())
  val episodes = mutableListOf<Episode>()
  var feedName = "RSS podcast"
  var item: MutableMap<String, String>? = null
  while (parser.next() != XmlPullParser.END_DOCUMENT) {
    if (parser.eventType == XmlPullParser.START_TAG) {
      when (parser.name) {
        "item" -> item = mutableMapOf()
        "enclosure" -> item?.apply {
          put("audio", parser.getAttributeValue(null, "url").orEmpty())
          put("length", parser.getAttributeValue(null, "length").orEmpty())
          put("type", parser.getAttributeValue(null, "type").orEmpty())
        }
        "title", "guid", "itunes:duration" -> {
          val name = parser.name
          val value = parser.nextText().trim()
          if (item != null) item[name] = value else if (name == "title") feedName = value
        }
      }
    } else if (parser.eventType == XmlPullParser.END_TAG && parser.name == "item") {
      val fields = requireNotNull(item)
      val audio = fields["audio"].orEmpty()
      episodes += Episode("rss", "$url#${fields["guid"].orEmpty().ifBlank { audio }}", fields["title"].orEmpty(), audio,
        rssDurationMs(fields["itunes:duration"].orEmpty()), revision = "rss", audioVersion = digest(audio + "|" + fields["length"].orEmpty()),
        extension = when (fields["type"]?.substringBefore(';')) {
          "audio/mp4", "audio/x-m4a" -> "m4a"; "audio/ogg" -> "ogg"; "audio/flac" -> "flac"; else -> "mp3"
        })
      item = null
      if (episodes.size == 1000) break
    }
  }
  return PlaylistSnapshot(name = feedName, episodes = episodes).also { it.validate() }
}
