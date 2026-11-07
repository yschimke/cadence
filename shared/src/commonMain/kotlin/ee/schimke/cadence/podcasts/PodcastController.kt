@file:OptIn(kotlin.time.ExperimentalTime::class)

package ee.schimke.cadence.podcasts

import androidx.compose.runtime.Composable
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.CancellationException
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

  /**
   * False in the browser, where Pocket Casts only accepts API calls from its own site, unless a
   * [pocketCastsServer] makes them.
   */
  val pocketCastsApi: Boolean
    get() = true

  /** Set where a server keeps the Pocket Casts sign-in and makes the calls. */
  val pocketCastsServer: PocketCastsServer?
    get() = null
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
            library = restored.library.copy(config = supported(restored.library.config)),
          )
      }
      platform.pocketCastsServer?.let { server ->
        // The server's sign-in is the one that counts; another browser may have changed it. If it
        // can't be reached, the last known state stays until a call says otherwise.
        runCatching { server.session() }
          .onSuccess { email ->
            state.value =
              state.value.copy(
                pocketCasts =
                  if (email == null) PocketCastsSession()
                  else PocketCastsSession(email = email, server = true),
              )
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
   * Replaces the playlist with [text]'s snapshot. [upcoming] and [added] are saved in the same
   * write, so a refresh cancelled during the cache checks below never loses them.
   */
  suspend fun importSnapshot(
    text: String,
    upcoming: List<Episode> = emptyList(),
    added: List<Episode> = emptyList(),
  ) {
    val snapshot = podcastJson.decodeFromString<PlaylistSnapshot>(text)
    snapshot.validate()
    save(
      state.value.copy(
        library =
          state.value.library.copy(
            playlist = snapshot,
            upcoming = upcoming,
            added = added,
            feedUrl = "",
            manifests = emptyList(),
            lastBackendSync = Clock.System.now().toString(),
          ),
        readySectionIds = emptySet(),
        copiedSectionIds = emptySet(),
      ),
    )
    platform.pruneAudio(snapshot.episodes)
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
        platform.downloadOriginal(episode) { bytes, total ->
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
    val old = state.value.library
    val episodes =
      snapshot.episodes.map { episode ->
        val previous =
          old.playlist?.episodes?.find {
            it.key == episode.key && it.audioVersion == episode.audioVersion
          }
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
      else -> throw PodcastProblem("Import a new export to update these episodes")
    }
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
      "Imported ${count(minOf(all.size, latest), "episode")} from ${snapshot.name}" +
        (if (later > 0) " · $later more listed" else "") +
        if (skipped > 0) " · skipped $skipped without HTTPS audio or a known length" else ""
  }

  /** Writes need the token on this device; the web-player capture and servers only read. */
  private val pocketCastsWritable: Boolean
    get() =
      pocketCastsCapture == null &&
        !state.value.pocketCasts.server &&
        state.value.pocketCasts.signedIn

  /** Whether an episode can be archived in its source from here. */
  val canArchive: Boolean
    get() = state.value.library.source == PodcastSourceKind.PocketCasts && pocketCastsWritable

  /**
   * Whether a fresh episode can be added to the source. Up Next takes it in Pocket Casts too;
   * another list only gets it on this phone, since Pocket Casts playlists can't be edited from
   * here.
   */
  val canAddFresh: Boolean
    get() = canArchive

  /** Whether this source offers fresh episodes at all. */
  val supportsFresh: Boolean
    get() = canArchive

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
    val api = pocketCastsWriter()
    val podcast = podcastUuid(api, episode)
    api.sendPosition(episode.id, podcast, episode.durationMs, episode.durationMs)
    if (library.sourceList == POCKET_CASTS_UP_NEXT) api.upNext(UP_NEXT_REMOVE, episode, podcast)
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
    val waiting = current.upcoming.filterNot { it.key == episodeKey }
    // The next one in the list moves up into the synced episodes without another refresh.
    val promoted = waiting.take((current.feedLatest - kept.size).coerceAtLeast(0))
    save(
      state.value.copy(
        library =
          current.copy(
            playlist = (current.playlist ?: playlist).copy(episodes = kept + promoted),
            upcoming = waiting.drop(promoted.size),
            added = current.added.filterNot { it.key == episodeKey },
            manifests = current.manifests.filterNot { it.episode.key == episodeKey },
          ),
      ),
    )
    if (promoted.isNotEmpty()) {
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
    val room = library.upcoming.isEmpty() && playlist.episodes.size < library.feedLatest
    save(
      current.copy(
        library =
          library.copy(
            playlist =
              if (present || !room) playlist
              else playlist.copy(episodes = playlist.episodes + episode),
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
    if (episode.backend != "gpodder") throw PodcastProblem("Only gpodder imports accept positions")
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

  /**
   * What this platform can prepare: the browser copies whole episodes only, and Android encodes
   * only M4A, so settings it can't honour are switched rather than failing at download time.
   */
  fun supported(config: Segmentation): Segmentation {
    val formats = platform.splitFormats
    return when {
      formats.isEmpty() ->
        config.copy(splitAtPosition = false, skipListened = false, chunkMinutes = 0)
      config.chunkFormat !in formats -> config.copy(chunkFormat = formats.sorted().first())
      else -> config
    }
  }

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
    val library = state.value.library
    val playlist = requireNotNull(library.playlist) { "Import a playlist first" }
    require(playlist.episodes.isNotEmpty())
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
      platform.prepare(manifest.episode, manifest.sections)
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
    notice =
      "Copied ${count(sections.size, "file")} to the headphones. Eject the volume before unplugging."
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
