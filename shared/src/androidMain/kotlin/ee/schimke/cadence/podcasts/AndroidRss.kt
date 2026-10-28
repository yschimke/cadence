package ee.schimke.cadence.podcasts

import android.util.Xml
import org.xmlpull.v1.XmlPullParser

/** Android's pull parser avoids JAXP features that are unavailable on some Android releases. */
internal fun parseAndroidRss(text: String): RssFeed {
  require(!text.contains("<!DOCTYPE", ignoreCase = true)) { "DOCTYPE is not supported" }
  val parser = Xml.newPullParser()
  parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
  parser.setInput(text.reader())
  val items = mutableListOf<RssItem>()
  var feedName = ""
  var item: MutableMap<String, String>? = null
  var itemDepth = 0
  while (parser.next() != XmlPullParser.END_DOCUMENT) {
    if (parser.eventType == XmlPullParser.START_TAG) {
      when (parser.name) {
        "item" -> {
          item = mutableMapOf()
          itemDepth = parser.depth
        }
        "enclosure" ->
          item
            ?.takeIf { "audio" !in it }
            ?.apply {
              put("audio", parser.getAttributeValue(null, "url").orEmpty())
              put("length", parser.getAttributeValue(null, "length").orEmpty())
              put("type", parser.getAttributeValue(null, "type").orEmpty())
            }
        "title",
        "guid",
        "itunes:duration" -> {
          val name = parser.name
          // Only direct children of <item>, and the first channel title (not <image><title>).
          val direct = item != null && parser.depth == itemDepth + 1
          val value = parser.nextText().trim()
          if (direct) item!!.putIfAbsent(name, value)
          else if (item == null && name == "title" && feedName.isEmpty()) feedName = value
        }
      }
    } else if (parser.eventType == XmlPullParser.END_TAG && parser.name == "item") {
      val fields = requireNotNull(item)
      items +=
        RssItem(
          fields["guid"].orEmpty(),
          fields["title"].orEmpty(),
          fields["audio"].orEmpty(),
          fields["itunes:duration"].orEmpty(),
          fields["length"].orEmpty(),
          fields["type"].orEmpty(),
        )
      item = null
    }
  }
  return RssFeed(feedName, items)
}
