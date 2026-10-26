package ee.schimke.cadence.podcasts

import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

/** Runs against the real Android codec and muxer stack, not Robolectric. */
class Media3ClippingTest {
  @Test
  fun clipsPcmInputToAacM4aWithOriginalTimelineMapping() = runBlocking {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val temp = File(context.cacheDir, "cadence-media3-test").apply { mkdirs() }
    try {
      val source = File(temp, "tone.wav")
      val samples = 44_100 * 2
      val wav = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN)
      wav.put("RIFF".toByteArray()).putInt(36 + samples * 2).put("WAVEfmt ".toByteArray())
      wav.putInt(16).putShort(1).putShort(1).putInt(44_100).putInt(88_200).putShort(2).putShort(16)
      wav.put("data".toByteArray()).putInt(samples * 2)
      repeat(samples) { wav.putShort((sin(2 * PI * 440 * it / 44_100) * 10_000).toInt().toShort()) }
      source.writeBytes(wav.array())
      val episode =
        Episode(
          "test",
          "media3",
          "Media3 tone",
          "https://example.com/tone.wav",
          2_000,
          1_000,
          "1",
          "v1",
          "wav",
        )
      val platform =
        object : AndroidPodcastPlatform(context) {
          override suspend fun download(episode: Episode, destination: File) {
            destination.parentFile!!.mkdirs()
            source.copyTo(destination, overwrite = true)
          }
        }
      val sections = segment(episode, Segmentation(chunkFormat = "m4a"))
      withTimeout(60_000) { platform.prepare(episode, sections) }
      assertEquals(listOf(0L to 1_000L, 1_000L to 2_000L), sections.map { it.startMs to it.endMs })
      for (section in sections) {
        assertTrue(platform.available(section))
        val output = File(context.filesDir, "podcasts/audio/${section.fileName}")
        val extractor = MediaExtractor()
        try {
          extractor.setDataSource(output.path)
          assertEquals(1, extractor.trackCount)
          val format = extractor.getTrackFormat(0)
          assertEquals("audio/mp4a-latm", format.getString(MediaFormat.KEY_MIME))
          assertTrue(format.getLong(MediaFormat.KEY_DURATION) in 900_000..1_100_000)
        } finally {
          extractor.release()
          output.delete()
        }
      }
    } finally {
      temp.deleteRecursively()
    }
  }
}
