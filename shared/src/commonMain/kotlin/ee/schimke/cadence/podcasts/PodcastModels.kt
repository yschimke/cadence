package ee.schimke.cadence.podcasts

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.encodeUtf8

val podcastJson = Json {
  encodeDefaults = true
  ignoreUnknownKeys = true
}

@Serializable
data class Episode(
  val backend: String,
  val id: String,
  val title: String,
  val audioUrl: String,
  val durationMs: Long,
  val positionMs: Long = 0,
  val revision: String,
  // An immutable enclosure version. Changes (including dynamic ads) require a new version.
  val audioVersion: String,
  val extension: String = "mp3",
) {
  val key: String
    get() = digest("$backend\u0000$id")

  fun validate() {
    require(
      backend.isNotBlank() && id.isNotBlank() && revision.isNotBlank() && audioVersion.isNotBlank()
    )
    require(durationMs in 1..2_592_000_000L && positionMs in 0..durationMs) {
      "Invalid episode duration or position"
    }
    require(extension in listOf("mp3", "m4a", "ogg", "opus", "flac", "wav")) {
      "Unsupported audio format"
    }
    require(audioUrl.startsWith("https://")) { "Audio requires an HTTPS enclosure URL" }
  }
}

@Serializable
data class PlaylistSnapshot(
  val schema: Int = 1,
  val name: String,
  val episodes: List<Episode>,
) {
  fun validate() {
    require(schema == 1 && name.isNotBlank()) { "Unsupported or unnamed snapshot" }
    require(episodes.size <= 1000) { "A snapshot may contain at most 1000 episodes" }
    episodes.forEach(Episode::validate)
    require(episodes.map { it.key }.distinct().size == episodes.size) { "Duplicate episodes" }
  }
}

@Serializable
data class Segmentation(
  val splitAtPosition: Boolean = true,
  val chunkMinutes: Int = 0,
  val chunkFormat: String = "mp3",
  // Leave out audio before the imported position. Defaults off so stored manifests stay valid.
  val skipListened: Boolean = false,
) {
  fun validate() {
    require(chunkFormat in listOf("mp3", "m4a"))
    require(chunkMinutes in 0..120) { "Chunks must be 0 (off) or 1–120 minutes" }
  }
}

@Serializable
data class Section(
  val id: String,
  val episodeKey: String,
  val audioVersion: String,
  val startMs: Long,
  val endMs: Long,
  val fileName: String,
)

/** `m:ss` or `h:mm:ss`. */
fun formatTime(ms: Long): String {
  val seconds = ms / 1000
  val h = seconds / 3600
  val m = seconds / 60 % 60
  val s = (seconds % 60).toString().padStart(2, '0')
  return if (h > 0) "$h:${m.toString().padStart(2, '0')}:$s" else "$m:$s"
}

fun digest(value: String): String = value.encodeUtf8().sha256().hex()

fun segment(episode: Episode, config: Segmentation): List<Section> {
  episode.validate()
  config.validate()
  val resumable = episode.positionMs > 0 && episode.positionMs < episode.durationMs
  val boundaries = mutableListOf(if (config.skipListened && resumable) episode.positionMs else 0L)
  if (config.splitAtPosition && resumable && !config.skipListened) boundaries += episode.positionMs
  boundaries += episode.durationMs
  val ranges =
    boundaries.zipWithNext().flatMap { (start, end) ->
      val size = if (config.chunkMinutes == 0) end - start else config.chunkMinutes * 60_000L
      buildList {
        var offset = start
        while (offset < end) {
          val next = minOf(end, offset + size)
          add(offset to next)
          offset = next
          require(size > 0 && this.size <= 10_000) { "Too many sections" }
        }
      }
    }
  require(ranges.size <= 10_000) { "Too many sections" }
  // Only the untouched whole file keeps the source format; any clipped range is re-encoded.
  val whole = ranges.size == 1 && ranges[0] == 0L to episode.durationMs
  return ranges.map { (start, end) ->
    val id = digest("${episode.key}|${episode.audioVersion}|$start|$end")
    val title =
      episode.title.replace(Regex("[^a-zA-Z0-9]"), "_").take(40).trim('_').ifBlank { "episode" }
    val name = "$title-${episode.key.take(8)}-${start.toString().padStart(12, '0')}-$id"
    Section(
      id,
      episode.key,
      episode.audioVersion,
      start,
      end,
      "$name.${if (whole) episode.extension else config.chunkFormat}",
    )
  }
}

@Serializable
data class Manifest(val episode: Episode, val config: Segmentation, val sections: List<Section>)

@Serializable
data class Completion(
  val sectionId: String,
  val observedRevision: String,
  val episodeKey: String,
  val audioVersion: String,
  val startMs: Long,
  val endMs: Long,
)

/**
 * Only contiguous completion after the backend position advances playback; gaps never skip audio.
 */
fun advancedPosition(episode: Episode, completions: List<Completion>): Long {
  var position = episode.positionMs
  val intervals =
    completions
      .filter {
        it.episodeKey == episode.key &&
          it.audioVersion == episode.audioVersion &&
          it.observedRevision == episode.revision
      }
      .sortedBy { it.startMs }
  for (interval in intervals) {
    if (interval.startMs <= position && interval.endMs > position)
      position = minOf(interval.endMs, episode.durationMs)
  }
  return position
}

/** Where the current playlist came from, so it can be refreshed from the same place. */
@Serializable
enum class PodcastSourceKind {
  PocketCasts,
  Rss,
  Gpodder,
  Overcast,
  Snapshot,
}

@Serializable
data class PodcastLibrary(
  val schema: Int = 1,
  val playlist: PlaylistSnapshot? = null,
  val source: PodcastSourceKind? = null,
  // The Pocket Casts list (Up Next, In progress or a playlist id) for a Pocket Casts source.
  val sourceList: String = "",
  val feedUrl: String = "",
  val feedLatest: Int = 3,
  val config: Segmentation = Segmentation(),
  val manifests: List<Manifest> = emptyList(),
  val completions: List<Completion> = emptyList(),
  val lastBackendSync: String = "Never",
) {
  fun validate() {
    require(schema == 1)
    config.validate()
    require(feedUrl.isBlank() || feedUrl.startsWith("https://"))
    require(feedLatest in 1..MAX_FEED_EPISODES)
    playlist?.validate()
    manifests.forEach { manifest ->
      require(manifest.sections == segment(manifest.episode, manifest.config)) {
        "Invalid timeline manifest"
      }
    }
    completions.forEach {
      require(it.observedRevision.isNotBlank() && it.startMs >= 0 && it.endMs > it.startMs)
      require(
        it.sectionId == digest("${it.episodeKey}|${it.audioVersion}|${it.startMs}|${it.endMs}")
      ) {
        "Invalid completion"
      }
    }
  }
}

@Serializable
data class LocalPodcastState(
  val library: PodcastLibrary = PodcastLibrary(),
  // These fields are device-local, never uploaded as cross-device readiness.
  val readySectionIds: Set<String> = emptySet(),
  val target: String = "",
  val copiedSectionIds: Set<String> = emptySet(),
  val cloudUrl: String = "",
  val cloudKey: String = "",
  val cloudRevision: Long = 0,
  val cloudUserId: String = "",
  // Server and user only; the password is held in memory for the app session.
  val gpodder: GpodderAccount = GpodderAccount(),
  // Positions this device sent to the source, by episode key, for the status shown per episode.
  val sentPositions: Map<String, Long> = emptyMap(),
  val pocketCasts: PocketCastsSession = PocketCastsSession(),
)

/** Capability checks precede any authentication, download or write. */
data class BackendCapabilities(
  val selection: Boolean,
  val audio: Boolean,
  val positionWrites: Boolean,
  val explanation: String,
)

interface PodcastBackend {
  val id: String
  val capabilities: BackendCapabilities

  suspend fun playlists(): List<String>

  suspend fun snapshot(playlist: String): PlaylistSnapshot

  // Implementations MUST compare expectedRevision and deduplicate operationId server-side.
  suspend fun writePosition(
    episode: Episode,
    positionMs: Long,
    expectedRevision: String,
    operationId: String,
  ): Episode
}

object PocketCastsBackend : PodcastBackend {
  override val id = "pocket-casts"
  override val capabilities =
    BackendCapabilities(
      false,
      false,
      false,
      "Pocket Casts has no public API. Cadence reads Up Next, in-progress episodes and manual playlists through its private web-player API (see PocketCastsApi); positions are not written back.",
    )

  override suspend fun playlists(): List<String> = error(capabilities.explanation)

  override suspend fun snapshot(playlist: String): PlaylistSnapshot =
    error(capabilities.explanation)

  override suspend fun writePosition(
    episode: Episode,
    positionMs: Long,
    expectedRevision: String,
    operationId: String,
  ): Episode = error(capabilities.explanation)
}

fun rssDurationMs(value: String): Long {
  val parts = value.split(':')
  require(parts.size in 1..3)
  val seconds =
    parts.fold(0.0) { total, part ->
      val number =
        requireNotNull(part.toDoubleOrNull()) {
          "Feed needs episode durations; use a snapshot for this feed"
        }
      require(number.isFinite() && number >= 0)
      total * 60 + number
    }
  require(seconds > 0 && seconds < 30 * 24 * 3600)
  return (seconds * 1000).toLong()
}

/** Raw `<item>` fields as written in the feed; [rssSnapshot] decides which are usable. */
data class RssItem(
  val guid: String,
  val title: String,
  val audioUrl: String,
  val duration: String,
  val length: String,
  val type: String,
)

data class RssFeed(val name: String, val items: List<RssItem>)

data class RssImport(val snapshot: PlaylistSnapshot, val skipped: Int)

const val MAX_FEED_CHARS = 40_000_000

fun rssExtension(type: String): String =
  when (type.substringBefore(';').trim().lowercase()) {
    "audio/mp4",
    "audio/x-m4a",
    "audio/m4a",
    "audio/aac" -> "m4a"
    "audio/ogg" -> "ogg"
    "audio/opus" -> "opus"
    "audio/flac" -> "flac"
    "audio/wav",
    "audio/x-wav" -> "wav"
    else -> "mp3"
  }

/**
 * Keeps the newest [latest] usable items in feed order. Feeds publish newest first; items without
 * an HTTPS enclosure or a readable duration are skipped rather than failing the whole feed.
 */
fun rssSnapshot(url: String, feed: RssFeed, latest: Int): RssImport {
  require(latest in 1..MAX_FEED_EPISODES) { "Choose 1–$MAX_FEED_EPISODES episodes" }
  var skipped = 0
  val episodes = mutableListOf<Episode>()
  val seen = mutableSetOf<String>()
  for (item in feed.items) {
    if (episodes.size == latest) break
    val episode = runCatching {
      Episode(
          "rss",
          "$url#${item.guid.ifBlank { item.audioUrl }}",
          item.title.ifBlank { "Untitled episode" },
          item.audioUrl,
          rssDurationMs(item.duration),
          revision = "rss",
          audioVersion = digest(item.audioUrl + "|" + item.length),
          extension = rssExtension(item.type),
        )
        .also { it.validate() }
    }
      .getOrNull()
    if (episode == null || !seen.add(episode.key)) skipped++ else episodes += episode
  }
  if (episodes.isEmpty())
    throw PodcastProblem(
      "No downloadable episodes found. Items need an HTTPS audio enclosure and an itunes:duration."
    )
  val snapshot = PlaylistSnapshot(name = feed.name.ifBlank { "RSS podcast" }, episodes = episodes)
  snapshot.validate()
  return RssImport(snapshot, skipped)
}

const val MAX_FEED_EPISODES = 50

/**
 * A supported read-only backend for public downloadable podcast enclosures. RSS has no account
 * positions.
 */
class RssBackend(
  private val platform: PodcastPlatform,
  private val feedUrl: String,
  private val latest: Int,
) : PodcastBackend {
  override val id = "rss"
  override val capabilities =
    BackendCapabilities(true, true, false, "Public RSS enclosures; positions are local to Cadence")

  override suspend fun playlists() = listOf(feedUrl)

  override suspend fun snapshot(playlist: String): PlaylistSnapshot = read(playlist).snapshot

  suspend fun read(playlist: String = feedUrl): RssImport {
    require(playlist == feedUrl)
    return rssSnapshot(feedUrl, platform.rss(feedUrl), latest)
  }

  override suspend fun writePosition(
    episode: Episode,
    positionMs: Long,
    expectedRevision: String,
    operationId: String,
  ): Episode = error("RSS has no position write API")
}

@Serializable
data class DeviceEpisode(
  val backend: String,
  val episodeKey: String,
  val title: String,
  val audioVersion: String,
  val sections: List<Section>,
)

@Serializable data class DeviceManifest(val schema: Int = 1, val episodes: List<DeviceEpisode>)

fun deviceManifest(library: PodcastLibrary): DeviceManifest =
  DeviceManifest(
    episodes =
      library.manifests.map {
        DeviceEpisode(
          it.episode.backend,
          it.episode.key,
          it.episode.title,
          it.episode.audioVersion,
          it.sections,
        )
      }
  )

/**
 * A failure safe to show as-is. [status] is the HTTP status when the server refused the request.
 */
class PodcastProblem(message: String, val status: Int = 0) : Exception(message)

fun validateAudioFileName(name: String) {
  require(
    name.matches(Regex("[a-zA-Z0-9_-]{1,60}-[0-9]{12}-[a-f0-9]{64}\\.(mp3|m4a|ogg|opus|flac|wav)"))
  ) {
    "Invalid podcast filename"
  }
}

fun LocalPodcastState.readyForDevice(): Boolean {
  val sections = library.manifests.flatMap { it.sections }
  return sections.isNotEmpty() && sections.all { it.id in readySectionIds }
}
