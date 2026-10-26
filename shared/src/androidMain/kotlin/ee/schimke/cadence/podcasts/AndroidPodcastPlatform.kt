package ee.schimke.cadence.podcasts

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.*
import androidx.media3.transformer.Composition
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.documentfile.provider.DocumentFile
import ee.schimke.cadence.data.toUri
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Path.Companion.toPath

@Composable actual fun rememberPodcastPlatform(): PodcastPlatform {
  val context = LocalContext.current.applicationContext
  return remember(context) { AndroidPodcastPlatform(context) }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
open class AndroidPodcastPlatform(private val context: Context) : FilePodcastPlatform(File(context.filesDir, "podcasts")) {
  override val splitFormats = setOf("m4a")
  override val description = "Android: HTTPS downloads, Media3 AAC/M4A chunks and USB copying through the system document picker. The OS must expose the headphone volume. No raw USB block access. Keep the app open while syncing."

  override suspend fun rss(url: String) = parseAndroidRss(request(url), url)

  override suspend fun prepare(episode: Episode, sections: List<Section>) {
    val whole = sections.size == 1 && sections[0].startMs == 0L && sections[0].endMs == episode.durationMs
    if (sections.all { available(it) }) return
    if (whole) { download(episode, audio(sections.single())); return }
    require(sections.all { it.fileName.endsWith(".m4a") })
    val source = File(root, "source/${episode.key}-${digest(episode.audioVersion)}.${episode.extension}")
    download(episode, source)
    try {
      for (section in sections) {
        val output = audio(section)
        withContext(Dispatchers.IO) { output.parentFile!!.mkdirs() }
        val temp = File(output.parentFile, output.name + ".part.m4a")
        try {
          transform(source, section, temp)
          withContext(Dispatchers.IO) { require(temp.length() > 0); replacePodcastFile(temp, output) }
        } finally { withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { temp.delete() } }
      }
    } finally { withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { source.delete() } }
  }

  // Transformer must be created, started and cancelled on the same application looper.
  private suspend fun transform(source: File, section: Section, output: File) = withContext(Dispatchers.Main.immediate) {
    suspendCancellableCoroutine<Unit> { continuation ->
      val input = MediaItem.Builder().setUri(android.net.Uri.fromFile(source))
        .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder()
          .setStartPositionMs(section.startMs).setEndPositionMs(section.endMs).build()).build()
      val item = EditedMediaItem.Builder(input).setRemoveVideo(true).build()
      val transformer = Transformer.Builder(context).setAudioMimeType(MimeTypes.AUDIO_AAC)
        .addListener(object : Transformer.Listener {
          override fun onCompleted(composition: Composition, exportResult: ExportResult) {
            if (continuation.isActive) continuation.resume(Unit)
          }
          override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
            if (continuation.isActive) continuation.resumeWithException(exportException)
          }
        }).build()
      continuation.invokeOnCancellation { if (Looper.myLooper() == Looper.getMainLooper()) transformer.cancel() else Handler(Looper.getMainLooper()).post { transformer.cancel() } }
      try { transformer.start(item, output.path) }
      catch (error: Exception) { if (continuation.isActive) continuation.resumeWithException(error) }
    }
  }

  override suspend fun export(sections: List<Section>, target: String, manifest: String) = withContext(Dispatchers.IO) {
    val volume = requireNotNull(DocumentFile.fromTreeUri(context, target.toPath().toUri()))
    fun directory(parent: DocumentFile, name: String): DocumentFile =
      (parent.findFile(name) ?: parent.createDirectory(name))!!.also { require(it.isDirectory) }
    val folder = directory(directory(volume, "Cadence"), "podcasts")
    val marker = folder.findFile(".cadence-owner")
    if (marker == null) {
      require(folder.listFiles().isEmpty()) { "Existing folder is not owned by Cadence" }
      val created = requireNotNull(folder.createFile("application/octet-stream", ".cadence-owner"))
      requireNotNull(context.contentResolver.openOutputStream(created.uri, "wt")).use { it.write("cadence-podcasts-v1".toByteArray()) }
    } else {
      require(context.contentResolver.openInputStream(marker.uri)?.bufferedReader()?.use { it.readText() } == "cadence-podcasts-v1")
    }
    fun write(name: String, mime: String, writer: (java.io.OutputStream) -> Unit) {
      val targetFile = folder.findFile(name) ?: requireNotNull(folder.createFile(mime, name))
      require(targetFile.isFile)
      requireNotNull(context.contentResolver.openOutputStream(targetFile.uri, "wt")).use(writer)
    }
    // SAF does not promise atomic replace. A failed copy is retryable and never marks readiness copied.
    sections.forEach { section ->
      write(section.fileName, if (section.fileName.endsWith(".mp3")) "audio/mpeg" else "application/octet-stream") { output -> audio(section).inputStream().use { it.copyTo(output) } }
    }
    write("cadence-manifest.json", "application/json") { it.write(manifest.toByteArray()) }
  }

  override fun publishStatus(status: String, ready: Boolean) {
    context.getSharedPreferences("podcast-widget", Context.MODE_PRIVATE).edit().putString("status", status).putBoolean("ready", ready).apply()
    PodcastSyncWidget.updateAll(context)
  }
}
