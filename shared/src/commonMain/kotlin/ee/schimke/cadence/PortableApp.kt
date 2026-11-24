package ee.schimke.cadence

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import dev.zacsweers.metrox.viewmodel.LocalMetroViewModelFactory
import dev.zacsweers.metrox.viewmodel.MetroViewModelFactory
import ee.schimke.cadence.theme.CadenceTheme

/** Desktop/browser storage comes from mounted or selected folders, rather than Android apps. */
internal val LocalPortableHost = staticCompositionLocalOf { false }

/** Desktop and browser have no wallpaper colours, so they use the Cadence brand scheme. */
@Composable
fun PortableApp(
  metroViewModelFactory: MetroViewModelFactory,
  desktopNavigation: DesktopNavigation? = null,
) {
  val owner = remember {
    object : ViewModelStoreOwner {
      override val viewModelStore = ViewModelStore()
    }
  }
  DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
  CompositionLocalProvider(
    LocalPortableHost provides true,
    LocalMetroViewModelFactory provides metroViewModelFactory,
    LocalViewModelStoreOwner provides owner,
  ) {
    CadenceTheme { App(desktopNavigation) }
  }
}
