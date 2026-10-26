package ee.schimke.cadence.podcasts

import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.*
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Element

abstract class FilePodcastPlatform(protected val root: File) : PodcastPlatform {
  protected fun audio(section: Section): File {
    validateAudioFileName(section.fileName)
    require(section.fileName.substringBeforeLast('.').endsWith(section.id))
    require(section.id.matches(Regex("[a-f0-9]{64}")))
    require(
      section.fileName.substringAfterLast('.') in listOf("mp3", "m4a", "ogg", "opus", "flac", "wav")
    )
    return File(root, "audio/${section.fileName}")
  }

  override suspend fun readState(): String? =
    withContext(Dispatchers.IO) { File(root, "library.json").takeIf { it.exists() }?.readText() }

  override suspend fun saveState(state: String) =
    withContext(Dispatchers.IO) {
      root.mkdirs()
      val temp = Files.createTempFile(root.toPath(), "library-", ".tmp").toFile()
      temp.outputStream().use {
        it.write(state.toByteArray())
        it.fd.sync()
      }
      try {
        replacePodcastFile(temp, File(root, "library.json"))
      } finally {
        temp.delete()
      }
    }

  override suspend fun request(
    url: String,
    method: String,
    headers: Map<String, String>,
    body: String?,
  ): String =
    withContext(Dispatchers.IO) {
      connection(url, method, headers).let { conn ->
        try {
          if (body != null) {
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toByteArray()) }
          }
          when (conn.responseCode) {
            401,
            403 ->
              throw PodcastProblem(
                "Sign in again or check the cloud project's authentication and row policies."
              )
            409 ->
              throw PodcastProblem(
                "Cloud revision conflict. Restore cloud configuration, reapply local settings, and save again."
              )
          }
          check(conn.responseCode in 200..299) { "Request failed" }
          conn.inputStream.bufferedReader().use { reader ->
            val text = StringBuilder()
            val buffer = CharArray(8192)
            while (true) {
              val count = reader.read(buffer)
              if (count < 0) break
              text.append(buffer, 0, count)
              require(text.length <= 8_000_000)
            }
            text.toString()
          }
        } finally {
          conn.disconnect()
        }
      }
    }

  private fun connection(
    url: String,
    method: String = "GET",
    headers: Map<String, String> = emptyMap(),
  ): HttpURLConnection {
    val uri = URI(url)
    require(uri.scheme == "https" && uri.userInfo == null)
    return (uri.toURL().openConnection() as HttpURLConnection).apply {
      requestMethod = method
      connectTimeout = 30_000
      readTimeout = 60_000
      // Never forward credentials across a redirect. Public enclosure downloads handle redirects
      // below.
      instanceFollowRedirects = false
      headers.forEach { (key, value) -> setRequestProperty(key, value) }
    }
  }

  protected open suspend fun download(episode: Episode, destination: File) =
    withContext(Dispatchers.IO) {
      episode.validate()
      destination.parentFile!!.mkdirs()
      val temp = File(destination.parentFile, destination.name + ".part")
      try {
        var location = episode.audioUrl
        var downloaded = false
        for (attempt in 0..5) {
          val conn = connection(location)
          try {
            val code = conn.responseCode
            if (code in listOf(301, 302, 303, 307, 308)) {
              location =
                URI(location).resolve(requireNotNull(conn.getHeaderField("Location"))).toString()
              continue
            }
            require(code == 200)
            require(
              conn.contentType?.substringBefore(';')?.let {
                it.startsWith("audio/") || it == "application/octet-stream"
              } == true
            ) {
              "Not downloadable audio"
            }
            var count = 0L
            conn.inputStream.use { source ->
              temp.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                  val read = source.read(buffer)
                  if (read < 0) break
                  count += read
                  require(count <= 2_000_000_000L) { "Audio exceeds 2 GB limit" }
                  output.write(buffer, 0, read)
                }
                output.fd.sync()
              }
            }
            require(count > 0 && (conn.contentLengthLong < 0 || conn.contentLengthLong == count))
            replacePodcastFile(temp, destination)
            downloaded = true
            break
          } finally {
            conn.disconnect()
          }
        }
        check(downloaded)
      } finally {
        temp.delete()
      }
    }

  override suspend fun available(section: Section): Boolean =
    withContext(Dispatchers.IO) { audio(section).let { it.isFile && it.length() > 0 } }

  override suspend fun rss(url: String): PlaylistSnapshot {
    val text = request(url)
    val factory =
      DocumentBuilderFactory.newInstance().apply {
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        isXIncludeAware = false
        isExpandEntityReferences = false
      }
    val document = factory.newDocumentBuilder().parse(text.byteInputStream())
    val items = document.getElementsByTagName("item")
    val episodes =
      (0 until items.length).take(1000).map { index ->
        val item = items.item(index) as Element
        fun field(name: String) =
          item.getElementsByTagName(name).item(0)?.textContent?.trim().orEmpty()
        val enclosure =
          item.getElementsByTagName("enclosure").item(0) as? Element ?: error("Missing enclosure")
        val audioUrl = enclosure.getAttribute("url")
        val duration = rssDurationMs(field("itunes:duration"))
        val id = field("guid").ifBlank { audioUrl }
        Episode(
          "rss",
          "$url#$id",
          field("title"),
          audioUrl,
          duration,
          revision = "rss",
          audioVersion = digest(audioUrl + "|" + enclosure.getAttribute("length")),
          extension =
            when (enclosure.getAttribute("type").substringBefore(';')) {
              "audio/mp4",
              "audio/x-m4a" -> "m4a"
              "audio/ogg" -> "ogg"
              "audio/flac" -> "flac"
              else -> "mp3"
            },
        )
      }
    val name =
      document.getElementsByTagName("channel").item(0)?.let {
        (it as Element).getElementsByTagName("title").item(0)?.textContent
      } ?: "RSS podcast"
    return PlaylistSnapshot(name = name, episodes = episodes).also { it.validate() }
  }
}

/**
 * Replace an existing snapshot on Windows as well as Unix; avoid File.renameTo platform
 * differences.
 */
fun replacePodcastFile(source: File, target: File) {
  try {
    Files.move(source.toPath(), target.toPath(), REPLACE_EXISTING, ATOMIC_MOVE)
  } catch (_: AtomicMoveNotSupportedException) {
    Files.move(source.toPath(), target.toPath(), REPLACE_EXISTING)
  }
}
