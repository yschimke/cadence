package ee.schimke.cadence.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidSyncOrchestratorTreeUriTest {
  @Test
  fun opensOkioNormalizedContentTreeUri() {
    val context = RuntimeEnvironment.getApplication()
    val uri =
      AndroidSyncOrchestrator.resolveDeviceTreeUri(
        context,
        "content:/com.android.externalstorage.documents/tree/6072-C688%3A",
      )
    assertNotNull(uri)
    assertEquals(
      "content://com.android.externalstorage.documents/tree/6072-C688%3A/document/6072-C688%3A",
      uri.toString(),
    )
  }

  @Test
  fun opensStandardContentTreeUri() {
    val context = RuntimeEnvironment.getApplication()
    val uri =
      AndroidSyncOrchestrator.resolveDeviceTreeUri(
        context,
        "content://com.android.externalstorage.documents/tree/6072-C688%3A",
      )
    assertNotNull(uri)
    assertEquals(
      "content://com.android.externalstorage.documents/tree/6072-C688%3A/document/6072-C688%3A",
      uri.toString(),
    )
  }

  @Test
  fun returnsNullInsteadOfThrowingForInvalidTreeUri() {
    val context = RuntimeEnvironment.getApplication()
    assertNull(AndroidSyncOrchestrator.resolveDeviceTreeUri(context, ""))
    assertNull(
      AndroidSyncOrchestrator.resolveDeviceTreeUri(
        context,
        "content://com.android.externalstorage.documents/document/6072-C688%3A",
      ),
    )
  }
}
