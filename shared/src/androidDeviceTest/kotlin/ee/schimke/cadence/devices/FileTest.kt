package ee.schimke.cadence.devices

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FileTest {
  @Test
  @Ignore
  fun listFiles() {
    val f = File("/mnt/media_rw").listFiles()

    println(f?.joinToString())
  }
}
