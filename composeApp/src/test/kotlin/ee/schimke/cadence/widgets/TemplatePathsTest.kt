package ee.schimke.cadence.widgets

import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcPathData
import ee.schimke.composeai.rcplayer.protocol.RcWireLimits
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Every bundled template's paths have something in them. Icons are captured as paths read from an
 * `android.graphics.Path`; a capture under Robolectric's legacy graphics, which stubs that class
 * out, writes each one empty and the widget draws its buttons without their icons.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TemplatePathsTest {
  private val catalog = File(requireNotNull(System.getProperty("cadence.livery.dir")))

  @Test
  fun noTemplateHasAnEmptyPath() {
    val templates = File(catalog, "designs").walk().filter { it.extension == "rc" }.toList()
    assertTrue(templates.isNotEmpty(), "no templates under $catalog")
    val empty = templates.flatMap { file ->
      RcDocumentCodec.decode(file.readBytes(), RcWireLimits())
        .operations
        .filterIsInstance<RcPathData>()
        .filter { it.words.isEmpty() }
        .map { "${file.relativeTo(catalog)}: path ${it.idAndWinding}" }
    }
    assertEquals(emptyList(), empty, "captured without native graphics?")
  }
}
