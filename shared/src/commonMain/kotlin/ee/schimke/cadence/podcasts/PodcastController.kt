@file:OptIn(kotlin.time.ExperimentalTime::class)

package ee.schimke.cadence.podcasts

import androidx.compose.runtime.Composable
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okio.ByteString.Companion.decodeBase64

interface PodcastPlatform {
  val splitFormats: Set<String>
  val description: String

  fun controller(): PodcastController = PodcastController(this)

  suspend fun readState(): String?

  suspend fun saveState(state: String)

  suspend fun request(
    url: String,
    method: String = "GET",
    headers: Map<String, String> = emptyMap(),
    body: String? = null,
  ): String

  /** Read public metadata without forwarding credentials across HTTPS redirects. */
  suspend fun publicText(url: String): String = request(url)

  /** Fetches and parses a public feed, following HTTPS redirects without credentials. */
  suspend fun rss(url: String): RssFeed

  suspend fun prepare(episode: Episode, sections: List<Section>)

  val supportsOriginalDownloads: Boolean
    get() = false

  suspend fun originalAvailable(episode: Episode): Boolean = false

  /**
   * Downloads [episode]'s original audio unless it is already cached. [onProgress] reports bytes
   * read and the expected total (or -1 when the server doesn't say), possibly from another thread.
   * Failures worth showing are thrown as [PodcastProblem] with a short reason.
   */
  suspend fun downloadOriginal(
    episode: Episode,
    onProgress: (downloaded: Long, total: Long) -> Unit = { _, _ -> },
  ) {
    throw PodcastProblem("Downloading originals is not supported on this platform")
  }

  suspend fun available(section: Section): Boolean

  /**
   * The measured lengths of the original tracks a downloaded episode was joined from (see
   * [Episode.audioParts]), or empty when it wasn't joined or isn't downloaded.
   */
  suspend fun trackLengths(episode: Episode): List<Long> = emptyList()

  /**
   * Chapter starts (ms) marked in a downloaded episode's own audio: ID3v2 `CHAP` frames in an MP3,
   * a Nero `chpl` list in an M4A/M4B. Empty when it has none or isn't downloaded.
   */
  suspend fun chapters(episode: Episode): List<Long> = emptyList()

  /** Remove staged audio for episodes no longer in the imported source. */
  suspend fun pruneAudio(episodes: List<Episode>) {}

  suspend fun export(sections: List<Section>, target: String, manifest: String)

  fun publishStatus(status: String, state: LocalPodcastState) {}

  /**
   * An unexpected failure that was caught and shown as a generic message. Platforms with crash
   * reporting record it as non-fatal; messages are left out, since they can hold signed URLs.
   */
  fun reportFailure(error: Throwable) {}

  suspend fun readDeviceManifest(target: String): DeviceManifest? = null

  /** How to safely disconnect the headphone storage once a copy finishes. */
  val ejectHint: String
    get() = "Eject the volume before unplugging."

  /**
   * False in the browser, where Pocket Casts only accepts API calls from its own site, unless a
   * [pocketCastsServer] makes them.
   */
  val pocketCastsApi: Boolean
    get() = true

  /** Set where a server keeps the Pocket Casts sign-in and makes the calls. */
  val pocketCastsServer: PocketCastsServer?
    get() = null

  /** False in the browser, which can't send the headers Libro.fm's API requires. */
  val libroFmApi: Boolean
    get() = true

  /**
   * False in the browser, whose JS download handler cannot forward custom authentication headers
   * (Audiobookshelf, Jellyfin, Plex).
   */
  val authenticatedDownloads: Boolean
    get() = true
}

@Composable expect fun rememberPodcastPlatform(): PodcastPlatform

@Serializable data class CloudDocument(val revision: Long, val document: PodcastLibrary)

class PodcastController(val platform: PodcastPlatform) {
  val state =
    MutableStateFlow(
      LocalPodcastState(
        library =
          PodcastLibrary(
            config =
              Segmentation(
                splitAtPosition = platform.splitFormats.isNotEmpty(),
                skipListened = platform.splitFormats.isNotEmpty(),
                chunkFormat = if ("mp3" in platform.splitFormats) "mp3" else "m4a",
              ),
          ),
      ),
    )
  val status = MutableStateFlow("Loading library…")
  val busy = MutableStateFlow(false)
  /** How far through the current task, 0 to 1, when it can tell; null while it can't. */
  val progress = MutableStateFlow<Float?>(null)
  val signedIn = MutableStateFlow(false)
  // Set once the saved library, including any Pocket Casts session, has been read.
  val loaded = MutableStateFlow(false)
  private val lock = Mutex()
  private var token = ""
  private var authenticatedUser = ""
  private var authenticatedUrl = ""
  private var gpodderPassword = ""
  val pocketCastsLists = MutableStateFlow<List<PocketCastsList>>(emptyList())
  // Set when the web view could only capture the player's own responses, not its token.
  private var pocketCastsCapture: PocketCastsCapture? = null
  /** The signed-in Libro.fm library, newest purchase first, to pick a book from. */
  val libroFmBooks = MutableStateFlow<List<LibroFmBook>>(emptyList())
  val audiobookshelfLibraries = MutableStateFlow<List<AudiobookshelfLibrary>>(emptyList())
  val subsonicPlaylists = MutableStateFlow<List<SubsonicPlaylist>>(emptyList())
  val subsonicAlbums = MutableStateFlow<List<SubsonicAlbum>>(emptyList())
  val jellyfinPlaylists = MutableStateFlow<List<JellyfinCollection>>(emptyList())
  val jellyfinAlbums = MutableStateFlow<List<JellyfinCollection>>(emptyList())
  val plexPlaylists = MutableStateFlow<List<PlexPlaylist>>(emptyList())
  val plexSections = MutableStateFlow<List<PlexSection>>(emptyList())
  val plexPin = MutableStateFlow<PlexPin?>(null)
  val plexServers = MutableStateFlow<List<PlexServerChoice>>(emptyList())
  val libriVoxResults = MutableStateFlow<List<LibriVoxBook>>(emptyList())
  // A successful action's summary, shown instead of the generic "Saved".
  private var notice: String? = null

  /** Runs [action] and reports it in [status]; returns whether it succeeded. */
  suspend fun run(action: suspend PodcastController.() -> Unit): Boolean = lock.withLock {
    busy.value = true
    try {
      status.value = "Working…"
      progress.value = null
      publish()
      notice = null
      action()
      status.value = notice ?: "Saved"
      true
    } catch (e: CancellationException) {
      status.value = "Cancelled · last saved library retained"
      throw e
    } catch (e: PodcastProblem) {
      status.value = e.message ?: "Operation failed"
      false
    } catch (e: Exception) {
      platform.reportFailure(e)
      // Network exceptions can contain signed enclosure URLs or credentials. Never surface raw
      // errors.
      status.value =
        "Operation failed. Check network, permissions, configuration and supported formats. Previous library is preserved."
      false
    } finally {
      busy.value = false
      progress.value = null
      publish()
    }
  }

  suspend fun load() {
    try {
      platform.readState()?.let { stored ->
        val restored = podcastJson.decodeFromString<LocalPodcastState>(stored)
        restored.library.validate()
        // Settings saved elsewhere, or before a format was supported here, may not apply.
        state.value =
          restored.copy(
            lastCopied =
              restored.lastCopied
                ?: restored.target
                  .takeIf { it.isNotBlank() }
                  ?.let { platform.readDeviceManifest(it) },
            library =
              restored.library.copy(
                config = supported(restored.library.config),
                podcastTrims = supportedTrims(restored.library.podcastTrims),
              ),
          )
      }
      platform.pocketCastsServer?.let { server ->
        // The server's sign-in is the one that counts; another browser may have changed it. If it
        // can't be reached, the last known state stays until a call says otherwise.
        try {
          val email = server.session()
          state.value =
            state.value.copy(
              pocketCasts =
                if (email == null) PocketCastsSession()
                else PocketCastsSession(email = email, server = true),
            )
        } catch (_: Throwable) {
          currentCoroutineContext().ensureActive()
        }
      }
      recheckReady()
      recheckDownloads()
      status.value = "Library loaded"
    } finally {
      // Even an unreadable library counts as loaded, so the home screen can offer to start over.
      loaded.value = true
    }
  }

  private suspend fun save(next: LocalPodcastState) {
    next.library.validate()
    platform.saveState(podcastJson.encodeToString(next))
    state.value = next
    publish()
  }

  private fun publish() {
    platform.publishStatus(
      status.value + " · Last import: " + state.value.library.lastBackendSync,
      state.value,
    )
  }

  /**
   * Replaces the main source's episodes with [text]'s snapshot. [upcoming] and [added] are saved in
   * the same write, so a refresh cancelled during the cache checks below never loses them.
   *
   * A [PodcastLibrary.second] source stays, with its episodes, when [kind] is the other service of
   * the pair; any other import replaces both.
   */
  suspend fun importSnapshot(
    text: String,
    upcoming: List<Episode> = emptyList(),
    added: List<Episode> = emptyList(),
    kind: PodcastSourceKind? = null,
  ) {
    val snapshot = podcastJson.decodeFromString<PlaylistSnapshot>(text)
    snapshot.validate()
    val library = state.value.library
    val second = library.second?.takeIf { kind in PAIRED_SOURCES && kind != it.kind }
    val playlist =
      snapshot.copy(
        episodes =
          snapshot.episodes.filterNot { it.backend == second?.backend } +
            library.playlist?.episodes.orEmpty().filter { it.backend == second?.backend },
      )
    playlist.validate()
    save(
      state.value.copy(
        library =
          library.copy(
            playlist = playlist,
            upcoming = upcoming,
            added = added,
            feedUrl = "",
            manifests = emptyList(),
            second = second,
            lastBackendSync = Clock.System.now().toString(),
          ),
        readySectionIds = emptySet(),
        copiedSectionIds = emptySet(),
      ),
    )
    platform.pruneAudio(playlist.episodes)
    recheckDownloads()
    recheckCachedProcessing()
  }

  private suspend fun recheckCachedProcessing() {
    val library = state.value.library
    val manifests =
      library.playlist?.episodes.orEmpty().mapNotNull { episode ->
        val config = library.configFor(episode)
        val sections =
          runCatching { segment(episode, config) }.getOrNull() ?: return@mapNotNull null
        if (sections.isNotEmpty() && sections.all { platform.available(it) })
          Manifest(episode, config, sections)
        else null
      }
    save(
      state.value.copy(
        library = library.copy(manifests = manifests),
        readySectionIds = manifests.flatMap { it.sections }.map { it.id }.toSet(),
      ),
    )
  }

  private suspend fun recheckDownloads() {
    if (!platform.supportsOriginalDownloads) return
    val downloaded =
      state.value.library.playlist
        ?.episodes
        .orEmpty()
        .filter { platform.originalAvailable(it) }
        .map { it.key }
        .toSet()
    save(state.value.copy(downloadedEpisodeKeys = downloaded))
  }

  /**
   * Downloads every episode not already on the phone. One bad episode doesn't stop the rest: each
   * finished download is kept, and the failures are summarised at the end (as a [PodcastProblem],
   * so a retry only fetches what is still missing).
   */
  suspend fun downloadOriginals() {
    val episodes =
      state.value.library.playlist?.episodes ?: throw PodcastProblem("Connect a source first")
    val missing = episodes.filterNot { platform.originalAvailable(it) }
    if (missing.size < episodes.size) recheckDownloads()
    if (missing.isEmpty()) {
      notice = "All ${count(episodes.size, "episode")} already downloaded"
      return
    }
    val failures = mutableListOf<Pair<Episode, String>>()
    missing.forEachIndexed { index, episode ->
      val label = "Downloading ${index + 1} of ${missing.size}: ${episode.title}"
      status.value = label
      progress.value = index.toFloat() / missing.size
      publish()
      var shownPercent = -1
      var shownAt: TimeMark? = null
      try {
        platform.downloadOriginal(downloadable(episode)) { bytes, total ->
          if (total <= 0) return@downloadOriginal
          // Reported per buffer; the UI and notification get whole-percent steps, at most once a
          // second (plus the first and the last).
          val percent = (bytes * 100 / total).toInt().coerceIn(0, 100)
          val due = shownAt.let { it == null || it.elapsedNow() >= 1.seconds } || percent == 100
          if (percent != shownPercent && due) {
            shownPercent = percent
            shownAt = TimeSource.Monotonic.markNow()
            progress.value = (index + percent / 100f) / missing.size
            status.value = "$label · $percent%"
          }
        }
        save(
          state.value.copy(downloadedEpisodeKeys = state.value.downloadedEpisodeKeys + episode.key),
        )
        recordTracks(episode)
      } catch (e: CancellationException) {
        throw e
      } catch (e: PodcastProblem) {
        failures +=
          episode to
            (e.message?.trimEnd('.')?.replaceFirstChar { it.lowercase() } ?: "download failed")
      } catch (e: Exception) {
        platform.reportFailure(e)
        // Raw messages can carry signed enclosure URLs; never show them.
        failures += episode to "download failed"
      }
    }
    progress.value = 1f
    val done = missing.size - failures.size
    if (failures.isEmpty()) {
      notice = "Downloaded ${count(done, "episode")}"
      return
    }
    val (first, reason) = failures.first()
    val others = if (failures.size > 1) " (and ${failures.size - 1} more)" else ""
    throw PodcastProblem(
      (if (done > 0) "Downloaded $done of ${missing.size}. " else "") +
        "Couldn't download “${first.title}”$others: $reason. Try again to retry the rest.",
    )
  }

  suspend fun importRss(url: String, latest: Int = state.value.library.feedLatest) {
    if (!url.startsWith("https://")) throw PodcastProblem("Enter an HTTPS feed URL")
    val (snapshot, skipped) = RssBackend(platform, url, latest).read()
    // RSS carries no account position; retain this app's position for matching enclosure versions.
    val episodes = keepLocalPositions(snapshot.episodes)
    importSnapshot(podcastJson.encodeToString(snapshot.copy(episodes = episodes)))
    save(
      state.value.copy(
        library =
          state.value.library.copy(
            feedUrl = url,
            feedLatest = latest,
            source = PodcastSourceKind.Rss,
          ),
      ),
    )
    notice =
      "Imported the latest ${count(episodes.size, "episode")} of ${snapshot.name}" +
        if (skipped > 0) " · skipped $skipped without HTTPS audio or duration" else ""
  }

  /** For sources without a position: this app's position for each matching audio version. */
  private fun keepLocalPositions(episodes: List<Episode>): List<Episode> {
    val old = state.value.library
    return episodes.map { episode ->
      val previous =
        old.allEpisodes.find { it.key == episode.key && it.audioVersion == episode.audioVersion }
      if (previous == null) episode
      else
        episode.copy(
          positionMs =
            episodePosition(
              previous,
              old,
              old.manifests.firstOrNull { it.episode.key == previous.key }?.config,
            ),
        )
    }
  }

  /** Detects a Cadence snapshot (JSON) or an Overcast "All data" OPML export. */
  suspend fun importText(text: String, latest: Int = state.value.library.feedLatest) {
    val trimmed = text.trim()
    when {
      trimmed.startsWith("{") -> {
        importSnapshot(trimmed)
        setSource(PodcastSourceKind.Snapshot)
        notice = "Imported ${count(state.value.library.playlist?.episodes?.size ?: 0, "episode")}"
      }
      trimmed.contains("<opml", ignoreCase = true) -> {
        val (snapshot, skipped) =
          resolveProgress(
            platform,
            "overcast",
            "Overcast in progress",
            overcastProgress(trimmed, latest),
          )
        importResolved(snapshot, skipped, "Overcast")
        setSource(PodcastSourceKind.Overcast)
      }
      else ->
        throw PodcastProblem(
          "Paste a Cadence snapshot (JSON) or an Overcast All data export (OPML).",
        )
    }
  }

  private suspend fun importResolved(snapshot: PlaylistSnapshot, skipped: Int, source: String) {
    importSnapshot(podcastJson.encodeToString(snapshot))
    notice =
      "Imported ${count(snapshot.episodes.size, "in-progress episode")} from $source" +
        if (skipped > 0) " · skipped $skipped not found in their feeds or with changed audio"
        else ""
  }

  /** Reads unfinished episodes from a gpodder-compatible server; the password stays in memory. */
  suspend fun importGpodder(
    account: GpodderAccount,
    password: String,
    latest: Int = state.value.library.feedLatest,
  ) {
    account.validate()
    if (password.isBlank()) throw PodcastProblem("Enter the sync password or app password")
    val body = platform.request(account.actionsUrl(), headers = account.headers(password))
    gpodderPassword = password
    save(state.value.copy(gpodder = account))
    val progress = gpodderProgress(gpodderPlays(body), latest)
    if (progress.isEmpty())
      throw PodcastProblem(
        "No unfinished episodes on this account. Play something in your podcast app first.",
      )
    val (snapshot, skipped) = resolveProgress(platform, "gpodder", "gpodder in progress", progress)
    importResolved(
      snapshot,
      skipped,
      account.server.substringAfter("https://").substringBefore('/'),
    )
    setSource(PodcastSourceKind.Gpodder)
  }

  private suspend fun setSource(kind: PodcastSourceKind, list: String = "") {
    save(state.value.copy(library = state.value.library.copy(source = kind, sourceList = list)))
  }

  /** Whether [refreshSource] can run without asking for anything. */
  val canRefresh: Boolean
    get() =
      when (state.value.library.source) {
        PodcastSourceKind.PocketCasts -> state.value.pocketCasts.signedIn
        PodcastSourceKind.Rss -> state.value.library.feedUrl.isNotBlank()
        PodcastSourceKind.Gpodder -> gpodderPassword.isNotBlank()
        PodcastSourceKind.LibroFm -> state.value.libroFm.signedIn
        PodcastSourceKind.Audiobookshelf -> state.value.audiobookshelf.signedIn
        PodcastSourceKind.Subsonic -> state.value.subsonic.signedIn
        PodcastSourceKind.Jellyfin -> state.value.jellyfin.signedIn
        PodcastSourceKind.Plex -> state.value.plex.signedIn
        else -> false
      }

  /** Imports again from wherever the current playlist came from. */
  suspend fun refreshSource() {
    val library = state.value.library
    when (library.source) {
      PodcastSourceKind.PocketCasts -> {
        // First and best effort: new releases still refresh when the import finds the source
        // empty, which is exactly when there is room for them.
        try {
          refreshFresh()
        } catch (e: CancellationException) {
          throw e
        } catch (e: PodcastProblem) {
          if (e.status == 401) throw e
        } catch (_: Exception) {}
        importPocketCasts(library.sourceList, library.feedLatest)
      }
      PodcastSourceKind.Rss -> importRss(library.feedUrl, library.feedLatest)
      PodcastSourceKind.Gpodder ->
        if (gpodderPassword.isBlank())
          throw PodcastProblem("Enter your gpodder password again to refresh")
        else importGpodder(state.value.gpodder, gpodderPassword, library.feedLatest)
      PodcastSourceKind.LibroFm -> importLibroFm(library.sourceList, library.feedLatest)
      PodcastSourceKind.Audiobookshelf ->
        importAudiobookshelf(library.sourceList, library.feedLatest)
      PodcastSourceKind.Subsonic -> importSubsonic(library.sourceList, library.feedLatest)
      PodcastSourceKind.Jellyfin -> importJellyfin(library.sourceList, library.feedLatest)
      PodcastSourceKind.Plex -> importPlex(library.sourceList, library.feedLatest)
      else -> throw PodcastProblem("Import a new export to update these episodes")
    }
    val second = state.value.library.second ?: return
    val first = notice
    importSecond(second.kind, second.list, second.latest)
    notice = listOfNotNull(first, notice).joinToString(" · ")
  }

  /**
   * Adds [kind] beside the main source, or reads it again: the first [latest] of [list] join the
   * synced episodes after the main source's, and the rest wait in [SecondSource.upcoming].
   */
  suspend fun importSecond(
    kind: PodcastSourceKind,
    list: String,
    latest: Int = state.value.library.feedLatest,
  ) {
    val main = state.value.library.source
    if (state.value.library.playlist == null || main !in PAIRED_SOURCES)
      throw PodcastProblem("Add a signed-in service first, then another beside it")
    if (kind !in PAIRED_SOURCES || kind == main)
      throw PodcastProblem("${kind.title} can't be added beside ${main.title}")
    val backend = sourceBackend(kind)
    val name: String
    val all: List<Episode>
    val skipped: Int
    when (kind) {
      PodcastSourceKind.PocketCasts -> {
        val (snapshot, missing) = readPocketCasts(pocketCastsReader(), list, MAX_FEED_EPISODES)
        name = snapshot.name
        all = snapshot.episodes
        skipped = missing
      }
      PodcastSourceKind.LibroFm -> {
        val api = libroFm()
        val books = api.library()
        libroFmBooks.value = books
        val (snapshot, missing) = libroFmSnapshot(books, list)
        name = snapshot.name
        // Libro.fm has no position to read, so this app's own is kept across refreshes.
        all = keepLocalPositions(libroFmFormats(api, snapshot.episodes, latest))
        skipped = missing
      }
      PodcastSourceKind.Audiobookshelf -> {
        val (snapshot, missing) = readAudiobookshelf(list)
        name = snapshot.name
        all = snapshot.episodes
        skipped = missing
      }
      PodcastSourceKind.Subsonic -> {
        val (snapshot, missing) = readSubsonic(list)
        name = snapshot.name
        all = keepLocalPositions(snapshot.episodes)
        skipped = missing
      }
      PodcastSourceKind.Jellyfin -> {
        val (snapshot, missing) = readJellyfin(list)
        name = snapshot.name
        all = snapshot.episodes
        skipped = missing
      }
      PodcastSourceKind.Plex -> {
        val (snapshot, missing) = readPlex(list)
        name = snapshot.name
        all = snapshot.episodes
        skipped = missing
      }
      else -> throw PodcastProblem("${kind.title} can't be added beside ${main.title}")
    }
    val library = state.value.library
    val current = library.playlist ?: throw PodcastProblem("Connect a source first")
    val playlist =
      current.copy(
        episodes = current.episodes.filterNot { it.backend == backend } + all.take(latest),
      )
    playlist.validate()
    save(
      state.value.copy(
        library =
          library.copy(
            playlist = playlist,
            second = SecondSource(kind, list, name, latest, all.drop(latest)),
            manifests =
              library.manifests.filter { manifest ->
                playlist.episodes.any { it.key == manifest.episode.key }
              },
            lastBackendSync = Clock.System.now().toString(),
          ),
      ),
    )
    platform.pruneAudio(playlist.episodes)
    recheckDownloads()
    recheckCachedProcessing()
    val noun = sourceItemNoun(kind)
    val later = all.size - minOf(all.size, latest)
    notice =
      if (all.isEmpty()) "Added $name beside ${main.title}, empty for now"
      else
        "Added ${count(minOf(all.size, latest), noun)} from $name beside ${main.title}" +
          (if (later > 0) " · $later more listed" else "") +
          if (skipped > 0) " · skipped $skipped without a known length" else ""
  }

  /** Forgets the second source's episodes and their audio; the main source stays. */
  suspend fun removeSecondSource() {
    val library = state.value.library
    val second = library.second ?: return
    val playlist =
      library.playlist?.let { playlist ->
        playlist.copy(episodes = playlist.episodes.filterNot { it.backend == second.backend })
      }
    save(
      state.value.copy(
        library =
          library.copy(
            playlist = playlist,
            second = null,
            manifests = library.manifests.filterNot { it.episode.backend == second.backend },
          ),
      ),
    )
    playlist?.let { platform.pruneAudio(it.episodes) }
    recheckDownloads()
    recheckCachedProcessing()
    notice = "Removed ${second.name.ifBlank { second.kind.title }}"
  }

  /** Forgets the imported episodes and prepared sections; sign-ins and settings stay. */
  suspend fun removeSource() {
    save(
      state.value.copy(
        library =
          PodcastLibrary(
            config = state.value.library.config,
            feedLatest = state.value.library.feedLatest,
            podcastTrims = state.value.library.podcastTrims,
          ),
        readySectionIds = emptySet(),
        copiedSectionIds = emptySet(),
        sentPositions = emptyMap(),
      ),
    )
    notice = "Removed podcasts"
  }

  private fun pocketCastsReader(): PocketCastsReader =
    pocketCastsCapture
      ?: platform.pocketCastsServer?.let { server ->
        PocketCastsServerReader(server) {
          save(state.value.copy(pocketCasts = PocketCastsSession()))
          pocketCastsLists.value = emptyList()
        }
      }
      ?: PocketCastsApi(platform, state.value.pocketCasts, ::nowMs) {
        save(state.value.copy(pocketCasts = it))
        if (!it.signedIn) pocketCastsLists.value = emptyList()
      }

  suspend fun signInPocketCasts(email: String, password: String) {
    if (email.isBlank() || password.isBlank())
      throw PodcastProblem("Enter your Pocket Casts email and password")
    val server = platform.pocketCastsServer
    usePocketCasts(
      if (server != null)
        PocketCastsSession(email = server.signIn(email.trim(), password), server = true)
      else PocketCastsApi.signIn(platform, email.trim(), password, nowMs()),
    )
  }

  /** The `auth` cookie the Pocket Casts web player sets after signing in, URL-decoded. */
  suspend fun usePocketCastsCookie(cookie: String) {
    usePocketCasts(pocketCastsSession(cookie, nowMs()))
  }

  private suspend fun usePocketCasts(session: PocketCastsSession) {
    pocketCastsCapture = null
    save(state.value.copy(pocketCasts = session))
    refreshPocketCastsLists()
    notice =
      "Signed in to Pocket Casts" + if (session.email.isNotBlank()) " as ${session.email}" else ""
  }

  /** Falls back to the responses the web player loaded when its token isn't readable. */
  suspend fun usePocketCastsCapture(json: String) {
    val capture = PocketCastsCapture(json)
    if (capture.empty) throw PodcastProblem("Pocket Casts didn't load any playlists yet")
    pocketCastsCapture = capture
    refreshPocketCastsLists()
    if (pocketCastsLists.value.isEmpty())
      throw PodcastProblem("No manual playlists were loaded. Open Playlists in Pocket Casts.")
    notice = "Read ${count(pocketCastsLists.value.size, "list")} from the Pocket Casts web player"
  }

  suspend fun refreshPocketCastsLists() {
    pocketCastsLists.value = readPocketCastsLists(pocketCastsReader())
  }

  /**
   * Reads the whole list (up to [MAX_FEED_EPISODES]): the first [latest] are downloaded and synced,
   * the rest wait in [PodcastLibrary.upcoming] to move up as those are archived.
   */
  suspend fun importPocketCasts(list: String, latest: Int = state.value.library.feedLatest) {
    val previous = state.value.library
    val (snapshot, skipped) = readPocketCasts(pocketCastsReader(), list, MAX_FEED_EPISODES)
    // Additions made here survive a refresh of the same list, until the list itself has them.
    val listed = snapshot.episodes.map { it.key }.toSet()
    val added =
      if (previous.source == PodcastSourceKind.PocketCasts && previous.sourceList == list)
        previous.added.filter { it.key !in listed }
      else emptyList()
    val all = snapshot.episodes + added
    importSnapshot(
      podcastJson.encodeToString(snapshot.copy(episodes = all.take(latest))),
      upcoming = all.drop(latest),
      added = added,
      kind = PodcastSourceKind.PocketCasts,
    )
    save(
      state.value.copy(
        library =
          state.value.library.copy(
            feedLatest = latest,
            source = PodcastSourceKind.PocketCasts,
            sourceList = list,
          ),
      ),
    )
    val later = all.size - minOf(all.size, latest)
    notice =
      if (all.isEmpty())
        "Added ${snapshot.name}, empty in Pocket Casts for now. Refresh once it has episodes."
      else
        "Imported ${count(minOf(all.size, latest), "episode")} from ${snapshot.name}" +
          (if (later > 0) " · $later more listed" else "") +
          if (skipped > 0) " · skipped $skipped without HTTPS audio or a known length" else ""
  }

  private fun libroFm() =
    LibroFmApi(platform, state.value.libroFm) {
      save(state.value.copy(libroFm = LibroFmSession()))
      libroFmBooks.value = emptyList()
    }

  suspend fun signInLibroFm(email: String, password: String) {
    if (email.isBlank() || password.isBlank())
      throw PodcastProblem("Enter your Libro.fm email and password")
    save(state.value.copy(libroFm = LibroFmApi.signIn(platform, email.trim(), password)))
    refreshLibroFmBooks()
    notice = "Signed in to Libro.fm as ${email.trim()}"
  }

  suspend fun refreshLibroFmBooks() {
    libroFmBooks.value = libroFm().library()
  }

  suspend fun signOutLibroFm() {
    libroFmBooks.value = emptyList()
    save(state.value.copy(libroFm = LibroFmSession()))
    notice = "Signed out of Libro.fm"
  }

  /**
   * Imports one book, or the library newest first: the first [latest] are downloaded and synced,
   * the rest wait in [PodcastLibrary.upcoming]. Libro.fm has no position to read, so this app's own
   * position is kept across refreshes.
   */
  suspend fun importLibroFm(list: String, latest: Int = state.value.library.feedLatest) {
    val api = libroFm()
    val books = api.library()
    libroFmBooks.value = books
    val (snapshot, skipped) = libroFmSnapshot(books, list)
    val all = keepLocalPositions(libroFmFormats(api, snapshot.episodes, latest))
    importSnapshot(
      podcastJson.encodeToString(snapshot.copy(episodes = all.take(latest))),
      upcoming = all.drop(latest),
      kind = PodcastSourceKind.LibroFm,
    )
    save(
      state.value.copy(
        library =
          state.value.library.copy(
            feedLatest = latest,
            source = PodcastSourceKind.LibroFm,
            sourceList = list,
          ),
      ),
    )
    val later = all.size - minOf(all.size, latest)
    notice =
      if (all.isEmpty()) "Your Libro.fm library is empty for now. Refresh once you have a book."
      else
        "Imported ${count(minOf(all.size, latest), "audiobook")} from Libro.fm" +
          (if (later > 0) " · $later more listed" else "") +
          if (skipped > 0) " · skipped $skipped without a known length" else ""
  }

  private fun sourceItemNoun(kind: PodcastSourceKind?): String =
    when (kind) {
      PodcastSourceKind.LibroFm -> "audiobook"
      PodcastSourceKind.Subsonic -> "track"
      PodcastSourceKind.Jellyfin -> "item"
      PodcastSourceKind.Audiobookshelf -> "item"
      else -> "episode"
    }

  private fun audiobookshelf() =
    AudiobookshelfApi(platform, state.value.audiobookshelf) {
      save(state.value.copy(audiobookshelf = AudiobookshelfSession()))
      audiobookshelfLibraries.value = emptyList()
    }

  suspend fun signInAudiobookshelf(server: String, username: String, password: String) {
    if (server.isBlank() || username.isBlank() || password.isBlank())
      throw PodcastProblem("Enter your Audiobookshelf server URL, username and password")
    val session = AudiobookshelfApi.signIn(platform, server, username.trim(), password)
    save(state.value.copy(audiobookshelf = session))
    refreshAudiobookshelfLibraries()
    notice = "Signed in to Audiobookshelf as ${session.username.ifBlank { username.trim() }}"
  }

  suspend fun signInAudiobookshelfToken(server: String, token: String) {
    if (server.isBlank() || token.isBlank())
      throw PodcastProblem("Enter your Audiobookshelf server URL and API token")
    val session = AudiobookshelfApi.signInWithToken(platform, server, token)
    save(state.value.copy(audiobookshelf = session))
    refreshAudiobookshelfLibraries()
    notice =
      "Signed in to Audiobookshelf" +
        session.username.takeIf { it.isNotBlank() }?.let { " as $it" }.orEmpty()
  }

  suspend fun refreshAudiobookshelfLibraries() {
    audiobookshelfLibraries.value = audiobookshelf().libraries()
  }

  suspend fun signOutAudiobookshelf() {
    audiobookshelfLibraries.value = emptyList()
    save(state.value.copy(audiobookshelf = AudiobookshelfSession()))
    notice = "Signed out of Audiobookshelf"
  }

  private suspend fun readAudiobookshelf(list: String): Pair<PlaylistSnapshot, Int> {
    val api = audiobookshelf()
    val (_, progressList) = api.me()
    val progressByKey = progressList.associateBy { it.libraryItemId to it.episodeId }
    val libraries = api.libraries()
    audiobookshelfLibraries.value = libraries
    val name: String
    val entries: List<Pair<String, String>>
    if (list == AUDIOBOOKSHELF_IN_PROGRESS || list.isBlank()) {
      name = "Continue listening"
      entries = api.itemsInProgress()
    } else {
      val libId = list.removePrefix(AUDIOBOOKSHELF_LIBRARY_PREFIX)
      name = libraries.firstOrNull { it.id == libId }?.name ?: "Audiobookshelf"
      entries = api.libraryItems(libId, MAX_FEED_EPISODES)
    }
    var skipped = 0
    val episodes = mutableListOf<Episode>()
    for ((itemId, epId) in entries.take(MAX_FEED_EPISODES)) {
      val expanded = api.expandedItem(itemId, epId)
      if (expanded.isEmpty()) {
        skipped++
        continue
      }
      for (item in expanded) {
        val progress = progressByKey[item.id to item.episodeId]
        val ep = audiobookshelfToEpisode(state.value.audiobookshelf.baseUrl, item, progress)
        if (ep == null || episodes.any { it.key == ep.key }) skipped++ else episodes += ep
        if (episodes.size >= MAX_FEED_EPISODES) break
      }
      if (episodes.size >= MAX_FEED_EPISODES) break
    }
    return PlaylistSnapshot(name = name, episodes = episodes) to skipped
  }

  suspend fun importAudiobookshelf(list: String, latest: Int = state.value.library.feedLatest) {
    val (snapshot, skipped) = readAudiobookshelf(list)
    val all = snapshot.episodes
    importSnapshot(
      podcastJson.encodeToString(snapshot.copy(episodes = all.take(latest))),
      upcoming = all.drop(latest),
      kind = PodcastSourceKind.Audiobookshelf,
    )
    save(
      state.value.copy(
        library =
          state.value.library.copy(
            feedLatest = latest,
            source = PodcastSourceKind.Audiobookshelf,
            sourceList = list,
          ),
      ),
    )
    val later = all.size - minOf(all.size, latest)
    notice =
      if (all.isEmpty()) "${snapshot.name} is empty for now. Refresh when something is added."
      else
        "Imported ${count(minOf(all.size, latest), "item")} from ${snapshot.name}" +
          (if (later > 0) " · $later more listed" else "") +
          if (skipped > 0) " · skipped $skipped without supported audio" else ""
  }

  private fun subsonic() =
    SubsonicApi(platform, state.value.subsonic) {
      save(state.value.copy(subsonic = SubsonicSession()))
      subsonicPlaylists.value = emptyList()
      subsonicAlbums.value = emptyList()
    }

  suspend fun signInSubsonic(
    server: String,
    username: String,
    password: String,
    apiKey: String = "",
  ) {
    val session = SubsonicApi.signIn(platform, server, username, password, apiKey)
    save(state.value.copy(subsonic = session))
    refreshSubsonicCatalog()
    notice =
      "Signed in to Subsonic" +
        session.username.takeIf { it.isNotBlank() }?.let { " as $it" }.orEmpty()
  }

  suspend fun refreshSubsonicCatalog() {
    val api = subsonic()
    subsonicPlaylists.value = api.playlists()
    subsonicAlbums.value = api.newestAlbums()
  }

  suspend fun signOutSubsonic() {
    subsonicPlaylists.value = emptyList()
    subsonicAlbums.value = emptyList()
    save(state.value.copy(subsonic = SubsonicSession()))
    notice = "Signed out of Subsonic"
  }

  private suspend fun readSubsonic(list: String): Pair<PlaylistSnapshot, Int> {
    val api = subsonic()
    val (name, songs) =
      when {
        list.startsWith(SUBSONIC_PLAYLIST_PREFIX) ->
          api.playlist(list.removePrefix(SUBSONIC_PLAYLIST_PREFIX))
        list.startsWith(SUBSONIC_ALBUM_PREFIX) ->
          api.album(list.removePrefix(SUBSONIC_ALBUM_PREFIX))
        else -> "Starred songs" to api.starred()
      }
    val server = state.value.subsonic.baseUrl
    val episodes = songs.distinctBy { it.id }.mapNotNull { subsonicToEpisode(server, it) }
    val skipped = songs.distinctBy { it.id }.size - episodes.size
    return PlaylistSnapshot(name = name, episodes = episodes) to skipped
  }

  suspend fun importSubsonic(list: String, latest: Int = state.value.library.feedLatest) {
    val (snapshot, skipped) = readSubsonic(list)
    val all = keepLocalPositions(snapshot.episodes)
    importSnapshot(
      podcastJson.encodeToString(snapshot.copy(episodes = all.take(latest))),
      upcoming = all.drop(latest),
      kind = PodcastSourceKind.Subsonic,
    )
    save(
      state.value.copy(
        library =
          state.value.library.copy(
            feedLatest = latest,
            source = PodcastSourceKind.Subsonic,
            sourceList = list,
          ),
      ),
    )
    val later = all.size - minOf(all.size, latest)
    notice =
      if (all.isEmpty()) "${snapshot.name} is empty for now."
      else
        "Imported ${count(minOf(all.size, latest), "track")} from ${snapshot.name}" +
          (if (later > 0) " · $later more listed" else "") +
          if (skipped > 0) " · skipped $skipped without a known length" else ""
  }

  private fun jellyfin() =
    JellyfinApi(platform, state.value.jellyfin) {
      save(state.value.copy(jellyfin = JellyfinSession()))
      jellyfinPlaylists.value = emptyList()
      jellyfinAlbums.value = emptyList()
    }

  suspend fun signInJellyfin(server: String, username: String, password: String) {
    val session = JellyfinApi.signIn(platform, server, username, password)
    save(state.value.copy(jellyfin = session))
    refreshJellyfinCatalog()
    notice = "Signed in to Jellyfin as ${session.username}"
  }

  suspend fun refreshJellyfinCatalog() {
    val api = jellyfin()
    jellyfinPlaylists.value = api.playlists()
    jellyfinAlbums.value = api.newestAlbums()
  }

  suspend fun signOutJellyfin() {
    jellyfinPlaylists.value = emptyList()
    jellyfinAlbums.value = emptyList()
    save(state.value.copy(jellyfin = JellyfinSession()))
    notice = "Signed out of Jellyfin"
  }

  private suspend fun readJellyfin(list: String): Pair<PlaylistSnapshot, Int> {
    val api = jellyfin()
    val (name, items) =
      when {
        list.startsWith(JELLYFIN_PLAYLIST_PREFIX) -> {
          val id = list.removePrefix(JELLYFIN_PLAYLIST_PREFIX)
          val title =
            jellyfinPlaylists.value.firstOrNull { it.id == id }?.name ?: "Jellyfin playlist"
          title to api.playlistItems(id)
        }
        list.startsWith(JELLYFIN_ALBUM_PREFIX) -> {
          val id = list.removePrefix(JELLYFIN_ALBUM_PREFIX)
          val title = jellyfinAlbums.value.firstOrNull { it.id == id }?.name ?: "Jellyfin album"
          title to api.albumItems(id)
        }
        list == JELLYFIN_FAVORITES -> "Favorites" to api.favorites()
        else -> "Audiobooks" to api.audiobooks()
      }
    val server = state.value.jellyfin.baseUrl
    val episodes = items.distinctBy { it.id }.mapNotNull { jellyfinToEpisode(server, it) }
    val skipped = items.distinctBy { it.id }.size - episodes.size
    return PlaylistSnapshot(name = name, episodes = episodes) to skipped
  }

  suspend fun importJellyfin(list: String, latest: Int = state.value.library.feedLatest) {
    val (snapshot, skipped) = readJellyfin(list)
    val all = snapshot.episodes
    importSnapshot(
      podcastJson.encodeToString(snapshot.copy(episodes = all.take(latest))),
      upcoming = all.drop(latest),
      kind = PodcastSourceKind.Jellyfin,
    )
    save(
      state.value.copy(
        library =
          state.value.library.copy(
            feedLatest = latest,
            source = PodcastSourceKind.Jellyfin,
            sourceList = list,
          ),
      ),
    )
    val later = all.size - minOf(all.size, latest)
    notice =
      if (all.isEmpty()) "${snapshot.name} is empty for now."
      else
        "Imported ${count(minOf(all.size, latest), "item")} from ${snapshot.name}" +
          (if (later > 0) " · $later more listed" else "") +
          if (skipped > 0) " · skipped $skipped without a known length" else ""
  }

  private fun plex() =
    PlexApi(platform, state.value.plex) {
      save(state.value.copy(plex = PlexSession()))
      plexPlaylists.value = emptyList()
      plexSections.value = emptyList()
    }

  suspend fun signInPlexToken(server: String, token: String, serverName: String = "") {
    val session = PlexApi.signInWithToken(platform, server, token, serverName)
    save(state.value.copy(plex = session))
    plexPin.value = null
    plexServers.value = emptyList()
    refreshPlexCatalog()
    notice = "Connected to Plex" + serverName.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
  }

  suspend fun startPlexPin() {
    plexServers.value = emptyList()
    plexPin.value = PlexApi.startPin(platform)
    notice = "Open the Plex sign-in page, then tap Check sign-in"
  }

  suspend fun pollPlexPin() {
    val pin = plexPin.value ?: throw PodcastProblem("Start Plex sign-in first")
    val servers =
      PlexApi.pollPin(platform, pin.id)
        ?: throw PodcastProblem("Waiting for Plex approval. Finish signing in, then try again.")
    if (servers.size == 1) {
      val s = servers.single()
      signInPlexToken(s.url, s.token, s.name)
    } else {
      plexServers.value = servers
      notice = "Choose a Plex server"
    }
  }

  suspend fun refreshPlexCatalog() {
    val api = plex()
    plexPlaylists.value = api.playlists()
    plexSections.value = api.audioSections()
  }

  suspend fun signOutPlex() {
    plexPlaylists.value = emptyList()
    plexSections.value = emptyList()
    plexPin.value = null
    plexServers.value = emptyList()
    save(state.value.copy(plex = PlexSession()))
    notice = "Signed out of Plex"
  }

  private suspend fun readPlex(list: String): Pair<PlaylistSnapshot, Int> {
    val api = plex()
    val (name, tracks) =
      when {
        list.startsWith(PLEX_PLAYLIST_PREFIX) ->
          api.playlistItems(list.removePrefix(PLEX_PLAYLIST_PREFIX))
        list.startsWith(PLEX_SECTION_PREFIX) ->
          api.sectionTracks(list.removePrefix(PLEX_SECTION_PREFIX), MAX_FEED_EPISODES)
        else -> {
          val firstSection =
            plexSections.value.firstOrNull()
              ?: api.audioSections().firstOrNull()
              ?: throw PodcastProblem("No music or audiobook libraries found on this Plex server")
          api.sectionTracks(firstSection.key, MAX_FEED_EPISODES)
        }
      }
    val server = state.value.plex.baseUrl
    val episodes = tracks.distinctBy { it.ratingKey }.mapNotNull { plexToEpisode(server, it) }
    val skipped = tracks.distinctBy { it.ratingKey }.size - episodes.size
    return PlaylistSnapshot(name = name, episodes = episodes) to skipped
  }

  suspend fun importPlex(list: String, latest: Int = state.value.library.feedLatest) {
    val (snapshot, skipped) = readPlex(list)
    val all = snapshot.episodes
    importSnapshot(
      podcastJson.encodeToString(snapshot.copy(episodes = all.take(latest))),
      upcoming = all.drop(latest),
      kind = PodcastSourceKind.Plex,
    )
    save(
      state.value.copy(
        library =
          state.value.library.copy(
            feedLatest = latest,
            source = PodcastSourceKind.Plex,
            sourceList = list,
          ),
      ),
    )
    val later = all.size - minOf(all.size, latest)
    notice =
      if (all.isEmpty()) "${snapshot.name} is empty for now."
      else
        "Imported ${count(minOf(all.size, latest), "item")} from ${snapshot.name}" +
          (if (later > 0) " · $later more listed" else "") +
          if (skipped > 0) " · skipped $skipped without supported audio" else ""
  }

  suspend fun searchLibriVoxBooks(query: String) {
    libriVoxResults.value = searchLibriVox(platform, query)
    notice =
      if (libriVoxResults.value.isEmpty()) "No LibriVox audiobooks matched"
      else "Found ${count(libriVoxResults.value.size, "audiobook")} on LibriVox"
  }

  /**
   * [episode] with audio it can be downloaded from now. Libro.fm's links expire within minutes, so
   * a book not yet on this device gets a fresh one; authenticated self-hosted servers attach their
   * credentials only for the download request, never persisting them in the library.
   */
  private fun requireMatchingServerOrigin(
    episode: Episode,
    serverBaseUrl: String,
    service: String,
  ) {
    val urls = listOf(episode.audioUrl) + episode.audioParts
    if (serverBaseUrl.isBlank() || urls.any { !sameHttpsOrigin(it, serverBaseUrl) })
      throw PodcastProblem(
        "This item's audio URL doesn't match your signed-in $service server. Refresh $service before downloading.",
      )
  }

  private suspend fun downloadable(episode: Episode): Episode {
    if (platform.originalAvailable(episode)) return episode
    return when (episode.backend) {
      LIBRO_FM_BACKEND ->
        if (episode.extension == "mp3")
          libroFm().mp3Parts(episode.id).let {
            episode.copy(audioUrl = it.first(), audioParts = it)
          }
        else episode.copy(audioUrl = libroFm().m4bUrl(episode.id))
      AUDIOBOOKSHELF_BACKEND -> {
        val api = audiobookshelf()
        requireMatchingServerOrigin(episode, state.value.audiobookshelf.baseUrl, "Audiobookshelf")
        episode.copy(downloadHeaders = api.authHeaders())
      }
      SUBSONIC_BACKEND -> {
        val api = subsonic()
        requireMatchingServerOrigin(episode, state.value.subsonic.baseUrl, "Subsonic")
        episode.copy(audioUrl = api.downloadUrl(episode.id))
      }
      JELLYFIN_BACKEND -> {
        val api = jellyfin()
        requireMatchingServerOrigin(episode, state.value.jellyfin.baseUrl, "Jellyfin")
        episode.copy(downloadHeaders = api.authHeaders())
      }
      PLEX_BACKEND -> {
        val api = plex()
        requireMatchingServerOrigin(episode, state.value.plex.baseUrl, "Plex")
        episode.copy(downloadHeaders = api.authHeaders())
      }
      else -> episode
    }
  }

  /**
   * Finds chapters for each episode set to split by chapter that doesn't have them yet: a joined
   * book's original tracks, else the feed's chapter index, else the chapter marks in the downloaded
   * audio. An episode without any is left whole.
   */
  private suspend fun readChapters() {
    val library = state.value.library
    library.playlist?.episodes.orEmpty().forEach { episode ->
      val config = library.configFor(episode)
      if (config.splitMode != SplitMode.Chapters) return@forEach
      // A finished episode isn't prepared at all, so its chapters aren't worth a download.
      if (segment(episode, config.copy(split = SplitMode.Whole)).isEmpty()) return@forEach
      when {
        episode.backend == LIBRO_FM_BACKEND && episode.extension == "mp3" ->
          if (episode.trackMs.isEmpty()) {
            if (!platform.originalAvailable(episode))
              platform.downloadOriginal(downloadable(episode))
            recordTracks(episode)
          }
        episode.chapterMs.isEmpty() -> {
          val listed =
            episode.chaptersUrl
              .takeIf { it.isNotBlank() }
              ?.let { url ->
                try {
                  podcastChapters(platform.publicText(url), episode.durationMs)
                } catch (e: CancellationException) {
                  throw e
                } catch (_: Exception) {
                  // An index that can't be read just means no chapters from it.
                  emptyList()
                }
              }
              .orEmpty()
          val starts = listed.ifEmpty {
            if (!platform.supportsOriginalDownloads) return@ifEmpty emptyList()
            if (!platform.originalAvailable(episode))
              platform.downloadOriginal(downloadable(episode))
            chapterStarts(platform.chapters(episode), episode.durationMs)
          }
          if (starts.isNotEmpty()) updateEpisode(episode) { it.copy(chapterMs = starts) }
        }
      }
    }
  }

  /** Saves the measured lengths of the original tracks [episode] was joined from, once known. */
  private suspend fun recordTracks(episode: Episode) {
    val lengths = platform.trackLengths(episode)
    if (lengths.isEmpty() || lengths == episode.trackMs) return
    updateEpisode(episode) { it.copy(trackMs = lengths) }
  }

  /** Applies [change] to [episode] wherever the library lists it. */
  private suspend fun updateEpisode(episode: Episode, change: (Episode) -> Episode) {
    fun List<Episode>.changed() = map {
      if (it.key == episode.key && it.audioVersion == episode.audioVersion) change(it) else it
    }
    val library = state.value.library
    save(
      state.value.copy(
        library =
          library.copy(
            playlist = library.playlist?.let { it.copy(episodes = it.episodes.changed()) },
            upcoming = library.upcoming.changed(),
          ),
      ),
    )
  }

  /**
   * Each book's download: the packaged M4B where Libro.fm has one, else its MP3 tracks joined into
   * one file. Only the first [latest] are asked about, since only they are downloaded; a book seen
   * before keeps the format it had, so its download and position stay valid.
   */
  private suspend fun libroFmFormats(
    api: LibroFmApi,
    episodes: List<Episode>,
    latest: Int,
  ): List<Episode> {
    // Only the synced playlist was asked about; books waiting further down were not.
    val known = state.value.library.playlist?.episodes.orEmpty().associateBy { it.key }
    return episodes.mapIndexed { index, episode ->
      val previous = known[episode.key]
      when {
        previous != null &&
          previous.durationMs == episode.durationMs &&
          previous.extension == "mp3" -> episode.asLibroFmMp3().copy(trackMs = previous.trackMs)
        // An M4B already on this phone stays; one not downloaded yet is asked about again, which
        // also moves books imported before the MP3 fallback (all assumed M4B) to MP3 when needed.
        previous != null &&
          previous.durationMs == episode.durationMs &&
          platform.originalAvailable(previous) -> episode
        index < latest && api.m4bUrlOrNull(episode.id) == null -> episode.asLibroFmMp3()
        else -> episode
      }
    }
  }

  /** Writes need the token on this device; the web-player capture and servers only read. */
  private val pocketCastsWritable: Boolean
    get() =
      pocketCastsCapture == null &&
        !state.value.pocketCasts.server &&
        state.value.pocketCasts.signedIn

  /** Whether an episode can be archived in its source from here. */
  val canArchive: Boolean
    get() =
      PodcastSourceKind.PocketCasts.let {
        state.value.library.source == it || state.value.library.second?.kind == it
      } && pocketCastsWritable

  /** Whether [episode] can be archived: a Pocket Casts one, when [canArchive]. */
  fun canArchive(episode: Episode): Boolean =
    canArchive && episode.backend == sourceBackend(PodcastSourceKind.PocketCasts)

  /**
   * Whether a fresh episode can be added to the source. Up Next takes it in Pocket Casts too;
   * another list only gets it on this phone, since Pocket Casts playlists can't be edited from
   * here.
   */
  val canAddFresh: Boolean
    get() = canArchive && state.value.library.source == PodcastSourceKind.PocketCasts

  /** Whether this source offers fresh episodes at all. */
  val supportsFresh: Boolean
    get() = canAddFresh

  private fun pocketCastsWriter() =
    PocketCastsApi(platform, state.value.pocketCasts, ::nowMs) { session ->
      save(state.value.copy(pocketCasts = session))
    }

  private suspend fun podcastUuid(api: PocketCastsApi, episode: Episode): String =
    episode.pocketCastsPodcast
      .ifBlank {
        pocketCastsEpisodes(api.episode(episode.id)).singleOrNull()?.podcastUuid.orEmpty()
      }
      .ifBlank { throw PodcastProblem("Pocket Casts didn't return the episode's podcast ID") }

  /**
   * Done with an episode: marks it played in Pocket Casts, takes it out of Up Next when that is the
   * source, archives it, and drops it from this library so it isn't copied again.
   */
  suspend fun archiveEpisode(episodeKey: String) {
    if (!canArchive) throw PodcastProblem("Sign in to Pocket Casts in Cadence to archive episodes")
    val library = state.value.library
    val playlist = library.playlist ?: throw PodcastProblem("Connect a source first")
    val episode =
      library.allEpisodes.firstOrNull { it.key == episodeKey }
        ?: throw PodcastProblem("Episode no longer available")
    if (!canArchive(episode)) throw PodcastProblem("Only Pocket Casts episodes can be archived")
    val fromSecond = library.fromSecond(episode)
    val list = if (fromSecond) library.second?.list else library.sourceList
    val api = pocketCastsWriter()
    val podcast = podcastUuid(api, episode)
    api.sendPosition(episode.id, podcast, episode.durationMs, episode.durationMs)
    if (list == POCKET_CASTS_UP_NEXT) api.upNext(UP_NEXT_REMOVE, episode, podcast)
    // Archiving is a web-player call; played and out of Up Next is already the important part.
    val archived =
      try {
        api.archive(episode.id, podcast)
        true
      } catch (e: CancellationException) {
        throw e
      } catch (e: PodcastProblem) {
        if (e.status == 401) throw e
        false
      } catch (_: Exception) {
        // Timeouts and server errors surface as other exceptions on Android and desktop.
        false
      }
    val current = state.value.library
    val kept = (current.playlist ?: playlist).episodes.filterNot { it.key == episodeKey }
    // The next one in its source's list moves up into the synced episodes without another
    // refresh. Each source counts only its own toward its limit.
    val (main, beside) = kept.partition { !current.fromSecond(it) }
    val second = current.second
    val next =
      if (fromSecond && second != null) {
        val waiting = second.upcoming.filterNot { it.key == episodeKey }
        val promoted = waiting.take((second.latest - beside.size).coerceAtLeast(0))
        current.copy(
          playlist = (current.playlist ?: playlist).copy(episodes = main + beside + promoted),
          second = second.copy(upcoming = waiting.drop(promoted.size)),
        )
      } else {
        val waiting = current.upcoming.filterNot { it.key == episodeKey }
        val promoted = waiting.take((current.feedLatest - main.size).coerceAtLeast(0))
        current.copy(
          playlist = (current.playlist ?: playlist).copy(episodes = main + promoted + beside),
          upcoming = waiting.drop(promoted.size),
        )
      }
    val promoted = next.playlist!!.episodes.size - kept.size
    save(
      state.value.copy(
        library =
          next.copy(
            added = current.added.filterNot { it.key == episodeKey },
            manifests = current.manifests.filterNot { it.episode.key == episodeKey },
          ),
      ),
    )
    if (promoted > 0) {
      recheckDownloads()
      recheckCachedProcessing()
    }
    notice =
      if (archived) "Archived ${episode.title}"
      else "Marked ${episode.title} played in Pocket Casts"
  }

  /** Reads Pocket Casts New Releases into [LocalPodcastState.fresh]. */
  suspend fun refreshFresh() {
    if (!supportsFresh || !state.value.showFresh) return
    val inSource = state.value.library.allEpisodes.map { it.key }.toSet()
    val dismissed = state.value.dismissedFresh
    val body = pocketCastsWriter().newReleases()
    // Dismissed episodes are left out before the limit, so later releases move up into view.
    val fresh = pocketCastsFresh(body, inSource + dismissed)
    // Dismissals only need remembering while Pocket Casts still lists the episode.
    val listed = pocketCastsFresh(body, emptySet(), limit = Int.MAX_VALUE).map { it.key }.toSet()
    save(state.value.copy(fresh = fresh, dismissedFresh = dismissed.intersect(listed)))
  }

  /**
   * Adds a fresh episode to the end of the source: Up Next in Pocket Casts as well, any other list
   * only on this phone. It joins the synced episodes when there's room, else waits in upcoming.
   */
  suspend fun addFresh(episodeKey: String) {
    if (!canAddFresh) throw PodcastProblem("Sign in to Pocket Casts in Cadence to add episodes")
    val episode =
      state.value.fresh.firstOrNull { it.key == episodeKey }
        ?: throw PodcastProblem("Episode no longer available")
    val upNext = state.value.library.sourceList == POCKET_CASTS_UP_NEXT
    if (upNext) {
      val api = pocketCastsWriter()
      api.upNext(UP_NEXT_PLAY_LAST, episode, podcastUuid(api, episode))
    }
    val current = state.value
    val library = current.library
    val playlist = library.playlist ?: throw PodcastProblem("Connect a source first")
    val present = library.allEpisodes.any { it.key == episodeKey }
    val (main, beside) = playlist.episodes.partition { !library.fromSecond(it) }
    val room = library.upcoming.isEmpty() && main.size < library.feedLatest
    save(
      current.copy(
        library =
          library.copy(
            playlist =
              if (present || !room) playlist else playlist.copy(episodes = main + episode + beside),
            upcoming = if (present || room) library.upcoming else library.upcoming + episode,
            added = if (upNext || present) library.added else library.added + episode,
          ),
        fresh = current.fresh.filterNot { it.key == episodeKey },
      ),
    )
    notice =
      if (upNext) "Added ${episode.title} to Up Next"
      else "Added ${episode.title} to ${playlist.name} on this phone"
  }

  /** Hides a fresh episode here only; Pocket Casts is unchanged. */
  suspend fun dismissFresh(episodeKey: String) {
    save(state.value.copy(dismissedFresh = state.value.dismissedFresh + episodeKey))
    notice = "Dismissed"
  }

  suspend fun setShowFresh(show: Boolean) {
    save(state.value.copy(showFresh = show, fresh = if (show) state.value.fresh else emptyList()))
  }

  suspend fun signOutPocketCasts() {
    platform.pocketCastsServer?.signOut()
    pocketCastsCapture = null
    pocketCastsLists.value = emptyList()
    save(state.value.copy(pocketCasts = PocketCastsSession()))
    notice = "Signed out of Pocket Casts"
  }

  /**
   * Sends the locally advanced position back to the gpodder account. gpodder has no conditional
   * write, so this re-reads the episode first and refuses if another app moved it since import.
   */
  suspend fun sendPosition(episodeKey: String) {
    val manifest =
      state.value.library.manifests.firstOrNull { it.episode.key == episodeKey }
        ?: throw PodcastProblem("Prepare the episode first")
    val episode = manifest.episode
    if (episode.backend == "pocket-casts") {
      sendPocketCastsPosition(episode)
      return
    }
    if (episode.backend == AUDIOBOOKSHELF_BACKEND) {
      sendAudiobookshelfPosition(episode)
      return
    }
    if (episode.backend == PLEX_BACKEND) {
      sendPlexPosition(episode)
      return
    }
    if (episode.backend != "gpodder")
      throw PodcastProblem("This source does not accept position updates")
    val account = state.value.gpodder
    if (gpodderPassword.isBlank()) throw PodcastProblem("Import from gpodder again to sign in")
    val position =
      episodePosition(
        episode,
        state.value.library,
        state.value.library.manifests.firstOrNull { it.episode.key == episode.key }?.config,
      )
    if (position <= episode.positionMs) throw PodcastProblem("Set a later episode position first")
    val (podcast, audio) = episode.id.split(' ', limit = 2)
    val current =
      gpodderPlays(
          platform.request(account.actionsUrl(), headers = account.headers(gpodderPassword)),
        )
        .firstOrNull { it.podcast == podcast && it.episode == audio }
    val unchanged = current?.let { "gpodder:${it.timestamp}:${it.position}" } == episode.revision
    // A later send from this device is also expected state.
    val ownSend =
      state.value.sentPositions[episodeKey]?.let { current?.position == it / 1000 } == true
    if (!unchanged && !ownSend)
      throw PodcastProblem(
        "This episode moved in another app since import. Import again before sending a position.",
      )
    platform.request(
      account.uploadUrl(),
      "POST",
      account.headers(gpodderPassword),
      gpodderUpload(episode, position, gpodderTimestamp(Clock.System.now().toString())),
    )
    save(state.value.copy(sentPositions = state.value.sentPositions + (episodeKey to position)))
    notice =
      "Sent ${formatTime(position)} to ${account.server.substringAfter("https://").substringBefore('/')}"
  }

  private suspend fun sendPocketCastsPosition(episode: Episode) {
    if (
      pocketCastsCapture != null ||
        state.value.pocketCasts.server ||
        !state.value.pocketCasts.signedIn
    )
      throw PodcastProblem("Sign in to Pocket Casts in Cadence before sending a position")
    val position =
      episodePosition(
        episode,
        state.value.library,
        state.value.library.manifests.firstOrNull { it.episode.key == episode.key }?.config,
      )
    if (position <= episode.positionMs) throw PodcastProblem("Set a later episode position first")
    val api =
      PocketCastsApi(platform, state.value.pocketCasts, ::nowMs) { session ->
        save(state.value.copy(pocketCasts = session))
      }
    val remote =
      pocketCastsEpisodes(api.episode(episode.id)).singleOrNull()
        ?: throw PodcastProblem("Pocket Casts didn't return the episode")
    if (remote.uuid != episode.id || remote.podcastUuid.isBlank())
      throw PodcastProblem("Pocket Casts didn't return the episode's podcast ID")
    val remoteMs = (remote.playedUpToSeconds * 1000).toLong()
    val previousSend = state.value.sentPositions[episode.key]?.let { it / 1000 * 1000 }
    if (remoteMs != episode.positionMs && remoteMs != previousSend)
      throw PodcastProblem(
        "This episode moved in Pocket Casts since import. Refresh before sending a position.",
      )
    if (remote.playingStatus == 3 && position < episode.durationMs)
      throw PodcastProblem(
        "This episode is already marked played in Pocket Casts. Refresh before sending a position.",
      )
    api.sendPosition(episode.id, remote.podcastUuid, position, episode.durationMs)
    save(state.value.copy(sentPositions = state.value.sentPositions + (episode.key to position)))
    notice = "Sent ${formatTime(position)} to Pocket Casts"
  }

  private suspend fun sendAudiobookshelfPosition(episode: Episode) {
    if (!state.value.audiobookshelf.signedIn)
      throw PodcastProblem("Sign in to Audiobookshelf before sending a position")
    val position =
      episodePosition(
        episode,
        state.value.library,
        state.value.library.manifests.firstOrNull { it.episode.key == episode.key }?.config,
      )
    if (position <= episode.positionMs) throw PodcastProblem("Set a later episode position first")
    val parts = episode.id.split('/', limit = 2)
    val itemId = parts[0]
    val epId = parts.getOrElse(1) { "" }
    val api = audiobookshelf()
    val remote = api.progress(itemId, epId)
    val remoteMs =
      when {
        remote == null -> 0L
        remote.isFinished -> episode.durationMs
        else -> (remote.currentTimeSeconds * 1000).toLong().coerceIn(0, episode.durationMs)
      }
    val importedSec = episode.positionMs / 1000
    val remoteSec = remoteMs / 1000
    val previousSendSec = state.value.sentPositions[episode.key]?.let { it / 1000 }
    if (remoteSec != importedSec && remoteSec != previousSendSec)
      throw PodcastProblem(
        "This item moved in Audiobookshelf since import. Refresh before sending a position.",
      )
    if (remote?.isFinished == true && position < episode.durationMs)
      throw PodcastProblem(
        "This item is already marked finished in Audiobookshelf. Refresh before sending a position.",
      )
    api.sendPosition(itemId, epId, position, episode.durationMs)
    save(state.value.copy(sentPositions = state.value.sentPositions + (episode.key to position)))
    notice = "Sent ${formatTime(position)} to Audiobookshelf"
  }

  private suspend fun sendPlexPosition(episode: Episode) {
    if (!state.value.plex.signedIn)
      throw PodcastProblem("Sign in to Plex before sending a position")
    val position =
      episodePosition(
        episode,
        state.value.library,
        state.value.library.manifests.firstOrNull { it.episode.key == episode.key }?.config,
      )
    if (position <= episode.positionMs) throw PodcastProblem("Set a later episode position first")
    val api = plex()
    val remote = api.track(episode.id) ?: throw PodcastProblem("Plex didn't return this item")
    val importedSec = episode.positionMs / 1000
    val remoteSec = remote.viewOffsetMs / 1000
    val previousSendSec = state.value.sentPositions[episode.key]?.let { it / 1000 }
    if (remoteSec != importedSec && remoteSec != previousSendSec)
      throw PodcastProblem(
        "This item moved in Plex since import. Refresh before sending a position.",
      )
    api.sendPosition(episode.id, position, episode.durationMs)
    save(state.value.copy(sentPositions = state.value.sentPositions + (episode.key to position)))
    notice = "Sent ${formatTime(position)} to Plex"
  }

  /**
   * What this platform can prepare: the browser copies whole episodes only, and Android encodes
   * only M4A, so settings it can't honour are switched rather than failing at download time.
   */
  fun supported(config: Segmentation): Segmentation {
    val formats = platform.splitFormats
    return when {
      formats.isEmpty() ->
        config.copy(
          splitAtPosition = false,
          skipListened = false,
          chunkMinutes = 0,
          split = SplitMode.Whole,
        )
      config.chunkFormat !in formats -> config.copy(chunkFormat = formats.sorted().first())
      else -> config
    }
  }

  /**
   * Per-podcast splits this platform can't make, set elsewhere (Android or desktop), are dropped
   * here like the library's own; the browser copies whole episodes and has no per-podcast settings
   * to clear them from.
   */
  fun supportedTrims(trims: Map<String, PodcastTrim>): Map<String, PodcastTrim> =
    if (platform.splitFormats.isNotEmpty()) trims
    else trims.mapValues { (_, trim) -> trim.copy(split = null) }

  suspend fun configure(config: Segmentation) {
    config.validate()
    save(
      state.value.copy(
        library = state.value.library.copy(config = supported(config), manifests = emptyList()),
        readySectionIds = emptySet(),
        copiedSectionIds = emptySet(),
      ),
    )
  }

  suspend fun configurePodcastTrim(id: String, trim: PodcastTrim) {
    require(id.isNotBlank() && trim.startSeconds in 0..3600 && trim.endSeconds in 0..3600)
    val library = state.value.library
    val affected = library.playlist?.episodes.orEmpty().filter { it.podcastId == id }
    require(affected.isNotEmpty())
    if (affected.any { (trim.startSeconds + trim.endSeconds) * 1000L >= it.durationMs })
      throw PodcastProblem("Trim must be shorter than every episode in this podcast")
    val trims =
      if (trim == PodcastTrim()) library.podcastTrims - id else library.podcastTrims + (id to trim)
    save(
      state.value.copy(
        library = library.copy(podcastTrims = trims, manifests = emptyList()),
        readySectionIds = emptySet(),
        copiedSectionIds = emptySet(),
      ),
    )
  }

  suspend fun prepare() {
    readChapters()
    val library = state.value.library
    val playlist = requireNotNull(library.playlist) { "Import a playlist first" }
    // An empty source (a Pocket Casts playlist with nothing in it yet) has nothing to prepare;
    // it isn't a failure, so the overnight refresh doesn't keep retrying it.
    if (playlist.episodes.isEmpty()) {
      save(
        state.value.copy(
          library = state.value.library.copy(manifests = emptyList()),
          readySectionIds = emptySet(),
        ),
      )
      notice = "${playlist.name} has no episodes yet"
      return
    }
    val manifests =
      playlist.episodes
        .map { episode ->
          val config = library.configFor(episode)
          if ((config.trimStartSeconds + config.trimEndSeconds) * 1000L >= episode.durationMs)
            throw PodcastProblem(
              "Trim is longer than ${episode.title}. Reduce this podcast's trim before preparing.",
            )
          Manifest(episode, config, segment(episode, config))
        }
        .filter { it.sections.isNotEmpty() }
    if (manifests.isEmpty()) throw PodcastProblem("No unheard audio remains after trimming")
    if (
      manifests.any { m ->
        (m.sections.size > 1 || m.sections.first().startMs > 0) &&
          m.config.chunkFormat !in platform.splitFormats
      }
    )
      throw PodcastProblem(
        "Choose a supported chunk format: Android uses M4A, desktop supports MP3/M4A, web requires unsplit audio.",
      )
    // Publish the manifest and readiness only after every audio file is committed.
    manifests.forEachIndexed { index, manifest ->
      status.value = "Preparing ${index + 1} of ${manifests.size}: ${manifest.episode.title}"
      publish()
      platform.prepare(downloadable(manifest.episode), manifest.sections)
      val current = state.value
      save(
        current.copy(
          library =
            current.library.copy(
              manifests =
                current.library.manifests.filterNot { it.episode.key == manifest.episode.key } +
                  manifest,
            ),
          readySectionIds = current.readySectionIds + manifest.sections.map { it.id },
          downloadedEpisodeKeys =
            if (platform.originalAvailable(manifest.episode))
              current.downloadedEpisodeKeys + manifest.episode.key
            else current.downloadedEpisodeKeys,
        ),
      )
    }
    val sections = manifests.flatMap { it.sections }
    require(sections.all { platform.available(it) })
    notice = "Ready: ${count(manifests.size, "episode")} in ${count(sections.size, "file")}"
    save(
      state.value.copy(
        library = library.copy(manifests = manifests),
        readySectionIds = sections.map { it.id }.toSet(),
        copiedSectionIds = emptySet(),
      ),
    )
  }

  suspend fun markPlayed(section: Section) {
    require(state.value.library.manifests.any { section in it.sections })
    val completion =
      Completion(
        section.id,
        state.value.library.manifests.first { section in it.sections }.episode.revision,
        section.episodeKey,
        section.audioVersion,
        section.startMs,
        section.endMs,
      )
    save(
      state.value.copy(
        library =
          state.value.library.copy(
            completions =
              (state.value.library.completions + completion).distinctBy {
                it.sectionId to it.observedRevision
              },
          ),
      ),
    )
    // Provider writes are deliberately capability gated. A local acknowledgement never claims
    // success remotely.
  }

  suspend fun setEpisodePosition(episodeKey: String, positionMs: Long) {
    val library = state.value.library
    val episode =
      library.playlist?.episodes?.firstOrNull { it.key == episodeKey }
        ?: throw PodcastProblem("Episode no longer available")
    require(positionMs in 0..episode.durationMs)
    val progress = EpisodeProgress(episode.audioVersion, episode.revision, positionMs, nowMs())
    save(
      state.value.copy(
        library =
          library.copy(episodeProgress = library.episodeProgress + (episodeKey to progress)),
      ),
    )
    notice = "Position set to ${formatTime(positionMs)}"
  }

  suspend fun setTarget(target: String) {
    save(
      state.value.copy(
        target = target,
        copiedSectionIds = emptySet(),
        lastCopied = if (target == state.value.target) state.value.lastCopied else null,
      ),
    )
  }

  suspend fun copyToDevice() {
    recheckReady()
    val sections = state.value.library.manifests.flatMap { it.sections }
    if (!state.value.readyForDevice())
      throw PodcastProblem("Prepare all audio on this device before copying to headphones.")
    require(state.value.target.isNotBlank())
    platform.export(
      sections,
      state.value.target,
      podcastJson.encodeToString(deviceManifest(state.value.library)),
    )
    save(
      state.value.copy(
        copiedSectionIds = sections.map { it.id }.toSet(),
        lastCopied = deviceManifest(state.value.library),
      ),
    )
    notice = "Copied ${count(sections.size, "file")} to the headphones. ${platform.ejectHint}"
  }

  private suspend fun recheckReady() {
    val available =
      state.value.library.manifests
        .flatMap { it.sections }
        .filter { platform.available(it) }
        .map { it.id }
        .toSet()
    save(state.value.copy(readySectionIds = available))
  }

  suspend fun configureCloud(url: String, key: String) {
    require(
      url.startsWith("https://") && !url.trimEnd('/').substringAfter("https://").contains('/'),
    )
    require(key.isNotBlank())
    val jwtRole =
      key
        .split('.')
        .getOrNull(1)
        ?.replace('-', '+')
        ?.replace('_', '/')
        ?.decodeBase64()
        ?.utf8()
        ?.let {
          runCatching { Json.parseToJsonElement(it).jsonObject["role"]?.jsonPrimitive?.content }
            .getOrNull()
        }
    if (key.startsWith("sb_secret_") || jwtRole == "service_role")
      throw PodcastProblem(
        "Use a publishable/anon key. Service-role and secret keys must never be installed in the app.",
      )
    signOut()
    save(
      state.value.copy(
        cloudUrl = url.trimEnd('/'),
        cloudKey = key,
        cloudRevision = 0,
        cloudUserId = "",
      ),
    )
  }

  suspend fun signIn(email: String, password: String) {
    signOut()
    val local = state.value
    val body = buildJsonObject {
      put("email", email)
      put("password", password)
    }
      .toString()
    val response =
      Json.parseToJsonElement(
          platform.request(
            local.cloudUrl + "/auth/v1/token?grant_type=password",
            "POST",
            mapOf("apikey" to local.cloudKey, "Content-Type" to "application/json"),
            body,
          ),
        )
        .jsonObject
    token = response.getValue("access_token").jsonPrimitive.content
    authenticatedUser = response.getValue("user").jsonObject.getValue("id").jsonPrimitive.content
    authenticatedUrl = local.cloudUrl
    // A new account must pull before writing over an existing account document.
    if (local.cloudUserId != authenticatedUser)
      save(local.copy(cloudUserId = authenticatedUser, cloudRevision = 0))
    signedIn.value = true
  }

  fun forgetGpodderPassword() {
    gpodderPassword = ""
  }

  fun signOut() {
    token = ""
    authenticatedUser = ""
    authenticatedUrl = ""
    signedIn.value = false
    status.value = "Signed out · local library retained"
  }

  private suspend fun rpc(name: String, body: String = "{}"): String {
    val local = state.value
    require(
      signedIn.value &&
        token.isNotBlank() &&
        authenticatedUser == local.cloudUserId &&
        authenticatedUrl == local.cloudUrl,
    )
    return platform.request(
      local.cloudUrl + "/rest/v1/rpc/" + name,
      "POST",
      mapOf(
        "apikey" to local.cloudKey,
        "Authorization" to "Bearer $token",
        "Content-Type" to "application/json",
      ),
      body,
    )
  }

  suspend fun pushCloud() {
    val local = state.value
    val body = buildJsonObject {
      put("expected_revision", local.cloudRevision)
      put("new_document", podcastJson.encodeToJsonElement(local.library))
    }
      .toString()
    val revision = Json.parseToJsonElement(rpc("cadence_put", body)).jsonPrimitive.long
    save(local.copy(cloudRevision = revision))
  }

  /** Explicit restore: remote config wins; played section acknowledgements are grow-only. */
  suspend fun pullCloud() {
    val response = rpc("cadence_get")
    if (response == "null") return
    val remote = podcastJson.decodeFromString<CloudDocument>(response)
    remote.document.validate()
    val merged =
      remote.document.copy(
        episodeProgress =
          (remote.document.episodeProgress.keys + state.value.library.episodeProgress.keys)
            .associateWith { key ->
              listOfNotNull(
                  remote.document.episodeProgress[key],
                  state.value.library.episodeProgress[key],
                )
                .maxBy { it.updatedAtMs }
            },
        completions =
          (remote.document.completions + state.value.library.completions).distinctBy {
            it.sectionId to it.observedRevision
          },
      )
    save(
      state.value.copy(
        library = merged,
        cloudRevision = remote.revision,
        copiedSectionIds = emptySet(),
      ),
    )
    recheckReady()
  }
}

private fun nowMs() = Clock.System.now().toEpochMilliseconds()

private fun count(n: Int, noun: String) = "$n $noun" + if (n == 1) "" else "s"
