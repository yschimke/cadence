package ee.schimke.cadence.podcasts

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/**
 * Libro.fm's private mobile-app API (as used by open-source library downloaders, 2026), not a
 * supported third-party API. It can change without notice; reads are parsed leniently and books
 * Cadence can't play are skipped.
 */
const val LIBRO_FM_API = "https://libro.fm"

const val LIBRO_FM_BACKEND = "libro-fm"

/** The whole library, newest purchase first; any other list id is one book's ISBN. */
const val LIBRO_FM_LIBRARY = "library"

/**
 * Requests that don't look like the Android app are refused at Libro.fm's load balancer with an
 * empty 401. These track a current app build and may need refreshing when that gate tightens.
 */
private val libroFmAppHeaders =
  mapOf(
    "Content-Type" to "application/json",
    "User-Agent" to "okhttp/5.3.2",
    "X-LibroFm-AppVer" to "7.34.8",
  )

/** The bearer token, kept with the local library so later imports don't ask again. */
@Serializable
data class LibroFmSession(val accessToken: String = "", val email: String = "") {
  val signedIn
    get() = accessToken.isNotBlank()
}

data class LibroFmBook(
  val isbn: String,
  val title: String,
  val authors: List<String>,
  val narrators: List<String>,
  val durationSeconds: Long,
  val coverUrl: String,
  val published: String,
  val description: String,
)

private fun JsonObject.text(name: String) = this[name]?.jsonPrimitive?.contentOrNull.orEmpty()

private fun JsonObject.texts(name: String) =
  (this[name] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }

private fun libroFmObject(body: String): JsonObject =
  runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
    ?: throw PodcastProblem("Libro.fm returned an unexpected response")

/** `POST /oauth/token` answers `{access_token}`; the token lasts until a 401. */
fun libroFmSession(body: String, email: String): LibroFmSession {
  val token = libroFmObject(body).text("access_token")
  if (token.isBlank()) throw PodcastProblem("Libro.fm didn't return a sign-in token")
  return LibroFmSession(token, email)
}

private fun libroFmBook(node: JsonObject): LibroFmBook {
  val info = node["audiobook_info"] as? JsonObject
  return LibroFmBook(
    node.text("isbn"),
    node.text("title"),
    node.texts("authors"),
    info?.texts("narrators").orEmpty(),
    info?.get("duration")?.jsonPrimitive?.longOrNull ?: 0,
    node.text("cover_url"),
    node.text("publication_date"),
    node.text("description"),
  )
}

/** One page of `GET /api/v10/library`: its books and how many pages there are. */
fun libroFmLibraryPage(body: String): Pair<List<LibroFmBook>, Int> {
  val json = libroFmObject(body)
  val books =
    (json["audiobooks"] as? JsonArray).orEmpty().mapNotNull {
      (it as? JsonObject)?.let(::libroFmBook)
    }
  return books to (json["total_pages"]?.jsonPrimitive?.intOrNull ?: 1)
}

/** `GET /api/v10/audiobooks/{isbn}/packaged_m4b` answers `{m4b_url}`, a short-lived signed URL. */
fun libroFmM4bUrl(body: String): String =
  libroFmObject(body).text("m4b_url").takeIf { it.startsWith("https://") }
    ?: throw PodcastProblem("Libro.fm didn't return a download for this book")

/** The ISBN Cadence keeps in [Episode.id] for a Libro.fm book. */
private val isbnPattern = Regex("[0-9X]{10,13}")

/**
 * The Cadence episode for a book. Libro.fm's download links expire within minutes, so
 * [Episode. audioUrl] is the book's page and the controller asks for a fresh link right before
 * downloading. Libro.fm keeps no listening position Cadence can read, so books start at the
 * beginning.
 */
fun libroFmToEpisode(book: LibroFmBook): Episode? {
  if (!book.isbn.matches(isbnPattern) || book.durationSeconds <= 0) return null
  val durationMs = book.durationSeconds * 1000
  return Episode(
      LIBRO_FM_BACKEND,
      book.isbn,
      book.title.ifBlank { "Audiobook" },
      "$LIBRO_FM_API/audiobooks/${book.isbn}",
      durationMs,
      0,
      "librofm:0",
      // The packaged M4B of this edition; a re-mastered edition comes with a new length.
      digest("$LIBRO_FM_BACKEND|${book.isbn}|m4b|$durationMs"),
      // An M4B is an M4A with chapters; players and the encoder treat it as one.
      "m4a",
      podcastId = "$LIBRO_FM_BACKEND:${book.isbn}",
      podcastTitle = book.authors.joinToString(", "),
      summary =
        listOfNotNull(
            book.narrators
              .takeIf { it.isNotEmpty() }
              ?.let { "Narrated by ${it.joinToString(", ")}" },
            book.description.takeIf { it.isNotBlank() },
          )
          .joinToString("\n\n"),
      published = book.published,
      artwork = book.coverUrl.takeIf { it.startsWith("https://") }.orEmpty(),
    )
    .takeIf { runCatching { it.validate() }.isSuccess }
}

/** A book or the whole library, read from [books]; the second value counts skipped books. */
fun libroFmSnapshot(books: List<LibroFmBook>, list: String): Pair<PlaylistSnapshot, Int> {
  val chosen =
    if (list == LIBRO_FM_LIBRARY) books
    else
      listOf(
        books.firstOrNull { it.isbn == list }
          ?: throw PodcastProblem("That book is no longer in your Libro.fm library"),
      )
  val episodes = chosen.distinctBy { it.isbn }.mapNotNull(::libroFmToEpisode)
  if (episodes.isEmpty() && chosen.isNotEmpty())
    throw PodcastProblem(
      "Libro.fm didn't give a length for ${chosen.singleOrNull()?.title ?: "any of these books"}",
    )
  val name = if (list == LIBRO_FM_LIBRARY) "Libro.fm library" else episodes.single().title
  return PlaylistSnapshot(name = name, episodes = episodes) to chosen.size - episodes.size
}

/** Calls the API with the bearer token; [onSignedOut] runs when Libro.fm refuses it. */
class LibroFmApi(
  private val platform: PodcastPlatform,
  private val session: LibroFmSession,
  private val onSignedOut: suspend () -> Unit,
) {
  private suspend fun get(path: String): String {
    if (!session.signedIn) throw PodcastProblem("Sign in to Libro.fm first")
    return try {
      platform.request(
        LIBRO_FM_API + path,
        headers = libroFmAppHeaders + ("Authorization" to "Bearer ${session.accessToken}"),
      )
    } catch (e: PodcastProblem) {
      if (e.status != 401) throw e
      onSignedOut()
      throw PodcastProblem("Your Libro.fm sign-in expired. Sign in again.", e.status)
    }
  }

  /** Every book in the library, in the order Libro.fm lists them (newest purchase first). */
  suspend fun library(): List<LibroFmBook> = buildList {
    var page = 1
    var pages = 1
    while (page <= minOf(pages, MAX_LIBRARY_PAGES)) {
      val (books, total) = libroFmLibraryPage(get("/api/v10/library?page=$page"))
      addAll(books)
      pages = total
      page++
    }
  }

  /** A fresh signed link to the book's M4B; not every book has one packaged. */
  suspend fun m4bUrl(isbn: String): String =
    try {
      libroFmM4bUrl(get("/api/v10/audiobooks/$isbn/packaged_m4b"))
    } catch (e: PodcastProblem) {
      if (e.status != 404) throw e
      throw PodcastProblem("Libro.fm has no single-file (M4B) download for this book yet", 404)
    }

  companion object {
    private const val MAX_LIBRARY_PAGES = 50

    suspend fun signIn(platform: PodcastPlatform, email: String, password: String): LibroFmSession {
      val body = buildJsonObject {
        put("grant_type", "password")
        put("username", email)
        put("password", password)
      }
      val response =
        try {
          platform.request(
            "$LIBRO_FM_API/oauth/token",
            "POST",
            libroFmAppHeaders,
            body.toString(),
          )
        } catch (e: PodcastProblem) {
          if (e.status !in listOf(400, 401)) throw e
          throw PodcastProblem(
            "Libro.fm refused the sign-in. Check your email and password.",
            e.status,
          )
        }
      return libroFmSession(response, email)
    }
  }
}
