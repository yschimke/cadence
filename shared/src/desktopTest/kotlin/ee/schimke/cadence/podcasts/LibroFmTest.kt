package ee.schimke.cadence.podcasts

import kotlin.test.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*

/**
 * Response shapes follow the Libro.fm app API as open-source clients use it; the data is synthetic.
 */
class LibroFmTest {
  private fun book(isbn: String, title: String, duration: Int) =
    """{"title":"$title","authors":["A. Writer"],"isbn":"$isbn","cover_url":"https://covers.example/$isbn.jpg","publication_date":"2024-01-02T00:00:00Z","description":"About $title","audiobook_info":{"narrators":["N. Reader"],"duration":$duration,"track_count":3}}"""

  @Test
  fun parsesTokenLibraryAndDownload() {
    assertEquals(
      LibroFmSession("t1", "me@example.com"),
      libroFmSession("""{"access_token":"t1"}""", "me@example.com"),
    )
    assertFailsWith<PodcastProblem> { libroFmSession("""{"error":"no"}""", "") }
    val (books, pages) =
      libroFmLibraryPage(
        """{"page":1,"total_pages":2,"audiobooks":[${book("9780000000001", "One", 3600)}]}""",
      )
    assertEquals(2, pages)
    val episode = libroFmToEpisode(books.single())!!
    assertEquals(LIBRO_FM_BACKEND, episode.backend)
    assertEquals("9780000000001", episode.id)
    assertEquals(3_600_000, episode.durationMs)
    assertEquals("m4a", episode.extension)
    assertEquals("A. Writer", episode.podcastTitle)
    assertEquals("https://covers.example/9780000000001.jpg", episode.artworkUrl)
    assertTrue(episode.summary.startsWith("Narrated by N. Reader"))
    // No length, no episode: segments couldn't be planned.
    assertNull(libroFmToEpisode(books.single().copy(durationSeconds = 0)))
    assertEquals(
      "https://s3.example/b.m4b?sig=1",
      libroFmM4bUrl("""{"m4b_url":"https://s3.example/b.m4b?sig=1"}"""),
    )
    assertFailsWith<PodcastProblem> { libroFmM4bUrl("""{"m4b_url":"http://s3.example/b.m4b"}""") }
  }

  @Test
  fun signsInImportsAndDownloadsWithAFreshLink() = runBlocking {
    val platform = LibroPlatform()
    val controller = PodcastController(platform)
    controller.signInLibroFm("me@example.com", "secret")
    val login = Json.parseToJsonElement(platform.loginBody!!).jsonObject
    assertEquals("password", login.getValue("grant_type").jsonPrimitive.content)
    assertEquals("me@example.com", login.getValue("username").jsonPrimitive.content)
    assertEquals("t1", controller.state.value.libroFm.accessToken)
    // Both library pages were read.
    assertEquals(
      listOf("One", "Two", "No length"),
      controller.libroFmBooks.value.map { it.title },
    )

    controller.importLibroFm(LIBRO_FM_LIBRARY, 1)
    var library = controller.state.value.library
    assertEquals(PodcastSourceKind.LibroFm, library.source)
    assertEquals(listOf("9780000000001"), library.playlist!!.episodes.map { it.id })
    assertEquals(listOf("9780000000002"), library.upcoming.map { it.id })

    // One book alone; the local position survives a refresh, since Libro.fm has none.
    controller.importLibroFm("9780000000002", 3)
    library = controller.state.value.library
    assertEquals("Two", library.playlist!!.name)
    val two = library.playlist!!.episodes.single()
    controller.setEpisodePosition(two.key, 60_000)
    assertTrue(controller.canRefresh)
    controller.refreshSource()
    assertEquals(60_000, controller.state.value.library.playlist!!.episodes.single().positionMs)

    // The stored link is never downloaded; each download asks for a fresh signed one.
    assertTrue(controller.run { downloadOriginals() })
    assertEquals(listOf("https://s3.example/9780000000002.m4b?sig=1"), platform.downloads)

    // Every request passed the app headers that Libro.fm's gate requires.
    assertTrue(platform.agents.all { it == "okhttp/5.3.2" })

    // A refused token signs out.
    platform.token = "t2"
    assertFalse(controller.run { refreshLibroFmBooks() })
    assertFalse(controller.state.value.libroFm.signedIn)
    assertTrue(controller.libroFmBooks.value.isEmpty())
  }

  @Test
  fun aBookWithoutAnM4bDownloadsItsMp3Tracks() = runBlocking {
    val platform = LibroPlatform()
    platform.noM4b += "9780000000002"
    val controller = PodcastController(platform)
    controller.signInLibroFm("me@example.com", "secret")
    controller.importLibroFm(LIBRO_FM_LIBRARY, 2)
    val (m4b, mp3) = controller.state.value.library.playlist!!.episodes
    assertEquals("m4a", m4b.extension)
    assertEquals("mp3", mp3.extension)
    assertNotEquals(m4b.audioVersion, libroFmAudioVersion(mp3.id, "m4b", mp3.durationMs))
    // A refresh keeps the MP3 format without asking again.
    val asked = platform.m4bChecks.count { it == "9780000000002" }
    controller.refreshSource()
    assertEquals(asked, platform.m4bChecks.count { it == "9780000000002" })
    assertEquals("mp3", controller.state.value.library.playlist!!.episodes[1].extension)

    assertTrue(controller.run { downloadOriginals() })
    // The MP3 book downloads from its zip parts, in order; the M4B one from its file.
    assertEquals(
      listOf(
        "https://s3.example/9780000000001.m4b?sig=1",
        "https://s3.example/9780000000002-1.zip?sig=1",
      ),
      platform.downloads,
    )
    assertEquals(
      listOf(
        "https://s3.example/9780000000002-1.zip?sig=1",
        "https://s3.example/9780000000002-2.zip?sig=1",
      ),
      platform.parts.single(),
    )
  }

  @Test
  fun aBookImportedBeforeTheFallbackMovesToMp3() = runBlocking {
    val platform = LibroPlatform()
    val controller = PodcastController(platform)
    controller.signInLibroFm("me@example.com", "secret")
    // Imported as M4B (the only format before the fallback), never downloaded.
    controller.importLibroFm(LIBRO_FM_LIBRARY, 2)
    assertEquals("m4a", controller.state.value.library.playlist!!.episodes[1].extension)
    platform.noM4b += "9780000000002"
    controller.refreshSource()
    assertEquals("mp3", controller.state.value.library.playlist!!.episodes[1].extension)
  }

  @Test
  fun keptTracksBecomeOneFileEach() = runBlocking {
    val platform = LibroPlatform()
    platform.noM4b += "9780000000002"
    val controller = PodcastController(platform)
    controller.signInLibroFm("me@example.com", "secret")
    controller.importLibroFm("9780000000002", 1)
    controller.configure(controller.state.value.library.config.copy(split = SplitMode.Chapters))
    // Preparing downloads the book first, to measure its tracks.
    controller.prepare()
    val book = controller.state.value.library.playlist!!.episodes.single()
    assertEquals(listOf(3_000_000L, 4_200_000L), book.trackMs)
    val sections = controller.state.value.library.manifests.single().sections
    assertEquals(
      listOf(0L to 3_000_000L, 3_000_000L to 7_200_000L),
      sections.map { it.startMs to it.endMs },
    )
    assertTrue(sections.all { it.fileName.endsWith(".mp3") })
    // The measured tracks survive a refresh.
    controller.refreshSource()
    assertEquals(book.trackMs, controller.state.value.library.playlist!!.episodes.single().trackMs)
  }

  @Test
  fun anM4bSplitsAtItsChapterMarks() = runBlocking {
    val platform = LibroPlatform()
    val controller = PodcastController(platform)
    controller.signInLibroFm("me@example.com", "secret")
    controller.importLibroFm("9780000000001", 1)
    controller.configure(controller.state.value.library.config.copy(split = SplitMode.Chapters))
    controller.prepare()
    val book = controller.state.value.library.playlist!!.episodes.single()
    assertEquals(listOf(1_200_000L, 2_400_000L), book.chapterMs)
    assertEquals(
      listOf(0L to 1_200_000L, 1_200_000L to 2_400_000L, 2_400_000L to 3_600_000L),
      controller.state.value.library.manifests.single().sections.map { it.startMs to it.endMs },
    )
    // A podcast's own setting overrides the library's: kept whole here.
    controller.configurePodcastTrim(book.podcastId, PodcastTrim(split = SplitMode.Whole))
    controller.prepare()
    assertEquals(1, controller.state.value.library.manifests.single().sections.size)
  }

  @Test
  fun anOutageDoesNotSwitchABookToMp3() = runBlocking {
    val platform = LibroPlatform()
    platform.m4bOutage = true
    val controller = PodcastController(platform)
    controller.signInLibroFm("me@example.com", "secret")
    assertFalse(controller.run { importLibroFm(LIBRO_FM_LIBRARY, 1) })
  }

  @Test
  fun aBookThatLeftTheLibraryFails() = runBlocking {
    val controller = PodcastController(LibroPlatform())
    controller.signInLibroFm("me@example.com", "secret")
    val problem = assertFailsWith<PodcastProblem> { controller.importLibroFm("9789999999999") }
    assertEquals("That book is no longer in your Libro.fm library", problem.message)
  }

  private inner class LibroPlatform : PodcastPlatform {
    override val splitFormats = setOf("mp3", "m4a")
    override val description = "Fixture"
    override val supportsOriginalDownloads = true
    var saved: String? = null
    var token = "t1"
    var loginBody: String? = null
    val agents = mutableListOf<String>()
    val downloads = mutableListOf<String>()
    val parts = mutableListOf<List<String>>()
    val noM4b = mutableSetOf<String>()
    val m4bChecks = mutableListOf<String>()
    var m4bOutage = false
    private val downloaded = mutableSetOf<String>()

    override suspend fun readState() = saved

    override suspend fun saveState(state: String) {
      saved = state
    }

    override suspend fun request(
      url: String,
      method: String,
      headers: Map<String, String>,
      body: String?,
    ): String {
      assertTrue(url.startsWith(LIBRO_FM_API))
      agents += headers.getValue("User-Agent")
      assertTrue("X-LibroFm-AppVer" in headers)
      val path = url.removePrefix(LIBRO_FM_API)
      if (path == "/oauth/token") {
        assertEquals("POST", method)
        loginBody = body
        return """{"access_token":"t1"}"""
      }
      if (headers["Authorization"] != "Bearer $token")
        throw PodcastProblem("The server refused the sign-in.", 401)
      return when {
        path == "/api/v10/library?page=1" ->
          """{"page":1,"total_pages":2,"audiobooks":[${book("9780000000001", "One", 3600)},${book("9780000000002", "Two", 7200)}]}"""
        path == "/api/v10/library?page=2" ->
          """{"page":2,"total_pages":2,"audiobooks":[${book("9780000000003", "No length", 0)}]}"""
        path.startsWith("/api/v10/download-manifest?isbn=") -> {
          val isbn = path.substringAfter("isbn=")
          """{"parts":[{"url":"https://s3.example/$isbn-1.zip?sig=1","size_bytes":10},{"url":"https://s3.example/$isbn-2.zip?sig=1","size_bytes":10}],"tracks":[]}"""
        }
        path.startsWith("/api/v10/audiobooks/") && path.endsWith("/packaged_m4b") -> {
          val isbn = path.split('/')[4]
          m4bChecks += isbn
          if (m4bOutage) throw PodcastProblem("Unavailable", 503)
          if (isbn in noM4b) throw PodcastProblem("Not found (HTTP 404).", 404)
          """{"m4b_url":"https://s3.example/$isbn.m4b?sig=1"}"""
        }
        else -> error("Unexpected $path")
      }
    }

    override suspend fun originalAvailable(episode: Episode) = episode.key in downloaded

    override suspend fun downloadOriginal(episode: Episode, onProgress: (Long, Long) -> Unit) {
      downloads += episode.audioUrl
      if (episode.audioParts.isNotEmpty()) parts += episode.audioParts
      downloaded += episode.key
    }

    override suspend fun rss(url: String) = error("No feeds")

    val prepared = mutableListOf<Section>()

    override suspend fun prepare(episode: Episode, sections: List<Section>) {
      prepared += sections
    }

    override suspend fun available(section: Section) = section in prepared

    /** Chapter marks in a downloaded M4B: two, inside the first book's hour. */
    override suspend fun chapters(episode: Episode) =
      if (episode.extension == "m4a" && episode.key in downloaded) listOf(1_200_000L, 2_400_000L)
      else emptyList()

    /** The joined books' measured tracks: two, adding up to the listed length. */
    override suspend fun trackLengths(episode: Episode) =
      if (episode.extension == "mp3" && episode.key in downloaded)
        listOf(3_000_000L, episode.durationMs - 3_000_000L)
      else emptyList()

    override suspend fun export(sections: List<Section>, target: String, manifest: String) {}
  }
}
