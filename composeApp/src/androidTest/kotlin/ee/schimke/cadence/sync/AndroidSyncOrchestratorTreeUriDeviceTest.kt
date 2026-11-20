package ee.schimke.cadence.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** Runs against the real Android DocumentsContract / DocumentFile implementation. */
@RunWith(AndroidJUnit4::class)
class AndroidSyncOrchestratorTreeUriDeviceTest {
  @Test
  fun opensOkioNormalizedContentTreeUri() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
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
    val context = InstrumentationRegistry.getInstrumentation().targetContext
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
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    assertNull(AndroidSyncOrchestrator.resolveDeviceTreeUri(context, ""))
    assertNull(
      AndroidSyncOrchestrator.resolveDeviceTreeUri(
        context,
        "content://com.android.externalstorage.documents/document/6072-C688%3A",
      ),
    )
  }
}
