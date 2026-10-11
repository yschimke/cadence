package ee.schimke.cadence

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import ee.schimke.cadence.datastore.proto.ThemeMode
import ee.schimke.cadence.metro.*
import ee.schimke.cadence.theme.CadenceTheme

@Composable
fun PortableApp(factory: PortableViewModelFactory, notice: String) {
  val owner = remember {
    object : ViewModelStoreOwner {
      override val viewModelStore = ViewModelStore()
    }
  }
  DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
  val mode by factory.themeMode.collectAsState(ThemeMode.SYSTEM)
  CompositionLocalProvider(
    LocalPortableFactory provides factory,
    LocalViewModelStoreOwner provides owner,
  ) {
    if (mode == ThemeMode.CADENCE) CadenceTheme { App(notice) }
    else
      MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
      ) {
        App(notice)
      }
  }
}
