package ee.schimke.cadence.podcasts

import androidx.compose.runtime.*
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardCopyOption.*
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
actual fun rememberPodcastPlatform(): PodcastPlatform = remember {
  DesktopPodcastPlatform(File(System.getProperty("user.home"), ".cadence/podcasts"))
}

open class DesktopPodcastPlatform(root: File) : FilePodcastPlatform(root) {
  override val splitFormats = setOf("mp3", "m4a")
  override val description =
    "Desktop: HTTPS audio downloads and FFmpeg MP3 or AAC/M4A segmentation. Select the OS-mounted headphone volume for USB copying. FFmpeg must be installed separately."

  override suspend fun prepare(episode: Episode, sections: List<Section>) =
    withContext(Dispatchers.IO) {
      if (sections.all { available(it) }) return@withContext
      val whole =
        sections.size == 1 && sections[0].startMs == 0L && sections[0].endMs == episode.durationMs
      if (whole) {
        download(episode, audio(sections.single()))
        return@withContext
      }

      try {
        val probe =
          ProcessBuilder("ffmpeg", "-version")
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
        check(probe.waitFor(10, TimeUnit.SECONDS) && probe.exitValue() == 0)
      } catch (_: Exception) {
        throw PodcastProblem("Install FFmpeg on PATH to prepare chunks, or choose unsplit audio.")
      }
      val source = File(root, "source/${episode.key}-${digest(episode.audioVersion)}.mp3")
      download(episode, source)
      try {
        sections.forEach { section ->
          val output = audio(section)
          output.parentFile!!.mkdirs()
          val format = section.fileName.substringAfterLast('.')
          val temp = File(output.parentFile, output.name + ".part." + format)
          try {
            val process =
              ProcessBuilder(
                  "ffmpeg",
                  "-nostdin",
                  "-v",
                  "error",
                  "-y",
                  "-i",
                  source.path,
                  "-ss",
                  (section.startMs / 1000.0).toString(),
                  "-t",
                  ((section.endMs - section.startMs) / 1000.0).toString(),
                  "-map",
                  "0:a:0",
                  "-vn",
                  "-map_metadata",
                  "-1",
                  "-codec:a",
                  if (format == "mp3") "libmp3lame" else "aac",
                  "-b:a",
                  "128k",
                  temp.path,
                )
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start()
            try {
              check(process.waitFor(30, TimeUnit.MINUTES) && process.exitValue() == 0)
            } finally {
              if (process.isAlive) process.destroyForcibly()
            }
            require(temp.length() > 0)
            replacePodcastFile(temp, output)
          } finally {
            temp.delete()
          }
        }
      } finally {
        source.delete()
      }
    }

  override suspend fun export(sections: List<Section>, target: String, manifest: String) =
    withContext(Dispatchers.IO) {
      val volume = File(target).toPath().toRealPath()
      require(Files.isDirectory(volume))
      var path = volume
      for (name in listOf("Cadence", "podcasts")) {
        path = path.resolve(name)
        if (!Files.exists(path, NOFOLLOW_LINKS)) Files.createDirectory(path)
        require(!Files.isSymbolicLink(path) && Files.isDirectory(path, NOFOLLOW_LINKS))
      }
      val marker = path.resolve(".cadence-owner")
      if (!Files.exists(marker, NOFOLLOW_LINKS)) {
        Files.list(path).use {
          require(!it.findAny().isPresent) { "Existing folder is not owned by Cadence" }
        }
        Files.writeString(
          marker,
          "cadence-podcasts-v1",
          java.nio.file.StandardOpenOption.CREATE_NEW,
        )
      }
      require(!Files.isSymbolicLink(marker) && Files.readString(marker) == "cadence-podcasts-v1")
      fun commit(name: String, write: (java.nio.file.Path) -> Unit) {
        val destination = path.resolve(name)
        require(!Files.isSymbolicLink(destination))
        val temp = Files.createTempFile(path, ".cadence-", ".tmp")
        try {
          write(temp)
          Files.move(
            temp,
            destination,
            REPLACE_EXISTING,
          ) // FAT volumes may not support ATOMIC_MOVE.
        } finally {
          Files.deleteIfExists(temp)
        }
      }
      sections.forEach { section ->
        commit(section.fileName) { Files.copy(audio(section).toPath(), it, REPLACE_EXISTING) }
      }
      // Commit manifest last; a failed transfer never advertises a completed new manifest.
      commit("cadence-manifest.json") { Files.writeString(it, manifest) }
    }
}
