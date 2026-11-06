package ee.schimke.cadence.podcasts

import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.*
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.w3c.dom.Element

abstract class FilePodcastPlatform(protected val root: File) : PodcastPlatform {
  override val supportsOriginalDownloads = true

  override suspend fun originalAvailable(episode: Episode): Boolean =
    withContext(Dispatchers.IO) { source(episode).let { it.isFile && it.length() > 0 } }

  override suspend fun downloadOriginal(
    episode: Episode,
    onProgress: (downloaded: Long, total: Long) -> Unit,
  ) {
    cachedSource(episode, onProgress)
  }

  protected fun source(episode: Episode): File =
    File(root, "source/${episode.key}-${digest(episode.audioVersion)}.${episode.extension}")

  protected suspend fun cachedSource(
    episode: Episode,
    onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
  ): File {
    val file = source(episode)
    if (!withContext(Dispatchers.IO) { file.isFile && file.length() > 0 })
      try {
        download(episode, file, onProgress)
      } catch (e: PodcastProblem) {
        throw httpProblem(e.status) ?: e
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        throw downloadProblem(e) ?: e
      }
    return file
  }

  protected suspend fun copyWhole(episode: Episode, section: Section) =
    withContext(Dispatchers.IO) {
      val original = cachedSource(episode)
      val output = audio(section)
      output.parentFile!!.mkdirs()
      val temp = File(output.parentFile, output.name + ".part")
      try {
        original.copyTo(temp, overwrite = true)
        replacePodcastFile(temp, output)
      } finally {
        temp.delete()
      }
    }

  override suspend fun pruneAudio(episodes: List<Episode>) =
    withContext(Dispatchers.IO) {
      val keys = episodes.map { it.key }.toSet()
      val prefixes = keys.map { it.take(8) }.toSet()
      val original = Regex("^([a-f0-9]{64})-[a-f0-9]{64}\\.[a-z0-9]+$")
      val processed = Regex("^.+-([a-f0-9]{8})-[0-9]{12}-[a-f0-9]{64}\\.[a-z0-9]+$")
      File(root, "source").listFiles()?.forEach { file ->
        val key = original.matchEntire(file.name)?.groupValues?.get(1)
        if (file.isFile && key != null && key !in keys) file.delete()
      }
      File(root, "audio").listFiles()?.forEach { file ->
        val prefix = processed.matchEntire(file.name)?.groupValues?.get(1)
        if (file.isFile && prefix != null && prefix !in prefixes) file.delete()
      }
      Unit
    }

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
            400 -> throw PodcastProblem("The server rejected the request (HTTP 400).", 400)
            401,
            403 ->
              throw PodcastProblem(
                "The server refused the sign-in. Check the user name and password (or the cloud project's row policies) and sign in again.",
                conn.responseCode,
              )
            404 ->
              throw PodcastProblem(
                "Not found (HTTP 404). Check the server URL and server type.",
                404,
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

  /**
   * Opens a public GET, following at most five HTTPS redirects. No account headers are sent, so a
   * redirect cannot leak credentials. The caller must disconnect the returned connection.
   */
  protected fun openPublic(url: String): HttpURLConnection {
    var location = url
    repeat(6) {
      val conn = connection(location)
      val code = conn.responseCode
      if (code !in listOf(301, 302, 303, 307, 308)) {
        if (code == 200) return conn
        conn.disconnect()
        throw PodcastProblem("The server answered HTTP $code. Check the URL is public.", code)
      }
      val next = conn.getHeaderField("Location")
      conn.disconnect()
      location =
        URI(location)
          .resolve(next ?: throw PodcastProblem("Redirect without a location"))
          .toString()
      if (!location.startsWith("https://"))
        throw PodcastProblem("The server redirected to an insecure (HTTP) address")
    }
    throw PodcastProblem("Too many redirects")
  }

  protected suspend fun feedText(url: String): String =
    withContext(Dispatchers.IO) {
      val conn = openPublic(url)
      try {
        conn.inputStream.bufferedReader().use { reader ->
          val text = StringBuilder()
          val buffer = CharArray(64 * 1024)
          while (true) {
            val count = reader.read(buffer)
            if (count < 0) break
            text.append(buffer, 0, count)
            if (text.length > MAX_FEED_CHARS) throw PodcastProblem("Feed is larger than 40 MB")
          }
          text.toString()
        }
      } finally {
        conn.disconnect()
      }
    }

  protected open suspend fun download(
    episode: Episode,
    destination: File,
    onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
  ) =
    withContext(Dispatchers.IO) {
      episode.validate()
      destination.parentFile!!.mkdirs()
      val temp = File(destination.parentFile, destination.name + ".part")
      try {
        val conn = openPublic(episode.audioUrl)
        try {
          val type = conn.contentType?.substringBefore(';')?.trim()?.lowercase()
          if (type != null && !type.isAudioDownload())
            throw PodcastProblem("the server sent $type instead of audio")
          val total = conn.contentLengthLong
          var count = 0L
          onProgress(0, total)
          conn.inputStream.use { source ->
            temp.outputStream().use { output ->
              val buffer = ByteArray(64 * 1024)
              while (true) {
                currentCoroutineContext().ensureActive()
                val read = source.read(buffer)
                if (read < 0) break
                count += read
                if (count > 2_000_000_000L) throw PodcastProblem("the audio is over 2 GB")
                output.write(buffer, 0, read)
                onProgress(count, total)
              }
              output.fd.sync()
            }
          }
          currentCoroutineContext().ensureActive()
          if (count == 0L) throw PodcastProblem("the server sent an empty file")
          if (total >= 0 && total != count)
            throw PodcastProblem("the download stopped early, at ${count * 100 / total}%")
          replacePodcastFile(temp, destination)
        } finally {
          conn.disconnect()
        }
      } finally {
        temp.delete()
      }
    }

  override suspend fun available(section: Section): Boolean =
    withContext(Dispatchers.IO) { audio(section).let { it.isFile && it.length() > 0 } }

  override suspend fun publicText(url: String): String = feedText(url)

  override suspend fun rss(url: String): RssFeed {
    val text = feedText(url)
    val factory =
      DocumentBuilderFactory.newInstance().apply {
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        isXIncludeAware = false
        isExpandEntityReferences = false
      }
    val document = factory.newDocumentBuilder().parse(text.byteInputStream())
    val channel = document.getElementsByTagName("channel").item(0) as? Element
    fun Element.child(name: String) =
      (0 until childNodes.length)
        .map { childNodes.item(it) }
        .firstOrNull { it is Element && it.tagName == name }
        ?.textContent
        ?.trim()
        .orEmpty()
    val items = document.getElementsByTagName("item")
    return RssFeed(
      channel?.child("title").orEmpty(),
      (0 until items.length).map { index ->
        val item = items.item(index) as Element
        val enclosure = item.getElementsByTagName("enclosure").item(0) as? Element
        RssItem(
          item.child("guid"),
          item.child("title"),
          enclosure?.getAttribute("url").orEmpty(),
          item.child("itunes:duration"),
          enclosure?.getAttribute("length").orEmpty(),
          enclosure?.getAttribute("type").orEmpty(),
          item.child("description").ifBlank { item.child("itunes:summary") },
          (item.getElementsByTagName("podcast:transcript").item(0) as? Element)
            ?.getAttribute("url")
            .orEmpty(),
          item.child("pubDate"),
        )
      },
    )
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

/** Podcast hosts label audio loosely; anything that is clearly a web page or text is rejected. */
private fun String.isAudioDownload() =
  startsWith("audio/") ||
    this in
      setOf(
        "application/octet-stream",
        "binary/octet-stream",
        "application/binary",
        "application/mp4",
        "video/mp4",
        "application/ogg",
        "application/x-download",
        "application/force-download",
      )

/** Why an audio download got [status], in words that suggest what to do. */
private fun httpProblem(status: Int): PodcastProblem? =
  when (status) {
    0 -> null
    401,
    403 -> PodcastProblem("the server refused access (HTTP $status)", status)
    404,
    410 -> PodcastProblem("the audio is no longer at its address (HTTP $status)", status)
    429 -> PodcastProblem("the server is limiting downloads (HTTP 429), try later", status)
    in 500..599 -> PodcastProblem("the server had an error (HTTP $status), try later", status)
    else -> PodcastProblem("the server answered HTTP $status", status)
  }

/**
 * A short, URL-free reason for common network failures; null when there's nothing useful to say.
 */
private fun downloadProblem(e: Exception): PodcastProblem? =
  when (e) {
    is java.net.UnknownHostException -> PodcastProblem("no internet connection")
    is java.net.SocketTimeoutException -> PodcastProblem("the server stopped responding")
    is java.net.ConnectException -> PodcastProblem("couldn't reach the server")
    is javax.net.ssl.SSLException -> PodcastProblem("the secure connection failed")
    is java.io.IOException ->
      if (e.message?.contains("No space left", ignoreCase = true) == true)
        PodcastProblem("the phone is out of storage")
      else PodcastProblem("the connection dropped")
    // Our own checks (episode validation, HTTPS only) carry fixed, URL-free messages.
    is IllegalArgumentException ->
      PodcastProblem(
        e.message?.takeUnless { it == "Failed requirement." }
          ?: "the audio address isn't a valid HTTPS URL"
      )
    else -> null
  }
