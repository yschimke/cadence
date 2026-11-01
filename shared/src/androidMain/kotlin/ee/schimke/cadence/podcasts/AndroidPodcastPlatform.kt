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
  return remember(context) { AndroidPodcastPlatform.instance(context) }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
open class AndroidPodcastPlatform(private val context: Context) : FilePodcastPlatform(File(context.filesDir, "podcasts")) {
  private val sharedController by lazy { PodcastController(this) }
  override fun controller(): PodcastController = sharedController

  companion object {
    @Volatile private var singleton: AndroidPodcastPlatform? = null
    fun instance(context: Context): AndroidPodcastPlatform = singleton ?: synchronized(this) {
      singleton ?: AndroidPodcastPlatform(context.applicationContext).also { singleton = it }
    }
  }
  override val splitFormats = setOf("m4a")
  fun originalForPlayback(episode: Episode): File? = source(episode).takeIf { it.isFile && it.length() > 0 }
  override val description = "Android: HTTPS downloads, Media3 AAC/M4A chunks and USB copying through the system document picker. The OS must expose the headphone volume. No raw USB block access. Keep the app open while syncing."

  override suspend fun rss(url: String) = parseAndroidRss(feedText(url))

  override suspend fun prepare(episode: Episode, sections: List<Section>) {
    val whole = sections.size == 1 && sections[0].startMs == 0L && sections[0].endMs == episode.durationMs
    if (sections.all { available(it) }) return
    if (whole) { copyWhole(episode, sections.single()); return }
    require(sections.all { it.fileName.endsWith(".m4a") })
    val source = cachedSource(episode)
      for (section in sections) {
        // A stopped preparation can resume without encoding completed chunks again.
        if (available(section)) continue
        val output = audio(section)
        withContext(Dispatchers.IO) { output.parentFile!!.mkdirs() }
        val temp = File(output.parentFile, output.name + ".part.m4a")
        try {
          transform(source, section, temp)
          withContext(Dispatchers.IO) { require(temp.length() > 0); replacePodcastFile(temp, output) }
        } finally { withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { temp.delete() } }
      }
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
    fun write(parent: DocumentFile, name: String, mime: String, writer: (java.io.OutputStream) -> Unit) {
      val targetFile = parent.findFile(name) ?: requireNotNull(parent.createFile(mime, name))
      require(targetFile.isFile)
      requireNotNull(context.contentResolver.openOutputStream(targetFile.uri, "wt")).use(writer)
    }
    // SAF does not promise atomic replace. A failed copy is retryable and never marks readiness copied.
    val folders = podcastExportFolders(sections, manifest)
    val destinations = mutableMapOf<String, DocumentFile>()
    sections.forEach { section ->
      val show = folders[section.fileName]
      val destination = if (show == null) folder else destinations.getOrPut(show) {
        val dir = directory(folder, show)
        val owner = dir.findFile(".cadence-owner")
        if (owner == null) {
          require(dir.listFiles().isEmpty()) { "Existing podcast folder is not owned by Cadence" }
          val created = requireNotNull(dir.createFile("application/octet-stream", ".cadence-owner"))
          requireNotNull(context.contentResolver.openOutputStream(created.uri, "wt")).use { it.write("cadence-podcasts-v1".toByteArray()) }
        } else {
          require(context.contentResolver.openInputStream(owner.uri)?.bufferedReader()?.use { it.readText() } == "cadence-podcasts-v1")
        }
        dir
      }
      write(destination, section.fileName, if (section.fileName.endsWith(".mp3")) "audio/mpeg" else "application/octet-stream") { output -> audio(section).inputStream().use { it.copyTo(output) } }
    }
    write(folder, "cadence-manifest.json", "application/json") { it.write(manifest.toByteArray()) }
  }

  override suspend fun readDeviceManifest(target: String): DeviceManifest? = withContext(Dispatchers.IO) {
    runCatching {
      val root = DocumentFile.fromTreeUri(context, target.toPath().toUri()) ?: return@runCatching null
      val file = root.findFile("Cadence")?.findFile("podcasts")?.findFile("cadence-manifest.json") ?: return@runCatching null
      val json = context.contentResolver.openInputStream(file.uri)?.bufferedReader()?.use { it.readText() } ?: return@runCatching null
      podcastJson.decodeFromString<DeviceManifest>(json).takeIf { it.schema == 1 }
    }.getOrNull()
  }

  override fun publishStatus(status: String, state: LocalPodcastState) {
    val summary = podcastWidgetSummary(state)
    context.getSharedPreferences("podcast-widget", Context.MODE_PRIVATE).edit().putString("status", status).putBoolean("ready", state.readyForDevice()).putString("copied", summary.copied).putString("available", summary.available).putString("target", state.target).apply()
    PodcastSyncWidget.updateAll(context)
    CloudSyncWidget.publish(context, state, sharedController.canRefresh, sharedController.busy.value, status)
  }
}
