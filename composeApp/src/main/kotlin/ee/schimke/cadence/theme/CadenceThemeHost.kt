package ee.schimke.cadence.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import ee.schimke.cadence.AndroidMaterialTheme
import ee.schimke.cadence.appearance.AppearanceViewModel
import ee.schimke.cadence.datastore.proto.ThemeMode
import ee.schimke.cadence.metro.metroViewModel

/**
 * Reads the persisted appearance preference and wraps [content] in either the
 * system-dynamic Roboto Flex theme or the Cadence-branded theme.
 *
 * Default is [ThemeMode.SYSTEM] so a fresh install keeps Material You behaviour;
 * switching is exposed as the "Appearance" row in `ManageSyncScreen`.
 */
@Composable
fun CadenceThemeHost(content: @Composable () -> Unit) {
  val viewModel = metroViewModel<AppearanceViewModel>()
  val mode by viewModel.themeMode.collectAsState()
  when (mode) {
    ThemeMode.CADENCE -> CadenceTheme(content = content)
    ThemeMode.SYSTEM -> AndroidMaterialTheme(content = content)
  }
}
