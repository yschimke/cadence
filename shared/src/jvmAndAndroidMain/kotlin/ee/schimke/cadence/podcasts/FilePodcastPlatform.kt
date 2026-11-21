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

  /** The original track files of a joined episode, kept beside it as 0000.mp3, 0001.mp3, … */
  private fun tracks(episode: Episode): File =
    File(root, "source/${episode.key}-${digest(episode.audioVersion)}.tracks")

  override suspend fun trackLengths(episode: Episode): List<Long> =
    withContext(Dispatchers.IO) {
      File(tracks(episode), LENGTHS)
        .takeIf { it.isFile }
        ?.readLines()
        ?.mapNotNull { it.trim().toLongOrNull()?.takeIf { length -> length > 0 } } ?: emptyList()
    }

  /**
   * Copies each section that is a whole original track straight from that track's file, untouched,
   * and returns the sections still to prepare.
   */
  protected suspend fun copyTracks(episode: Episode, sections: List<Section>): List<Section> {
    if (episode.trackMs.isEmpty()) return sections
    val ranges = episode.trackRanges()
    val dir = tracks(episode)
    return sections.filterNot { section ->
      val index = ranges.indexOf(section.startMs to section.endMs)
      val track = File(dir, trackName(index))
      if (index < 0 || !withContext(Dispatchers.IO) { track.isFile }) return@filterNot false
      if (!available(section))
        withContext(Dispatchers.IO) {
          val output = audio(section)
          output.parentFile!!.mkdirs()
          val temp = File(output.parentFile, output.name + ".part")
          try {
            track.copyTo(temp, overwrite = true)
            replacePodcastFile(temp, output)
          } finally {
            temp.delete()
          }
        }
      true
    }
  }

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
      val kept = Regex("^([a-f0-9]{64})-[a-f0-9]{64}\\.tracks(\\.part)?$")
      File(root, "source").listFiles()?.forEach { file ->
        val key = original.matchEntire(file.name)?.groupValues?.get(1)
        if (file.isFile && key != null && key !in keys) file.delete()
        val tracksKey = kept.matchEntire(file.name)?.groupValues?.get(1)
        if (file.isDirectory && tracksKey != null && tracksKey !in keys) file.deleteRecursively()
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
      section.fileName.substringAfterLast('.') in
        listOf("mp3", "m4a", "ogg", "opus", "flac", "wav"),
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
                "Cloud revision conflict. Restore cloud configuration, reapply local settings, and save again.",
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
    if (episode.audioParts.isNotEmpty()) downloadParts(episode, destination, onProgress)
    else downloadWhole(episode, destination, onProgress)

  /**
   * Fetches each zip archive in [Episode.audioParts], takes its MP3 tracks in order and joins them
   * into [destination]. Progress counts whole parts, each weighted equally.
   */
  private suspend fun downloadParts(
    episode: Episode,
    destination: File,
    onProgress: (downloaded: Long, total: Long) -> Unit,
  ) =
    withContext(Dispatchers.IO) {
      episode.validate()
      destination.parentFile!!.mkdirs()
      val work = File(destination.parentFile, destination.name + ".tracks")
      val temp = File(destination.parentFile, destination.name + ".part")
      val parts = episode.audioParts.size
      try {
        work.deleteRecursively()
        work.mkdirs()
        val tracks = mutableListOf<File>()
        episode.audioParts.forEachIndexed { index, url ->
          val zip = File(work, "part-$index.zip")
          fetchTo(url, zip) { bytes, total ->
            if (total > 0)
              onProgress(index * PART_SCALE + bytes * PART_SCALE / total, parts * PART_SCALE)
          }
          tracks += extractMp3Tracks(zip, File(work, "part-$index"))
          zip.delete()
        }
        if (tracks.isEmpty()) throw PodcastProblem("the download had no MP3 tracks")
        joinMp3Tracks(tracks, temp)
        // The original files and their lengths stay beside the joined one, for copying as-is.
        val kept = tracks(episode)
        val staged = File(kept.parentFile, kept.name + ".part")
        staged.deleteRecursively()
        staged.mkdirs()
        tracks.forEachIndexed { index, track -> track.renameTo(File(staged, trackName(index))) }
        File(staged, LENGTHS)
          .writeText(
            staged
              .listFiles()!!
              .filter { it.name.endsWith(".mp3") }
              .sortedBy { it.name }
              .joinToString("\n") { mp3DurationMs(it).toString() },
          )
        kept.deleteRecursively()
        check(staged.renameTo(kept)) { "Couldn't keep the original tracks" }
        onProgress(parts * PART_SCALE, parts * PART_SCALE)
        replacePodcastFile(temp, destination)
      } finally {
        work.deleteRecursively()
        temp.delete()
      }
    }

  /** Streams a public HTTPS file to [target], cancellable, refusing anything over 2 GB. */
  private suspend fun fetchTo(url: String, target: File, onProgress: (Long, Long) -> Unit) {
    val conn = openPublic(url)
    try {
      val total = conn.contentLengthLong
      var count = 0L
      conn.inputStream.use { source ->
        target.outputStream().use { output ->
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
        }
      }
      if (total >= 0 && total != count)
        throw PodcastProblem("the download stopped early, at ${count * 100 / total}%")
    } finally {
      conn.disconnect()
    }
  }

  private suspend fun downloadWhole(
    episode: Episode,
    destination: File,
    onProgress: (downloaded: Long, total: Long) -> Unit,
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
      channel
        ?.let { (0 until it.childNodes.length).map(it.childNodes::item) }
        ?.firstOrNull { it is Element && it.tagName == "itunes:image" }
        ?.let { (it as Element).getAttribute("href") }
        .orEmpty(),
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

/** Fixed, URL-free messages from [Episode.validate] and the download's own checks. */
private val SAFE_VALIDATION_MESSAGES =
  setOf(
    "Invalid episode duration or position",
    "Unsupported audio format",
    "Audio requires an HTTPS enclosure URL",
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
    // Only our own validation messages are shown verbatim: others (URI parsing, for one) can
    // carry the whole redirect URL, signed tokens included.
    is IllegalArgumentException ->
      PodcastProblem(
        e.message?.takeIf { it in SAFE_VALIDATION_MESSAGES }
          ?: "the audio address isn't a valid HTTPS URL",
      )
    is java.net.URISyntaxException -> PodcastProblem("the audio address isn't a valid HTTPS URL")
    else -> null
  }

private const val LENGTHS = "lengths.txt"

private fun trackName(index: Int) = index.toString().padStart(4, '0') + ".mp3"

/** Progress units per part when joining several downloads, so whole parts weigh the same. */
private const val PART_SCALE = 1_000_000L

/**
 * The MP3 entries of [zip], written to [into] in track order (by name, numbers compared as
 * numbers). Entry names never become paths, so an archive can't write outside [into].
 */
internal fun extractMp3Tracks(zip: File, into: File): List<File> {
  into.mkdirs()
  val names = mutableListOf<Pair<String, File>>()
  java.util.zip.ZipInputStream(zip.inputStream().buffered()).use { input ->
    while (true) {
      val entry = input.nextEntry ?: break
      if (!entry.isDirectory && entry.name.endsWith(".mp3", ignoreCase = true)) {
        val file = File(into, "track-${names.size}.mp3")
        file.outputStream().use { input.copyTo(it) }
        names += entry.name.substringAfterLast('/') to file
      }
      input.closeEntry()
    }
  }
  return names.sortedWith(compareBy(naturalOrder) { it.first }).map { it.second }
}

/** "Track 2" before "Track 10". */
private val naturalOrder =
  Comparator<String> { a, b ->
    val chunk = Regex("\\d+|\\D+")
    val left = chunk.findAll(a.lowercase()).map { it.value }.toList()
    val right = chunk.findAll(b.lowercase()).map { it.value }.toList()
    for (i in 0 until minOf(left.size, right.size)) {
      val x = left[i]
      val y = right[i]
      val order =
        if (x[0].isDigit() && y[0].isDigit())
          x.trimStart('0').length.compareTo(y.trimStart('0').length).takeIf { it != 0 }
            ?: x.trimStart('0').compareTo(y.trimStart('0'))
        else x.compareTo(y)
      if (order != 0) return@Comparator order
    }
    left.size.compareTo(right.size)
  }

/**
 * Joins MP3 [tracks] into [out]. MP3 is a stream of self-contained frames, so tracks join end to
 * end. Each track's ID3v2 header (after the first) and ID3v1 trailer are left out so tag bytes
 * don't play as noise between chapters, and every track's Xing/Info or VBRI frame is left out: it
 * counts only that track's frames, so players would take the first chapter's length (and seek
 * table) for the whole book. Without it they work from the bitrate, which is exact for the
 * constant-bitrate MP3s audiobooks use. Tracks are streamed, never read whole: an audiobook track
 * can be larger than an Android app's heap.
 */
internal fun joinMp3Tracks(tracks: List<File>, out: File) {
  out.outputStream().buffered().use { output ->
    tracks.forEachIndexed { index, track ->
      val (tags, start, end) = Mp3Window(track).use { it.audioRange() }
      track.inputStream().buffered().use { input ->
        if (index == 0) copyRange(input, output, 0, tags) else input.skipFully(tags)
        input.skipFully(start - tags)
        copyRange(input, output, 0, end - start)
      }
    }
  }
}

private fun java.io.InputStream.skipFully(count: Long) {
  var left = count
  while (left > 0) {
    val skipped = skip(left)
    if (skipped <= 0) {
      if (read() < 0) return
      left--
    } else left -= skipped
  }
}

/** Copies [length] bytes from [input] (after skipping [skip]) to [output]. */
private fun copyRange(
  input: java.io.InputStream,
  output: java.io.OutputStream,
  skip: Long,
  length: Long,
) {
  input.skipFully(skip)
  val buffer = ByteArray(64 * 1024)
  var left = length
  while (left > 0) {
    val read = input.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
    if (read < 0) break
    output.write(buffer, 0, read)
    left -= read
  }
}

/** Layer III bitrates in kbps by index, for MPEG-1 and for MPEG-2/2.5. */
private val mpeg1Bitrates =
  intArrayOf(0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320)
private val mpeg2Bitrates = intArrayOf(0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160)

/** An MPEG audio Layer III frame header: its length in bytes, samples and sample rate. */
private class Mp3Frame(val length: Int, val samples: Int, val sampleRate: Int, val sideInfo: Int)

/**
 * Random access to an MP3's bytes through one 64 KB window, so headers anywhere in a large file can
 * be read without loading it.
 */
private class Mp3Window(private val size: Long, private val read: (Long, ByteArray) -> Int) :
  AutoCloseable {
  private var file: java.io.RandomAccessFile? = null

  constructor(file: File) : this(file.length(), { _, _ -> 0 }) {
    this.file = java.io.RandomAccessFile(file, "r")
  }

  constructor(
    bytes: ByteArray,
  ) : this(
    bytes.size.toLong(),
    { at, into ->
      val n = minOf(into.size.toLong(), bytes.size - at).toInt()
      bytes.copyInto(into, 0, at.toInt(), at.toInt() + n)
      n
    },
  )

  private val window = ByteArray(64 * 1024)
  private var windowStart = -1L
  private var windowLength = 0

  /** The byte at [at], or -1 past the end. */
  operator fun get(at: Long): Int {
    if (at < 0 || at >= size) return -1
    if (at < windowStart || at >= windowStart + windowLength) {
      windowStart = at
      windowLength =
        file?.let { raf ->
          raf.seek(at)
          var filled = 0
          while (filled < window.size) {
            val n = raf.read(window, filled, window.size - filled)
            if (n < 0) break
            filled += n
          }
          filled
        } ?: read(at, window)
    }
    return window[(at - windowStart).toInt()].toInt() and 0xff
  }

  private fun text(at: Long, length: Int) =
    String(CharArray(length) { (this[at + it].takeIf { b -> b >= 0 } ?: 0).toChar() })

  /** The length of a leading ID3v2 tag (header, body and any footer), or 0 without one. */
  fun id3v2Length(): Long {
    if (size < 10 || text(0, 3) != "ID3") return 0
    val length =
      (this[6].toLong() and 0x7f shl 21) or
        (this[7].toLong() and 0x7f shl 14) or
        (this[8].toLong() and 0x7f shl 7) or
        (this[9].toLong() and 0x7f)
    val footer = if (this[5] and 0x10 != 0) 10 else 0
    return (10 + length + footer).coerceAtMost(size)
  }

  fun frame(offset: Long): Mp3Frame? {
    if (offset + 4 > size) return null
    val b1 = this[offset + 1]
    val b2 = this[offset + 2]
    val b3 = this[offset + 3]
    // Frame sync and Layer III.
    if (this[offset] != 0xff || b1 and 0xe0 != 0xe0 || b1 shr 1 and 3 != 1) return null
    val version = b1 shr 3 and 3 // 3 = MPEG-1, 2 = MPEG-2, 0 = MPEG-2.5
    if (version == 1) return null
    val mpeg1 = version == 3
    val bitrateIndex = b2 shr 4
    val rateIndex = b2 shr 2 and 3
    if (bitrateIndex !in 1..14 || rateIndex == 3) return null
    val sampleRate =
      intArrayOf(44100, 48000, 32000)[rateIndex] / if (mpeg1) 1 else if (version == 2) 2 else 4
    val bitrate = (if (mpeg1) mpeg1Bitrates else mpeg2Bitrates)[bitrateIndex] * 1000
    val padding = b2 shr 1 and 1
    val length = (if (mpeg1) 144 else 72) * bitrate / sampleRate + padding
    val mono = b3 shr 6 == 3
    val sideInfo = if (mpeg1) (if (mono) 17 else 32) else if (mono) 9 else 17
    return Mp3Frame(length, if (mpeg1) 1152 else 576, sampleRate, sideInfo)
  }

  /** The Xing/Info or VBRI frame's length at [offset], or 0 when it is ordinary audio. */
  fun infoFrameLength(offset: Long): Long {
    val frame = frame(offset) ?: return 0
    fun tagAt(position: Int) =
      offset + position + 4 <= size &&
        text(offset + position, 4).let { it == "Xing" || it == "Info" || it == "VBRI" }
    return if (tagAt(4 + frame.sideInfo) || tagAt(36))
      frame.length.toLong().coerceAtMost(size - offset)
    else 0
  }

  /** (end of the ID3v2 tag, start of the audio, end of the audio before any ID3v1 trailer). */
  fun audioRange(): Triple<Long, Long, Long> {
    val tags = id3v2Length()
    val start = tags + infoFrameLength(tags)
    val end = if (size - start >= 128 && text(size - 128, 3) == "TAG") size - 128 else size
    return Triple(tags, start, maxOf(start, end))
  }

  /** Playing time from the frames, resynchronising past stray bytes. */
  fun durationMs(): Long {
    var (_, offset, end) = audioRange()
    var samples = 0L
    var sampleRate = 0
    while (offset < end) {
      val frame = frame(offset)
      if (frame == null || frame.length <= 4) {
        offset++
        continue
      }
      samples += frame.samples
      sampleRate = frame.sampleRate
      offset += frame.length
    }
    return if (sampleRate == 0) 0 else samples * 1000 / sampleRate
  }

  override fun close() {
    file?.close()
  }
}

/**
 * The length of the Xing/Info or VBRI frame at [offset] (the first MPEG audio frame), or 0 when the
 * first frame is ordinary audio.
 */
internal fun infoFrameLength(bytes: ByteArray, offset: Int): Int =
  Mp3Window(bytes).infoFrameLength(offset.toLong()).toInt()

/** An MP3's playing time from its frames (each Layer III frame is 1152 or 576 samples). */
internal fun mp3DurationMs(bytes: ByteArray): Long = Mp3Window(bytes).durationMs()

/** The same, streamed from [file]. */
internal fun mp3DurationMs(file: File): Long = Mp3Window(file).use { it.durationMs() }
