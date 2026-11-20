package ee.schimke.cadence.devices

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.provider.Settings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DevicesEjectTest {
  @Test
  fun opensInternalStorageSettingsWhenAvailable() {
    val app = RuntimeEnvironment.getApplication()
    shadowOf(app).checkActivities(true)
    registerSettingsActivity(app, ".StorageSettings", Settings.ACTION_INTERNAL_STORAGE_SETTINGS)

    assertTrue(openStorageSettingsForEject(app))
    val started = shadowOf(app).nextStartedActivity
    assertEquals(Settings.ACTION_INTERNAL_STORAGE_SETTINGS, started.action)
  }

  @Test
  fun fallsBackToMemoryCardSettingsWhenInternalStorageUnavailable() {
    val app = RuntimeEnvironment.getApplication()
    shadowOf(app).checkActivities(true)
    registerSettingsActivity(app, ".MemoryCardSettings", Settings.ACTION_MEMORY_CARD_SETTINGS)

    assertTrue(openStorageSettingsForEject(app))
    val started = shadowOf(app).nextStartedActivity
    assertEquals(Settings.ACTION_MEMORY_CARD_SETTINGS, started.action)
  }

  @Test
  fun returnsFalseWhenNoSettingsActivityHandlesIntent() {
    val app = RuntimeEnvironment.getApplication()
    shadowOf(app).checkActivities(true)
    assertFalse(openStorageSettingsForEject(app))
  }

  private fun registerSettingsActivity(
    app: android.app.Application,
    className: String,
    action: String,
  ) {
    val pm = shadowOf(app.packageManager)
    val component = ComponentName("com.android.settings", className)
    pm.addActivityIfNotPresent(component)
    pm.addIntentFilterForActivity(
      component,
      IntentFilter(action).apply { addCategory(Intent.CATEGORY_DEFAULT) },
    )
  }
}
