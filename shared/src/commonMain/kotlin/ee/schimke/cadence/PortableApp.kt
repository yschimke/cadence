package ee.schimke.cadence

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import ee.schimke.cadence.metro.*
import ee.schimke.cadence.theme.CadenceTheme

/** Desktop/browser storage comes from mounted or selected folders, rather than Android apps. */
internal val LocalPortableHost = staticCompositionLocalOf { false }

/** Desktop and browser have no wallpaper colours, so they use the Cadence brand scheme. */
@Composable
fun PortableApp(factory: PortableViewModelFactory, desktopNavigation: DesktopNavigation? = null) {
  val owner = remember {
    object : ViewModelStoreOwner {
      override val viewModelStore = ViewModelStore()
    }
  }
  DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
  CompositionLocalProvider(
    LocalPortableHost provides true,
    LocalPortableFactory provides factory,
    LocalViewModelStoreOwner provides owner,
  ) {
    CadenceTheme { App(desktopNavigation) }
  }
}
