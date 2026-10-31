package ee.schimke.cadence.sync.google

import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
  private val http: HttpClient = HttpClient.newHttpClient(),
) {
  private val json = Json { ignoreUnknownKeys = true }

  /** Replace or create [name] in the user's Drive appDataFolder with [content]. */
  suspend fun uploadOrReplace(name: String, content: String) {
    withContext(Dispatchers.IO) {
      val existing = findByName(name)
      if (existing != null) replaceContent(existing, content) else createNew(name, content)
    }
  }

  /** Fetch [name] from the appDataFolder, or null if it doesn't exist. */
  suspend fun downloadByName(name: String): String? =
    withContext(Dispatchers.IO) {
      val existing = findByName(name) ?: return@withContext null
      val response =
        http.send(
          HttpRequest.newBuilder(
              URI.create("https://www.googleapis.com/drive/v3/files/$existing?alt=media")
            )
            .header("Authorization", "Bearer ${tokenProvider()}")
            .GET()
            .build(),
          HttpResponse.BodyHandlers.ofString(),
        )
      check(response.statusCode() == 200) {
        "Drive download failed (${response.statusCode()}): ${response.body()}"
      }
      response.body()
    }

  /** Returns the Drive file id for [name] in appDataFolder, or null if it doesn't exist. */
  private suspend fun findByName(name: String): String? {
    val query = "name = '${name.replace("'", "\\'")}' and trashed = false"
    val url =
      "https://www.googleapis.com/drive/v3/files" +
        "?spaces=appDataFolder" +
        "&fields=files(id,name)" +
        "&q=" +
        URLEncoder.encode(query, StandardCharsets.UTF_8)
    val response =
      http.send(
        HttpRequest.newBuilder(URI.create(url))
          .header("Authorization", "Bearer ${tokenProvider()}")
          .GET()
          .build(),
        HttpResponse.BodyHandlers.ofString(),
      )
    check(response.statusCode() == 200) {
      "Drive list failed (${response.statusCode()}): ${response.body()}"
    }
    return json.decodeFromString<ListResponse>(response.body()).files.firstOrNull()?.id
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
      http.send(
        HttpRequest.newBuilder(
            URI.create(
              "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&spaces=appDataFolder"
            )
          )
          .header("Authorization", "Bearer ${tokenProvider()}")
          .header("Content-Type", "multipart/related; boundary=$boundary")
          .POST(HttpRequest.BodyPublishers.ofString(body))
          .build(),
        HttpResponse.BodyHandlers.ofString(),
      )
    check(response.statusCode() in 200..299) {
      "Drive upload failed (${response.statusCode()}): ${response.body()}"
    }
  }

  private suspend fun replaceContent(fileId: String, content: String) {
    val response =
      http.send(
        HttpRequest.newBuilder(
            URI.create("https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media")
          )
          .header("Authorization", "Bearer ${tokenProvider()}")
          .header("Content-Type", "application/json; charset=UTF-8")
          .method("PATCH", HttpRequest.BodyPublishers.ofString(content))
          .build(),
        HttpResponse.BodyHandlers.ofString(),
      )
    check(response.statusCode() in 200..299) {
      "Drive patch failed (${response.statusCode()}): ${response.body()}"
    }
  }

  @Serializable private data class ListResponse(val files: List<DriveFile> = emptyList())

  @Serializable private data class DriveFile(val id: String, val name: String)
}
