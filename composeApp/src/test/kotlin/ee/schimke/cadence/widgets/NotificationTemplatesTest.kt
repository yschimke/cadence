@file:Suppress("RestrictedApi", "RestrictedApiAndroidX")

package ee.schimke.cadence.widgets

import ee.schimke.cadence.notifications.NotificationTemplate
import ee.schimke.cadence.notifications.NotificationTemplateContent
import ee.schimke.composeai.rcwidget.creation.rcWidgetHostAction
import ee.schimke.livery.capture.DesignFolder
import ee.schimke.livery.capture.LiveryCapture
import ee.schimke.livery.client.BindResult
import ee.schimke.livery.client.ContractBinder
import ee.schimke.livery.instantiate.InstantiateResult
import ee.schimke.livery.instantiate.TemplateInstantiator
import ee.schimke.livery.notifications.LiveryNotifications
import ee.schimke.livery.protocol.Contract
import ee.schimke.livery.protocol.SizeRange
import ee.schimke.livery.protocol.Surfaces
import ee.schimke.livery.protocol.Target
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Captures the notification body's Livery template ([NotificationTemplateContent]) and checks it is
 * the one committed in `composeApp/livery/designs/notification-default/`. Run with
 * `-Pcadence.livery.regenerate=true` to write it instead, after changing the drawing.
 */
@RunWith(RobolectricTestRunner::class)
// Native graphics: icons are drawn as paths read from an android.graphics.Path, which the legacy
// mode stubs out, so every icon was captured empty.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "mdpi")
class NotificationTemplatesTest {
  private val catalog =
    File(requireNotNull(System.getProperty("cadence.livery.dir")) { "no cadence.livery.dir" })
  private val regenerate = System.getProperty("cadence.livery.regenerate") == "true"
  private val design = File(catalog, "designs/notification-default")

  @Test
  fun templateIsCurrent() = runBlocking {
    val t = NotificationTemplate
    val name = "notification-w${t.MIN_WIDTH_DP}-h${t.MIN_HEIGHT_DP}"
    val captured =
      LiveryCapture.capture(
        RuntimeEnvironment.getApplication(),
        Target(
          Surfaces.NOTIFICATION,
          rcApiLevel = 7,
          profiles = listOf("widgets"),
          size = SizeRange(t.MIN_WIDTH_DP, null, t.MIN_HEIGHT_DP, t.MAX_HEIGHT_DP),
        ),
        t.CAPTURE_WIDTH_DP,
        t.CAPTURE_HEIGHT_DP,
        source = mapOf("composable" to "NotificationTemplateContent"),
      ) {
        NotificationTemplateContent(
          onOpen = rcWidgetHostAction(name = t.OPEN, payload = t.OPEN),
          onCancel = rcWidgetHostAction(name = t.CANCEL, payload = t.CANCEL),
        )
      }
    val folder = DesignFolder(design)
    if (regenerate) {
      folder.write(mapOf(name to captured))
      println("Livery: wrote the notification template to $design")
    } else {
      assertEquals(emptyList(), folder.check(mapOf(name to captured)), STALE)
    }
  }

  /** Each of the contract's examples, bound and instantiated as SystemUI is handed it. */
  @Test
  fun everyExampleInstantiatesInsideTheNotificationBudget() = runBlocking {
    val json = Json { ignoreUnknownKeys = true }
    val contract =
      json.decodeFromString(
        Contract.serializer(),
        File(catalog, "contracts/notification.json").readText(),
      )
    val instantiator = TemplateInstantiator { _, _, _ -> null }
    val templates = design.listFiles().orEmpty().filter { it.extension == "rc" }
    assertTrue(templates.isNotEmpty(), "no captured templates")
    for (rc in templates) {
      for ((name, example) in contract.examples) {
        val bindings = (ContractBinder.bind(contract, example) as BindResult.Ok).bindings
        val out =
          instantiator.instantiate(
            rc.readBytes(),
            bindings,
            600,
            400,
            maxBytes = LiveryNotifications.DEFAULT_MAX_BYTES,
            numericActions = true,
          )
        assertTrue(out is InstantiateResult.Ok, "${rc.name} with '$name': $out")
      }
    }
  }

  private companion object {
    const val STALE =
      "the committed notification template is stale: run ./gradlew :composeApp:testDebugUnitTest " +
        "--tests '*NotificationTemplatesTest*' -Pcadence.livery.regenerate=true"
  }
}
