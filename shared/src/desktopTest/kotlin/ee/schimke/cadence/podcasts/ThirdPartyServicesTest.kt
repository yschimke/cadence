package ee.schimke.cadence.podcasts

import kotlin.test.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*

class ThirdPartyServicesTest {
  @Test
  fun audiobookshelfSignInImportDownloadAndConflictCheckedPositionSync() = runBlocking {
    val platform = MockServicePlatform()
    val controller = PodcastController(platform)

    controller.signInAudiobookshelf("https://abs.example/", "alice", "secret")
    assertEquals("https://abs.example", controller.state.value.audiobookshelf.server)
    assertEquals("abs-jwt-1", controller.state.value.audiobookshelf.token)
    assertEquals(
      listOf("Audiobooks", "Podcasts"),
      controller.audiobookshelfLibraries.value.map { it.name },
    )

    // Import "Continue listening" (one book with chapters + progress, one podcast episode).
    controller.importAudiobookshelf(AUDIOBOOKSHELF_IN_PROGRESS, 5)
    val episodes = controller.state.value.library.playlist!!.episodes
    assertEquals(2, episodes.size)
    val book = episodes[0]
    assertEquals(AUDIOBOOKSHELF_BACKEND, book.backend)
    assertEquals("li_book_1", book.id)
    assertEquals("Dune", book.title)
    assertEquals(3_600_000L, book.durationMs)
    assertEquals(300_000L, book.positionMs)
    assertEquals("m4a", book.extension)
    assertEquals(listOf(600_000L, 1_800_000L), book.chapterMs)
    assertEquals("https://abs.example/api/items/li_book_1/file/ino_1/download", book.audioUrl)
    // Persisted library never contains credentials or downloadHeaders.
    assertFalse(platform.storedState!!.contains("abs-jwt-1"))
    assertTrue(book.downloadHeaders.isEmpty())

    val pod = episodes[1]
    assertEquals("li_pod_1/ep_42", pod.id)
    assertEquals("Pilot Episode", pod.title)
    assertEquals(1_200_000L, pod.durationMs)
    assertEquals(60_000L, pod.positionMs)

    // Download attaches Authorization: Bearer header transiently.
    assertTrue(controller.run { downloadOriginals() })
    assertEquals(
      "Bearer abs-jwt-1",
      platform.downloadedHeaders["https://abs.example/api/items/li_book_1/file/ino_1/download"]
        ?.get("Authorization"),
    )

    // Prepare + advance position + send position back to Audiobookshelf via PATCH.
    assertTrue(controller.run { prepare() })
    controller.setEpisodePosition(book.key, 900_000L)
    assertTrue(controller.run { sendPosition(book.key) })
    assertEquals("PATCH", platform.lastPatchMethod)
    assertEquals("https://abs.example/api/me/progress/li_book_1", platform.lastPatchUrl)
    val patchJson = Json.parseToJsonElement(platform.lastPatchBody!!).jsonObject
    assertEquals(900.0, patchJson["currentTime"]!!.jsonPrimitive.double)

    // If another client moved the remote position, conflict check refuses overwrite.
    platform.absRemoteProgressSec = 1500.0
    controller.setEpisodePosition(book.key, 1_200_000L)
    assertFalse(controller.run { sendPosition(book.key) })
    assertTrue(controller.status.value.contains("moved in Audiobookshelf"))

    // API token sign-in works too.
    controller.signOutAudiobookshelf()
    assertFalse(controller.state.value.audiobookshelf.signedIn)
    controller.signInAudiobookshelfToken("https://abs.example", "abs-jwt-1")
    assertTrue(controller.state.value.audiobookshelf.signedIn)
  }

  @Test
  fun subsonicAuthImportAndAuthenticatedDownloads() = runBlocking {
    val platform = MockServicePlatform()
    val controller = PodcastController(platform)

    // Standard salted-MD5 auth query check.
    val query =
      subsonicAuthQuery(
        SubsonicSession("https://music.example", username = "bob", password = "pw"),
        json = true,
        salt = "abc123",
      )
    assertTrue(query.contains("u=bob&t="))
    assertTrue(query.contains("&s=abc123&v=1.16.1&c=Cadence&f=json"))

    // OpenSubsonic API key auth query check.
    val apiKeyQuery =
      subsonicAuthQuery(SubsonicSession("https://music.example", apiKey = "key-99"), json = false)
    assertEquals("apiKey=key-99&v=1.16.1&c=Cadence", apiKeyQuery)

    // Sign in and browse playlists + newest albums.
    controller.signInSubsonic("https://music.example/", "bob", "pw")
    assertEquals(listOf("Swim Mix"), controller.subsonicPlaylists.value.map { it.name })
    assertEquals(listOf("Ambient Waves"), controller.subsonicAlbums.value.map { it.name })

    controller.importSubsonic("${SUBSONIC_PLAYLIST_PREFIX}pl1", 10)
    val tracks = controller.state.value.library.playlist!!.episodes
    assertEquals(2, tracks.size)
    assertEquals("mp3", tracks[0].extension)
    assertEquals("flac", tracks[1].extension)
    // Stored URL is credential-free.
    assertEquals("https://music.example/rest/download?id=s1", tracks[0].audioUrl)
    assertFalse(tracks[0].audioUrl.contains("t="))

    // Download appends auth query params only at download time.
    assertTrue(controller.run { downloadOriginals() })
    val dl = platform.downloads.first()
    assertTrue(dl.startsWith("https://music.example/rest/download?id=s1&u=bob&t="))
  }

  @Test
  fun jellyfinSignInImportAndDownloadHeaders() = runBlocking {
    val platform = MockServicePlatform()
    val controller = PodcastController(platform)

    controller.signInJellyfin("https://jf.example/", "carol", "secret")
    assertEquals("jf-tok-1", controller.state.value.jellyfin.accessToken)
    assertEquals("user-1", controller.state.value.jellyfin.userId)
    assertEquals(listOf("Running"), controller.jellyfinPlaylists.value.map { it.name })
    assertEquals(listOf("Night Drive"), controller.jellyfinAlbums.value.map { it.name })

    controller.importJellyfin(JELLYFIN_AUDIOBOOKS, 5)
    val ep = controller.state.value.library.playlist!!.episodes.single()
    assertEquals(JELLYFIN_BACKEND, ep.backend)
    assertEquals("The Hobbit", ep.title)
    assertEquals(3_600_000L, ep.durationMs)
    assertEquals(120_000L, ep.positionMs)
    assertEquals("m4a", ep.extension)
    assertEquals("https://jf.example/Items/jf_book_1/Download", ep.audioUrl)

    assertTrue(controller.run { downloadOriginals() })
    val auth =
      platform.downloadedHeaders["https://jf.example/Items/jf_book_1/Download"]?.get(
        "Authorization",
      )
    assertNotNull(auth)
    assertTrue(auth.contains("""Token="jf-tok-1""""))
  }

  @Test
  fun libriVoxSearchAndHttpsRssNormalization() = runBlocking {
    val platform = MockServicePlatform()
    val controller = PodcastController(platform)
    controller.searchLibriVoxBooks("Pride")
    val books = controller.libriVoxResults.value
    assertEquals(1, books.size)
    assertEquals("Pride and Prejudice", books.single().title)
    assertEquals("Jane Austen", books.single().authors)
    assertEquals("https://librivox.org/rss/52", books.single().rssUrl)
  }

  @Test
  fun plexPinSignInImportDownloadAndConflictCheckedPositionSync() = runBlocking {
    val platform = MockServicePlatform()
    val controller = PodcastController(platform)

    // Plex.tv PIN sign-in discovers the HTTPS server and connects automatically when there is one.
    controller.startPlexPin()
    assertEquals(77L, controller.plexPin.value?.id)
    assertTrue(controller.plexPin.value!!.authUrl.contains("code=ABCD"))
    controller.pollPlexPin()
    assertEquals("https://plex.example:32400", controller.state.value.plex.server)
    assertEquals("plex-tok-1", controller.state.value.plex.token)
    assertEquals("Home Plex", controller.state.value.plex.serverName)
    assertEquals(listOf("Swim Beats"), controller.plexPlaylists.value.map { it.title })
    assertEquals(listOf("Audiobooks"), controller.plexSections.value.map { it.title })

    controller.importPlex("${PLEX_PLAYLIST_PREFIX}901", 5)
    val ep = controller.state.value.library.playlist!!.episodes.single()
    assertEquals(PLEX_BACKEND, ep.backend)
    assertEquals("501", ep.id)
    assertEquals("Chapter One", ep.title)
    assertEquals(1_800_000L, ep.durationMs)
    assertEquals(120_000L, ep.positionMs)
    assertEquals("m4a", ep.extension)
    assertEquals(
      "https://plex.example:32400/library/parts/99/1700/book.m4b?download=1",
      ep.audioUrl,
    )
    // Persisted library never contains X-Plex-Token.
    assertFalse(platform.storedState!!.contains("plex-tok-1"))

    // Download attaches X-Plex-Token transiently in headers.
    assertTrue(controller.run { downloadOriginals() })
    assertEquals(
      "plex-tok-1",
      platform.downloadedHeaders[ep.audioUrl]?.get("X-Plex-Token"),
    )

    // Prepare + advance position + send position back to Plex with conflict check.
    assertTrue(controller.run { prepare() })
    controller.setEpisodePosition(ep.key, 600_000L)
    assertTrue(controller.run { sendPosition(ep.key) })
    assertTrue(platform.lastPlexProgressUrl!!.contains("/:/progress?key=501"))
    assertTrue(platform.lastPlexProgressUrl!!.contains("time=600000"))

    // If another client moved viewOffset in Plex, conflict check refuses overwrite.
    platform.plexRemoteOffsetMs = 900_000L
    controller.setEpisodePosition(ep.key, 750_000L)
    assertFalse(controller.run { sendPosition(ep.key) })
    assertTrue(controller.status.value.contains("moved in Plex"))
  }

  @Test
  fun secretsSplitAndMergeAllNewServiceSessions() {
    val state =
      LocalPodcastState(
        audiobookshelf = AudiobookshelfSession("https://abs.example", "tok1", "alice"),
        subsonic = SubsonicSession("https://music.example", "bob", "pw"),
        jellyfin = JellyfinSession("https://jf.example", "jf1", "u1", "carol"),
        plex = PlexSession("https://plex.example:32400", "px1", "Home Plex"),
      )
    val split = PodcastSecrets.split(podcastJson.encodeToString(state))
    assertTrue(split.changed)
    assertNotNull(split.secrets)
    assertFalse(split.library.contains("tok1"))
    assertFalse(split.library.contains("\"pw\""))
    assertFalse(split.library.contains("jf1"))
    assertFalse(split.library.contains("px1"))
    val restored =
      podcastJson.decodeFromString<LocalPodcastState>(
        PodcastSecrets.merge(split.library, split.secrets),
      )
    assertEquals(state, restored)
  }

  @Test
  fun audiobookshelfAndSubsonicCanBePairedTogether() = runBlocking {
    val platform = MockServicePlatform()
    val controller = PodcastController(platform)
    controller.signInAudiobookshelf("https://abs.example", "alice", "secret")
    controller.signInSubsonic("https://music.example", "bob", "pw")
    controller.importAudiobookshelf(AUDIOBOOKSHELF_IN_PROGRESS, 1)
    controller.importSecond(PodcastSourceKind.Subsonic, "${SUBSONIC_PLAYLIST_PREFIX}pl1", 2)

    val library = controller.state.value.library
    assertEquals(PodcastSourceKind.Audiobookshelf, library.source)
    assertEquals(PodcastSourceKind.Subsonic, library.second?.kind)
    val playlist = library.playlist!!
    assertEquals(3, playlist.episodes.size)
    assertEquals(
      listOf(AUDIOBOOKSHELF_BACKEND, SUBSONIC_BACKEND, SUBSONIC_BACKEND),
      playlist.episodes.map { it.backend },
    )
  }

  @Test
  fun refusesCredentialsForMismatchedDownloadOrigin() = runBlocking {
    val platform = MockServicePlatform()
    val controller = PodcastController(platform)
    controller.signInAudiobookshelf("https://abs.example", "alice", "secret")

    // Origin matcher rejects prefix spoofs, different ports, and userinfo.
    assertTrue(
      sameHttpsOrigin("https://ABS.EXAMPLE:443/api/items/1/file/2/download", "https://abs.example"),
    )
    assertFalse(sameHttpsOrigin("https://abs.example.evil.com/file.m4b", "https://abs.example"))
    assertFalse(sameHttpsOrigin("https://abs.example:8443/file.m4b", "https://abs.example"))
    assertFalse(sameHttpsOrigin("https://abs.example@evil.com/file.m4b", "https://abs.example"))

    // Crafted snapshot claiming audiobookshelf backend with an attacker URL must never get the
    // token.
    val crafted =
      PlaylistSnapshot(
        name = "Crafted",
        episodes =
          listOf(
            Episode(
              backend = AUDIOBOOKSHELF_BACKEND,
              id = "li_evil",
              title = "Evil",
              audioUrl = "https://attacker.example/steal.m4b",
              durationMs = 60_000,
              revision = "abs:0:0",
              audioVersion = "v1",
              extension = "m4a",
            ),
          ),
      )
    controller.importSnapshot(podcastJson.encodeToString(crafted))
    assertFalse(controller.run { downloadOriginals() })
    assertTrue(platform.downloads.isEmpty())
    assertTrue(platform.downloadedHeaders.isEmpty())
  }

  private class MockServicePlatform : PodcastPlatform {
    override val splitFormats = setOf("mp3", "m4a")
    override val description = "test"
    override val supportsOriginalDownloads = true

    var storedState: String? = null
    val downloads = mutableListOf<String>()
    val downloadedHeaders = mutableMapOf<String, Map<String, String>>()
    val downloadedKeys = mutableSetOf<String>()
    val readySections = mutableSetOf<String>()
    var absRemoteProgressSec = 300.0
    var plexRemoteOffsetMs = 120_000L
    var lastPatchMethod: String? = null
    var lastPatchUrl: String? = null
    var lastPatchBody: String? = null
    var lastPlexProgressUrl: String? = null

    override suspend fun readState(): String? = storedState

    override suspend fun saveState(state: String) {
      storedState = PodcastSecrets.split(state).library
    }

    override suspend fun request(
      url: String,
      method: String,
      headers: Map<String, String>,
      body: String?,
    ): String {
      if (method == "PATCH") {
        lastPatchMethod = method
        lastPatchUrl = url
        lastPatchBody = body
        return "{}"
      }
      if (url.contains("/:/progress?")) {
        lastPlexProgressUrl = url
        return "{}"
      }
      return when {
        // Audiobookshelf
        url == "https://abs.example/login" ->
          """{"user":{"id":"u1","username":"alice","token":"abs-jwt-1"}}"""
        url == "https://abs.example/api/me" ->
          """{"username":"alice","mediaProgress":[
            {"libraryItemId":"li_book_1","episodeId":"","currentTime":$absRemoteProgressSec,"duration":3600,"isFinished":false,"lastUpdate":1700000000000},
            {"libraryItemId":"li_pod_1","episodeId":"ep_42","currentTime":60,"duration":1200,"isFinished":false,"lastUpdate":1700000000000}
          ]}"""
        url == "https://abs.example/api/libraries" ->
          """{"libraries":[{"id":"lib1","name":"Audiobooks","mediaType":"book"},{"id":"lib2","name":"Podcasts","mediaType":"podcast"}]}"""
        url == "https://abs.example/api/me/items-in-progress" ->
          """{"libraryItems":[{"id":"li_book_1"},{"id":"li_pod_1","recentEpisode":{"id":"ep_42"}}]}"""
        url == "https://abs.example/api/items/li_book_1?expanded=1" ->
          """{"id":"li_book_1","mediaType":"book","media":{"duration":3600,"metadata":{"title":"Dune","authorName":"Frank Herbert","narratorName":"Scott Brick","description":"Arrakis","publishedYear":"1965"},"chapters":[{"start":0},{"start":600},{"start":1800}],"audioFiles":[{"ino":"ino_1","index":1,"duration":3600,"mimeType":"audio/mp4","metadata":{"ext":".m4b"}}]}}"""
        url == "https://abs.example/api/items/li_pod_1?expanded=1" ->
          """{"id":"li_pod_1","mediaType":"podcast","media":{"metadata":{"title":"Tech Show"},"episodes":[{"id":"ep_42","title":"Pilot Episode","description":"First","pubDate":"2026-01-01","duration":1200,"audioFile":{"ino":"ino_ep","index":1,"duration":1200,"mimeType":"audio/mpeg","metadata":{"ext":".mp3"}}}]}}"""
        url == "https://abs.example/api/me/progress/li_book_1" ->
          """{"libraryItemId":"li_book_1","currentTime":$absRemoteProgressSec,"duration":3600,"isFinished":false,"lastUpdate":1700000000000}"""

        // Subsonic / OpenSubsonic
        url.startsWith("https://music.example/rest/ping?") ->
          """{"subsonic-response":{"status":"ok","version":"1.16.1"}}"""
        url.startsWith("https://music.example/rest/getPlaylists?") ->
          """{"subsonic-response":{"status":"ok","playlists":{"playlist":[{"id":"pl1","name":"Swim Mix","songCount":2}]}}}"""
        url.startsWith("https://music.example/rest/getAlbumList2?") ->
          """{"subsonic-response":{"status":"ok","albumList2":{"album":[{"id":"al1","name":"Ambient Waves","artist":"Oceanic","songCount":8}]}}}"""
        url.startsWith("https://music.example/rest/getPlaylist?id=pl1") ->
          """{"subsonic-response":{"status":"ok","playlist":{"id":"pl1","name":"Swim Mix","entry":[
            {"id":"s1","title":"Lap One","artist":"Poolside","album":"Summer","albumId":"al2","duration":210,"size":5000000,"suffix":"mp3","contentType":"audio/mpeg"},
            {"id":"s2","title":"Deep End","artist":"Poolside","album":"Summer","albumId":"al2","duration":240,"size":25000000,"suffix":"flac","contentType":"audio/flac"}
          ]}}}"""

        // Jellyfin
        url == "https://jf.example/Users/AuthenticateByName" ->
          """{"AccessToken":"jf-tok-1","User":{"Id":"user-1","Name":"carol"}}"""
        url.contains("IncludeItemTypes=Playlist") ->
          """{"Items":[{"Id":"jpl1","Name":"Running"}]}"""
        url.contains("IncludeItemTypes=MusicAlbum") ->
          """{"Items":[{"Id":"jal1","Name":"Night Drive","AlbumArtist":"Synth"}]}"""
        url.contains("IncludeItemTypes=AudioBook") ->
          """{"Items":[{"Id":"jf_book_1","Name":"The Hobbit","Type":"AudioBook","AlbumArtist":"J.R.R. Tolkien","RunTimeTicks":36000000000,"MediaSources":[{"Container":"m4b","Size":90000000}],"UserData":{"PlaybackPositionTicks":1200000000}}]}"""

        // Plex
        url == "https://plex.tv/api/v2/pins?strong=true" -> """{"id":77,"code":"ABCD"}"""
        url == "https://plex.tv/api/v2/pins/77" ->
          """{"id":77,"code":"ABCD","authToken":"plex-account-tok"}"""
        url == "https://plex.tv/api/v2/resources?includeHttps=1" ->
          """[{"name":"Home Plex","provides":"server","accessToken":"plex-tok-1","connections":[{"uri":"https://plex.example:32400","relay":false}]}]"""
        url == "https://plex.example:32400/library/sections" ->
          """{"MediaContainer":{"Directory":[{"key":"3","title":"Audiobooks","type":"artist"},{"key":"1","title":"Movies","type":"movie"}]}}"""
        url == "https://plex.example:32400/playlists?playlistType=audio" ->
          """{"MediaContainer":{"Metadata":[{"ratingKey":"901","title":"Swim Beats","leafCount":1}]}}"""
        url == "https://plex.example:32400/playlists/901/items" ||
          url == "https://plex.example:32400/library/metadata/501" ->
          """{"MediaContainer":{"title":"Swim Beats","Metadata":[{"ratingKey":"501","title":"Chapter One","grandparentTitle":"Author","parentTitle":"Book","parentRatingKey":"500","duration":1800000,"viewOffset":$plexRemoteOffsetMs,"Media":[{"container":"m4b","Part":[{"key":"/library/parts/99/1700/book.m4b","container":"m4b","size":45000000}]}]}]}}"""

        // LibriVox
        url.startsWith(LIBRIVOX_API) ->
          """{"books":[{"id":"52","title":"Pride and Prejudice","authors":[{"first_name":"Jane","last_name":"Austen"}],"totaltime":"11:35:26","language":"English","url_rss":"http://librivox.org/rss/52"}]}"""

        else -> error("Unexpected request: $method $url")
      }
    }

    override suspend fun rss(url: String): RssFeed = RssFeed("Feed", emptyList())

    override suspend fun originalAvailable(episode: Episode): Boolean =
      episode.key in downloadedKeys

    override suspend fun downloadOriginal(
      episode: Episode,
      onProgress: (downloaded: Long, total: Long) -> Unit,
    ) {
      downloads += episode.audioUrl
      downloadedHeaders[episode.audioUrl] = episode.downloadHeaders
      downloadedKeys += episode.key
    }

    override suspend fun prepare(episode: Episode, sections: List<Section>) {
      downloadedKeys += episode.key
      readySections += sections.map { it.id }
    }

    override suspend fun available(section: Section): Boolean = section.id in readySections

    override suspend fun export(sections: List<Section>, target: String, manifest: String) {}
  }
}
