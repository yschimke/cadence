package ee.schimke.cadence.sync.google

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Minimal Google Drive v3 client scoped to the hidden appDataFolder. Just enough to upload a
 * manifest JSON per device, download it back, and list what's there.
 *
 * [tokenProvider] returns a valid bearer token; it's called for every request and expected to
 * refresh itself when needed so we never retry on 401 here.
 *
 * OkHttp rather than java.net.http.HttpClient: Android doesn't ship the latter, so R8 blows up at
 * minify time with "Missing class java.net.http.HttpClient" even though the JVM-side build compiles
 * fine.
 */
class GoogleDriveClient(private val tokenProvider: suspend () -> String) {
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
      val request =
        Request.Builder()
          .url("https://www.googleapis.com/drive/v3/files/$existing?alt=media")
          .header("Authorization", "Bearer ${tokenProvider()}")
          .get()
          .build()
      val (code, text) = sharedHttp.newCall(request).executeAsText()
      check(code == 200) { "Drive download failed ($code): $text" }
      text
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
    val request =
      Request.Builder().url(url).header("Authorization", "Bearer ${tokenProvider()}").get().build()
    val (code, text) = sharedHttp.newCall(request).executeAsText()
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
    val request =
      Request.Builder()
        .url(
          "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&spaces=appDataFolder"
        )
        .header("Authorization", "Bearer ${tokenProvider()}")
        .post(body.toRequestBody("multipart/related; boundary=$boundary".toMediaType()))
        .build()
    val (code, text) = sharedHttp.newCall(request).executeAsText()
    check(code in 200..299) { "Drive upload failed ($code): $text" }
  }

  private suspend fun replaceContent(fileId: String, content: String) {
    val request =
      Request.Builder()
        .url("https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media")
        .header("Authorization", "Bearer ${tokenProvider()}")
        .patch(content.toRequestBody(JSON_UTF8))
        .build()
    val (code, text) = sharedHttp.newCall(request).executeAsText()
    check(code in 200..299) { "Drive patch failed ($code): $text" }
  }

  @Serializable private data class ListResponse(val files: List<DriveFile> = emptyList())

  @Serializable private data class DriveFile(val id: String, val name: String)
}
