package ee.schimke.cadence.sync.google

import ee.schimke.cadence.net.SharedHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Minimal Google Drive v3 client scoped to the hidden appDataFolder. Just enough to upload a
 * manifest JSON per device, download it back, and list what's there.
 *
 * [tokenProvider] returns a valid bearer token; it's called for every request and expected to
 * refresh itself when needed so we never retry on 401 here.
 */
class GoogleDriveClient(
  private val tokenProvider: suspend () -> String,
  private val http: HttpClient = SharedHttpClient.client,
) {
  private val json = Json { ignoreUnknownKeys = true }

  /** Replace or create [name] in the user's Drive appDataFolder with [content]. */
  suspend fun uploadOrReplace(name: String, content: String) {
    val existing = findByName(name)
    if (existing != null) replaceContent(existing, content) else createNew(name, content)
  }

  /** Fetch [name] from the appDataFolder, or null if it doesn't exist. */
  suspend fun downloadByName(name: String): String? {
    val existing = findByName(name) ?: return null
    val response =
      http.get("https://www.googleapis.com/drive/v3/files/$existing") {
        parameter("alt", "media")
        header(HttpHeaders.Authorization, "Bearer ${tokenProvider()}")
      }
    val code = response.status.value
    val text = response.bodyAsText()
    check(code == 200) { "Drive download failed ($code): $text" }
    return text
  }

  /** Returns the Drive file id for [name] in appDataFolder, or null if it doesn't exist. */
  private suspend fun findByName(name: String): String? {
    val query = "name = '${name.replace("'", "\\'")}' and trashed = false"
    val response =
      http.get("https://www.googleapis.com/drive/v3/files") {
        parameter("spaces", "appDataFolder")
        parameter("fields", "files(id,name)")
        parameter("q", query)
        header(HttpHeaders.Authorization, "Bearer ${tokenProvider()}")
      }
    val code = response.status.value
    val text = response.bodyAsText()
    check(code == 200) { "Drive list failed ($code): $text" }
    return json.decodeFromString<ListResponse>(text).files.firstOrNull()?.id
  }

  private suspend fun createNew(name: String, content: String) {
    val boundary = "cadence-boundary-${System.nanoTime()}"
    val metadata = """{"name":"${name.replace("\"", "\\\"")}","parents":["appDataFolder"]}"""
    val body =
      StringBuilder()
        .append("--")
        .append(boundary)
        .append("\r\n")
        .append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
        .append(metadata)
        .append("\r\n")
        .append("--")
        .append(boundary)
        .append("\r\n")
        .append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
        .append(content)
        .append("\r\n")
        .append("--")
        .append(boundary)
        .append("--")
        .toString()
    val response =
      http.post("https://www.googleapis.com/upload/drive/v3/files") {
        parameter("uploadType", "multipart")
        parameter("spaces", "appDataFolder")
        header(HttpHeaders.Authorization, "Bearer ${tokenProvider()}")
        contentType(ContentType.parse("multipart/related; boundary=$boundary"))
        setBody(body)
      }
    val code = response.status.value
    val text = response.bodyAsText()
    check(code in 200..299) { "Drive upload failed ($code): $text" }
  }

  private suspend fun replaceContent(fileId: String, content: String) {
    val response =
      http.patch("https://www.googleapis.com/upload/drive/v3/files/$fileId") {
        parameter("uploadType", "media")
        header(HttpHeaders.Authorization, "Bearer ${tokenProvider()}")
        contentType(ContentType.Application.Json)
        setBody(content)
      }
    val code = response.status.value
    val text = response.bodyAsText()
    check(code in 200..299) { "Drive patch failed ($code): $text" }
  }

  @Serializable private data class ListResponse(val files: List<DriveFile> = emptyList())

  @Serializable private data class DriveFile(val id: String, val name: String)
}
