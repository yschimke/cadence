package ee.schimke.cadence

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import ee.schimke.cadence.metro.*

/** Desktop and browser have no wallpaper colours, so they use the Material 3 baseline scheme. */
@Composable
fun PortableApp(factory: PortableViewModelFactory, notice: String) {
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
    MaterialTheme(
      colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    ) {
      App(notice)
    }
  }
}
