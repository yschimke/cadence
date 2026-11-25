@file:Suppress("RestrictedApi", "RestrictedApiAndroidX")

package ee.schimke.cadence.widgets

import androidx.compose.runtime.CompositionLocalProvider
import ee.schimke.cadence.podcasts.StatusCardTemplate
import ee.schimke.cadence.podcasts.StatusCardTemplateContent
import ee.schimke.cadence.swim.LocalSwimClockStart
import ee.schimke.composeai.rcwidget.creation.rcWidgetHostAction
import ee.schimke.livery.capture.CapturedTemplate
import ee.schimke.livery.capture.DesignFolder
import ee.schimke.livery.capture.cmp.LiveryCapture
import ee.schimke.livery.protocol.SizeRange
import ee.schimke.livery.protocol.Surfaces
import ee.schimke.livery.protocol.Target
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Captures Home's status card designs (contract [StatusCardTemplate.CONTRACT]) and checks they are
 * the ones committed in `composeApp/livery/designs/<design>/`; run with
 * `-Pcadence.livery.regenerate=true` to write them instead, after changing the card's drawing.
 *
 * [StatusCardPlatformTest] draws the committed template with each of the contract's examples.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "mdpi")
class StatusCardTemplatesTest {
  private val catalog =
    File(requireNotNull(System.getProperty("cadence.livery.dir")) { "no cadence.livery.dir" })
  private val regenerate = System.getProperty("cadence.livery.regenerate") == "true"
  private val design = File(catalog, "designs/${StatusCardTemplate.SWIMMING_DESIGN}")

  @Test
  fun templatesAreCurrent() = runBlocking {
    val templates = mapOf(TEMPLATE to capture())
    val folder = DesignFolder(design)
    if (regenerate) {
      folder.write(templates)
      println("Livery: wrote ${templates.size} status card templates to $design")
    } else {
      assertEquals(emptyList(), folder.check(templates), STALE)
    }
  }

  private suspend fun capture(): CapturedTemplate =
    LiveryCapture.capture(
      Target(
        Surfaces.IN_APP,
        rcApiLevel = 7,
        profiles = listOf("widgets"),
        size =
          SizeRange(StatusCardTemplate.MIN_WIDTH_DP, null, StatusCardTemplate.MIN_HEIGHT_DP, null),
      ),
      StatusCardTemplate.MIN_WIDTH_DP,
      StatusCardTemplate.MIN_HEIGHT_DP,
      source = mapOf("composable" to "StatusCardTemplateContent"),
    ) {
      // The pool keeps the live swim clock, so the card animates as it plays; only the capture's
      // own start is pinned, so the committed template never changes with the time it was made.
      CompositionLocalProvider(LocalSwimClockStart provides 0f) {
        StatusCardTemplateContent(onAction = { rcWidgetHostAction(name = it, payload = it) })
      }
    }

  private companion object {
    const val TEMPLATE = "in-app"
    const val STALE =
      "the committed status card templates are stale: run ./gradlew :composeApp:testDebugUnitTest " +
        "--tests '*StatusCardTemplatesTest*' -Pcadence.livery.regenerate=true"
  }
}
