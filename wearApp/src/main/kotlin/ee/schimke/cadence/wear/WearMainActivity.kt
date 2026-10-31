package ee.schimke.cadence.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

class WearMainActivity : ComponentActivity() {
  private var startPage by mutableIntStateOf(PlayerPage)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    startPage = intent.startPage
    setContent { WearCompanionApp(WearCompanionServices.gateway(applicationContext), startPage) }
  }

  override fun onNewIntent(intent: android.content.Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    startPage = intent.startPage
  }

  private val android.content.Intent.startPage: Int
    get() = getIntExtra(ExtraStartPage, PlayerPage).coerceIn(PlayerPage, SettingsPage)

  companion object {
    const val ExtraStartPage = "ee.schimke.cadence.wear.START_PAGE"
    const val PlayerPage = 0
    const val SettingsPage = 2
  }
}
