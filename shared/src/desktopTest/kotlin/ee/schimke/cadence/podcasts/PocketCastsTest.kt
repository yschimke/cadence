package ee.schimke.cadence.podcasts

import kotlin.test.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*

/** Response shapes follow the web player's API as observed on 2026-10-28; the data is synthetic. */
class PocketCastsTest {
  private fun episode(
    uuid: String,
    duration: Int,
    playedUpTo: Int,
    url: String = "https://cdn.example/$uuid.mp3",
    fileType: String = "audio/mpeg",
    isDeleted: Boolean = false,
  ) =
    """{"uuid":"$uuid","url":"$url","title":"Episode $uuid","duration":$duration,"playedUpTo":$playedUpTo,"fileType":"$fileType","playingStatus":2,"isDeleted":$isDeleted,"podcastUuid":"p1","podcastTitle":"Show"}"""

  private val episodes =
    mapOf(
      "e1" to episode("e1", 3000, 600),
      "e2" to episode("e2", 1800, 0, "https://cdn.example/e2.m4a?ad=1", "audio/x-m4a"),
      "e3" to episode("e3", 1200, 100, isDeleted = true),
      "e4" to episode("e4", 0, 0),
      "e5" to episode("e5", 900, 30, url = "http://cdn.example/e5.mp3"),
    )

  private val upNext =
    """{"serverModified":1,"episodes":[
      {"uuid":"e1","title":"Episode e1","url":"https://cdn.example/e1.mp3","podcast":"p1","published":"2026-10-01"},
      {"uuid":"e2","title":"Episode e2","url":"https://cdn.example/e2.m4a?ad=1","podcast":"p1","published":"2026-10-02"}],
      "episodeSync":[{"uuid":"e1","playedUpTo":600,"duration":3000}]}"""

  private val playlists =
    """{"playlists":[
      {"uuid":"manual","title":"Commute","manual":true,"showArchived":false,"isDeleted":false,"episodeOrder":["e3","e2","e1"],"episodes":[]},
      {"uuid":"smart","title":"New releases","manual":false,"isDeleted":false,"episodeOrder":[],"episodes":[]},
      {"uuid":"gone","title":"Old","manual":true,"isDeleted":true,"episodeOrder":["e1"]}]}"""

  private val inProgress =
    """{"total":3,"episodes":[${episodes.getValue("e1")},${episodes.getValue("e4")},${episodes.getValue("e5")}]}"""

  @Test
  fun cookieAndTokenResponsesBecomeASession() {
    val session =
      pocketCastsSession(
        """{"accessToken":"a1","tokenType":"Bearer","expiresIn":3600,"refreshToken":"r1"}""",
        1_000,
      )
    assertEquals(PocketCastsSession("a1", "r1", 3_601_000, ""), session)
    // A refresh may omit the refresh token; the previous one is kept.
    assertEquals("r1", pocketCastsSession("""{"accessToken":"a2"}""", 0, session).refreshToken)
    assertFailsWith<PodcastProblem> { pocketCastsSession("""{"errorMessage":"no"}""", 0) }
  }

  @Test
  fun signsInAndImportsUpNextInProgressAndManualPlaylists() = runBlocking {
    val platform = ApiPlatform()
    val controller = PodcastController(platform)
    controller.signInPocketCasts("listener@example.com", "secret")
    val login =
      Json.parseToJsonElement(platform.bodies.getValue("/user/login_pocket_casts")).jsonObject
    assertEquals("webplayer", login.getValue("scope").jsonPrimitive.content)
    assertEquals("a1", controller.state.value.pocketCasts.accessToken)
    assertEquals("listener@example.com", controller.state.value.pocketCasts.email)
    // Smart and deleted playlists aren't offered.
    assertEquals(
      listOf("Up Next", "In progress", "Commute"),
      controller.pocketCastsLists.value.map { it.title },
    )

    controller.importPocketCasts(POCKET_CASTS_UP_NEXT, 5)
    var playlist = controller.state.value.library.playlist!!
    assertEquals("Pocket Casts Up Next", playlist.name)
    assertEquals(listOf("e1", "e2"), playlist.episodes.map { it.id })
    assertEquals(600_000, playlist.episodes[0].positionMs)
    // Up Next had no length for e2, so it was read from the episode.
    assertEquals(1_800_000, playlist.episodes[1].durationMs)
    assertEquals("m4a", playlist.episodes[1].extension)
    assertEquals(listOf("e2"), platform.episodeReads)

    controller.importPocketCasts(POCKET_CASTS_IN_PROGRESS, 5)
    playlist = controller.state.value.library.playlist!!
    // e4 has no length and e5 isn't HTTPS.
    assertEquals(listOf("e1"), playlist.episodes.map { it.id })

    controller.importPocketCasts("manual", 5)
    playlist = controller.state.value.library.playlist!!
    assertEquals("Commute", playlist.name)
    // The archived member is hidden, as in the player.
    assertEquals(listOf("e2", "e1"), playlist.episodes.map { it.id })
    assertTrue(platform.headers.all { it == "Bearer a1" })

    controller.signOutPocketCasts()
    assertFalse(controller.state.value.pocketCasts.signedIn)
    assertTrue(controller.pocketCastsLists.value.isEmpty())
    assertFailsWith<PodcastProblem> { controller.importPocketCasts(POCKET_CASTS_UP_NEXT, 5) }
    Unit
  }

  @Test
  fun anExpiredTokenIsRefreshedOnceAndSaved() = runBlocking {
    val platform = ApiPlatform()
    platform.validToken = "a2"
    val controller = PodcastController(platform)
    controller.usePocketCastsCookie(
      """{"accessToken":"a1","tokenType":"Bearer","expiresIn":3600,"refreshToken":"r1"}"""
    )
    val refresh = Json.parseToJsonElement(platform.bodies.getValue("/user/token")).jsonObject
    assertEquals("refresh_token", refresh.getValue("grant_type").jsonPrimitive.content)
    assertEquals("r1", refresh.getValue("refresh_token").jsonPrimitive.content)
    assertEquals("a2", controller.state.value.pocketCasts.accessToken)
    assertTrue("\"a2\"" in platform.saved!!)

    // A rejected refresh signs out rather than retrying forever.
    platform.validToken = "a3"
    platform.refreshes = false
    assertFailsWith<PodcastProblem> { controller.refreshPocketCastsLists() }
    assertFalse(controller.state.value.pocketCasts.signedIn)
    Unit
  }

  @Test
  fun capturedPlayerResponsesImportWithoutAToken() = runBlocking {
    val capture = buildJsonArray {
      addJsonObject {
        put("path", "/user/playlists")
        put("body", playlists)
      }
      listOf("e1", "e2", "e3").forEach { uuid ->
        addJsonObject {
          put("path", "/user/episode")
          put("body", episodes.getValue(uuid))
        }
      }
    }
    val platform = ApiPlatform()
    val controller = PodcastController(platform)
    controller.usePocketCastsCapture(capture.toString())
    // Up Next and In progress weren't loaded by the player, so only the playlist is offered.
    assertEquals(listOf("Commute"), controller.pocketCastsLists.value.map { it.title })
    controller.importPocketCasts("manual", 5)
    assertEquals(
      listOf("e2", "e1"),
      controller.state.value.library.playlist!!.episodes.map { it.id },
    )
    assertTrue(platform.bodies.isEmpty())
    assertFailsWith<PodcastProblem> { controller.usePocketCastsCapture("[]") }
    Unit
  }

  private inner class ApiPlatform : PodcastPlatform {
    override val splitFormats = setOf("mp3", "m4a")
    override val description = "Fixture"
    var saved: String? = null
    var validToken = "a1"
    var refreshes = true
    val bodies = mutableMapOf<String, String>()
    val headers = mutableListOf<String>()
    val episodeReads = mutableListOf<String>()

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
      assertTrue(url.startsWith(POCKET_CASTS_API))
      val path = url.removePrefix(POCKET_CASTS_API)
      body?.let { bodies[path] = it }
      when (path) {
        "/user/login_pocket_casts" ->
          return """{"email":"","uuid":"u1","accessToken":"a1","tokenType":"Bearer","expiresIn":3600,"refreshToken":"r1"}"""
        "/user/token" ->
          if (refreshes)
            return """{"accessToken":"$validToken","tokenType":"Bearer","expiresIn":3600}"""
          else throw PodcastProblem("The server refused the sign-in.")
      }
      val auth = headers.getValue("Authorization")
      if (auth != "Bearer $validToken") throw PodcastProblem("The server refused the sign-in.")
      this.headers += auth
      return when (path) {
        "/up_next/list" -> upNext
        "/user/in_progress" -> inProgress
        "/user/playlists" -> playlists.also { assertEquals("GET", method) }
        "/user/episode" -> {
          val uuid =
            Json.parseToJsonElement(body!!).jsonObject.getValue("uuid").jsonPrimitive.content
          episodeReads += uuid
          episodes.getValue(uuid)
        }
        else -> error("Unexpected $path")
      }
    }

    override suspend fun rss(url: String) = error("No feeds")

    override suspend fun prepare(episode: Episode, sections: List<Section>) {}

    override suspend fun available(section: Section) = false

    override suspend fun export(sections: List<Section>, target: String, manifest: String) {}
  }
}
