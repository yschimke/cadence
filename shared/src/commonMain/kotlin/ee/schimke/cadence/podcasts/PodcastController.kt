@file:OptIn(kotlin.time.ExperimentalTime::class)

package ee.schimke.cadence.podcasts

import androidx.compose.runtime.Composable
import kotlin.time.Clock
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

  suspend fun readState(): String?

  suspend fun saveState(state: String)

  suspend fun request(
    url: String,
    method: String = "GET",
    headers: Map<String, String> = emptyMap(),
    body: String? = null,
  ): String

  /** Fetches and parses a public feed, following HTTPS redirects without credentials. */
  suspend fun rss(url: String): RssFeed

  suspend fun prepare(episode: Episode, sections: List<Section>)

  suspend fun available(section: Section): Boolean

  suspend fun export(sections: List<Section>, target: String, manifest: String)

  fun publishStatus(status: String, ready: Boolean) {}
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
              )
          )
      )
    )
  val status = MutableStateFlow("Loading library…")
  val busy = MutableStateFlow(false)
  val signedIn = MutableStateFlow(false)
  private val lock = Mutex()
  private var token = ""
  private var authenticatedUser = ""
  private var authenticatedUrl = ""
  private var gpodderPassword = ""
  // A successful action's summary, shown instead of the generic "Saved".
  private var notice: String? = null

  suspend fun run(action: suspend PodcastController.() -> Unit) = lock.withLock {
    busy.value = true
    try {
      status.value = "Working…"
      publish()
      notice = null
      action()
      status.value = notice ?: "Saved"
    } catch (e: CancellationException) {
      status.value = "Cancelled · last saved library retained"
      throw e
    } catch (e: PodcastProblem) {
      status.value = e.message ?: "Operation failed"
    } catch (_: Exception) {
      // Network exceptions can contain signed enclosure URLs or credentials. Never surface raw
      // errors.
      status.value =
        "Operation failed. Check network, permissions, configuration and supported formats. Previous library is preserved."
    } finally {
      busy.value = false
      publish()
    }
  }

  suspend fun load() {
    platform.readState()?.let { stored ->
      val restored = podcastJson.decodeFromString<LocalPodcastState>(stored)
      restored.library.validate()
      state.value = restored
    }
    recheckReady()
    status.value = "Library loaded"
  }

  private suspend fun save(next: LocalPodcastState) {
    next.library.validate()
    platform.saveState(podcastJson.encodeToString(next))
    state.value = next
    publish()
  }

  private fun publish() {
    val ready = state.value.readyForDevice()
    platform.publishStatus(
      status.value + " · Last import: " + state.value.library.lastBackendSync,
      ready,
    )
  }

  suspend fun importSnapshot(text: String) {
    val snapshot = podcastJson.decodeFromString<PlaylistSnapshot>(text)
    snapshot.validate()
    save(
      state.value.copy(
        library =
          state.value.library.copy(
            playlist = snapshot,
            feedUrl = "",
            manifests = emptyList(),
            lastBackendSync = Clock.System.now().toString(),
          ),
        readySectionIds = emptySet(),
        copiedSectionIds = emptySet(),
      )
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
        else episode.copy(positionMs = advancedPosition(previous, old.completions))
      }
    importSnapshot(podcastJson.encodeToString(snapshot.copy(episodes = episodes)))
    save(state.value.copy(library = state.value.library.copy(feedUrl = url, feedLatest = latest)))
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
      }
      else ->
        throw PodcastProblem(
          "Paste a Cadence snapshot (JSON) or an Overcast All data export (OPML)."
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
        "No unfinished episodes on this account. Play something in your podcast app first."
      )
    val (snapshot, skipped) = resolveProgress(platform, "gpodder", "gpodder in progress", progress)
    importResolved(
      snapshot,
      skipped,
      account.server.substringAfter("https://").substringBefore('/'),
    )
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
    if (episode.backend != "gpodder") throw PodcastProblem("Only gpodder imports accept positions")
    val account = state.value.gpodder
    if (gpodderPassword.isBlank()) throw PodcastProblem("Import from gpodder again to sign in")
    val position = advancedPosition(episode, state.value.library.completions)
    if (position <= episode.positionMs) throw PodcastProblem("Mark sections played first")
    val (podcast, audio) = episode.id.split(' ', limit = 2)
    val current =
      gpodderPlays(
          platform.request(account.actionsUrl(), headers = account.headers(gpodderPassword))
        )
        .firstOrNull { it.podcast == podcast && it.episode == audio }
    val unchanged = current?.let { "gpodder:${it.timestamp}:${it.position}" } == episode.revision
    // A later send from this device is also expected state.
    val ownSend =
      state.value.sentPositions[episodeKey]?.let { current?.position == it / 1000 } == true
    if (!unchanged && !ownSend)
      throw PodcastProblem(
        "This episode moved in another app since import. Import again before sending a position."
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

  suspend fun configure(config: Segmentation) {
    config.validate()
    save(
      state.value.copy(
        library = state.value.library.copy(config = config, manifests = emptyList()),
        readySectionIds = emptySet(),
        copiedSectionIds = emptySet(),
      )
    )
  }

  suspend fun prepare() {
    val library = state.value.library
    val playlist = requireNotNull(library.playlist) { "Import a playlist first" }
    require(playlist.episodes.isNotEmpty())
    val manifests =
      playlist.episodes.map { Manifest(it, library.config, segment(it, library.config)) }
    if (
      manifests.any { m ->
        (m.sections.size > 1 || m.sections.first().startMs > 0) &&
          m.config.chunkFormat !in platform.splitFormats
      }
    )
      throw PodcastProblem(
        "Choose a supported chunk format: Android uses M4A, desktop supports MP3/M4A, web requires unsplit audio."
      )
    // Publish the manifest and readiness only after every audio file is committed.
    manifests.forEachIndexed { index, manifest ->
      status.value = "Downloading ${index + 1} of ${manifests.size}: ${manifest.episode.title}"
      publish()
      platform.prepare(manifest.episode, manifest.sections)
    }
    val sections = manifests.flatMap { it.sections }
    require(sections.all { platform.available(it) })
    notice = "Ready: ${count(manifests.size, "episode")} in ${count(sections.size, "file")}"
    save(
      state.value.copy(
        library = library.copy(manifests = manifests),
        readySectionIds = sections.map { it.id }.toSet(),
        copiedSectionIds = emptySet(),
      )
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
              }
          )
      )
    )
    // Provider writes are deliberately capability gated. A local acknowledgement never claims
    // success remotely.
  }

  suspend fun setTarget(target: String) {
    save(state.value.copy(target = target, copiedSectionIds = emptySet()))
  }

  suspend fun copyToDevice() {
    recheckReady()
    val sections = state.value.library.manifests.flatMap { it.sections }
    if (sections.isEmpty() || sections.any { it.id !in state.value.readySectionIds })
      throw PodcastProblem("Prepare all audio on this device before copying to headphones.")
    require(state.value.target.isNotBlank())
    platform.export(
      sections,
      state.value.target,
      podcastJson.encodeToString(deviceManifest(state.value.library)),
    )
    save(state.value.copy(copiedSectionIds = sections.map { it.id }.toSet()))
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
      url.startsWith("https://") && !url.trimEnd('/').substringAfter("https://").contains('/')
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
        "Use a publishable/anon key. Service-role and secret keys must never be installed in the app."
      )
    signOut()
    save(
      state.value.copy(
        cloudUrl = url.trimEnd('/'),
        cloudKey = key,
        cloudRevision = 0,
        cloudUserId = "",
      )
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
          )
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
        authenticatedUrl == local.cloudUrl
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
        completions =
          (remote.document.completions + state.value.library.completions).distinctBy {
            it.sectionId to it.observedRevision
          }
      )
    save(
      state.value.copy(
        library = merged,
        cloudRevision = remote.revision,
        copiedSectionIds = emptySet(),
      )
    )
    recheckReady()
  }
}

private fun count(n: Int, noun: String) = "$n $noun" + if (n == 1) "" else "s"
