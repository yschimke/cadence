package ee.schimke.cadence

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModelProvider
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import ee.schimke.cadence.metro.ActivityKey
import ee.schimke.cadence.platform.LocalPlayUpdateSettings
import ee.schimke.cadence.platform.PlayUpdateSettings
import ee.schimke.cadence.sync.google.GoogleSyncController
import ee.schimke.cadence.sync.google.LocalGoogleSyncController

@ContributesIntoMap(AppScope::class, binding<Activity>())
@ActivityKey(MainActivity::class)
@Inject
class MainActivity(
  private val viewModelFactory: ViewModelProvider.Factory,
  private val googleSync: GoogleSyncController,
) : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    val splashScreen = installSplashScreen()

    enableEdgeToEdge()

    super.onCreate(savedInstanceState)
    promptForPlayStoreUpgrade()

    setContent {
      var tester by remember { mutableStateOf(isPlayUpdateTester(this)) }
      val playUpdates =
        PlayUpdateSettings(
          version = BuildConfig.VERSION_NAME,
          tester = tester,
          onTesterChange = {
            tester = it
            setPlayUpdateTester(this, it)
          },
        )
      AndroidMaterialTheme {
        CompositionLocalProvider(
          LocalGoogleSyncController provides googleSync,
          LocalPlayUpdateSettings provides playUpdates,
        ) {
          App()
        }
      }
    }
  }

  override val defaultViewModelProviderFactory: ViewModelProvider.Factory
    get() {
      return viewModelFactory
    }
}
