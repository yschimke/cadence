package ee.schimke.cadence.widgets

import ee.schimke.composeai.rcplayer.protocol.RcAccessibilitySemantics
import ee.schimke.composeai.rcplayer.protocol.RcClickModifier
import ee.schimke.composeai.rcplayer.protocol.RcDocumentCodec
import ee.schimke.composeai.rcplayer.protocol.RcWireLimits
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Every tap in a bundled template reads as a button to accessibility services. rc-players'
 * `clickable` writes no accessibility semantics of its own (AndroidX's alpha23 wrote a button role
 * for every click), so Cadence's one `cadenceClickable` modifier writes them: an
 * [RcAccessibilitySemantics] with a role, right before each [RcClickModifier].
 *
 * That the templates hold only operations their launcher profile reads needs no test of its own:
 * [HeadphoneTemplatesTest] and [NotificationTemplatesTest] capture them under that profile, and the
 * writer refuses anything outside it.
 */
// Robolectric only for the codec's tracing, which reads Android's Trace.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ClickableRoleTest {
  private val catalog = File(requireNotNull(System.getProperty("cadence.livery.dir")))

  @Test
  fun everyClickIsAButton() {
    val templates = File(catalog, "designs").walk().filter { it.extension == "rc" }.toList()
    assertTrue(templates.isNotEmpty(), "no templates under $catalog")
    var clicks = 0
    val unlabelled = mutableListOf<String>()
    for (file in templates) {
      val operations = RcDocumentCodec.decode(file.readBytes(), RcWireLimits()).operations
      operations.forEachIndexed { index, operation ->
        if (operation !is RcClickModifier) return@forEachIndexed
        clicks++
        val semantics = operations.getOrNull(index - 1) as? RcAccessibilitySemantics
        if (semantics == null || semantics.role < 0) {
          unlabelled += "${file.relativeTo(catalog)}: click at operation $index"
        }
      }
    }
    assertTrue(clicks > 0, "no template has a tap")
    assertEquals(emptyList(), unlabelled, "taps with no button role")
  }
}
