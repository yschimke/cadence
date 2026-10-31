package ee.schimke.cadence.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class WearMainActivity : ComponentActivity() {
  private var showSettings by mutableStateOf(false)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    showSettings = intent.openSettings
    setContent {
      WearCompanionApp(
        gateway = WearCompanionServices.gateway(applicationContext),
        showSettings = showSettings,
        onDismissSettings = { showSettings = false },
      )
    }
  }

  override fun onNewIntent(intent: android.content.Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    showSettings = intent.openSettings
  }

  private val android.content.Intent.openSettings: Boolean
    get() = getBooleanExtra(ExtraOpenSettings, false)

  companion object {
    const val ExtraOpenSettings = "ee.schimke.cadence.wear.OPEN_SETTINGS"
  }
}
