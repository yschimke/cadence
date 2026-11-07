package ee.schimke.cadence

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import ee.schimke.cadence.metro.*
import ee.schimke.cadence.theme.CadenceTheme

/** Desktop and browser have no wallpaper colours, so they use the Cadence brand scheme. */
@Composable
fun PortableApp(factory: PortableViewModelFactory) {
  val owner = remember {
    object : ViewModelStoreOwner {
      override val viewModelStore = ViewModelStore()
    }
  }
  DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
  CompositionLocalProvider(
    LocalPortableFactory provides factory,
    LocalViewModelStoreOwner provides owner,
  ) {
    CadenceTheme { App() }
  }
}
