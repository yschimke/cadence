package ee.schimke.cadence.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ee.schimke.cadence.developer.LocalDeveloperMode
import ee.schimke.cadence.platform.LocalPlayUpdateSettings
import ee.schimke.cadence.platform.LocalWidgetDesigns
import ee.schimke.cadence.platform.PlayUpdateSettings
import ee.schimke.cadence.platform.WidgetDesigns
import kotlinx.coroutines.launch

/**
 * Settings: Advanced options most people leave off, then the developer options once unlocked, and
 * the version, which unlocks them after [DEVELOPER_TAPS] taps. Sources have their own screen.
 */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
  val advanced = LocalAdvancedSettings.current
  val developer = LocalDeveloperMode.current
  val playUpdates = LocalPlayUpdateSettings.current
  val widgetDesigns = LocalWidgetDesigns.current
  Surface(modifier = modifier, color = MaterialTheme.colorScheme.background) {
    LazyColumn(
      modifier =
        Modifier.fillMaxWidth()
          .windowInsetsPadding(
            WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
          ),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item {
        Text(
          "Settings",
          style = MaterialTheme.typography.headlineSmall,
          modifier = Modifier.semantics { heading() },
        )
      }
      item { SettingsHeading("Advanced") }
      item {
        SwitchRow(
          title = "Headphone controls",
          summary =
            "Play, pause and switch modes on Bluetooth headphones from Home, for headphones " +
              "that support it.",
          checked = advanced.headphoneControls,
          onCheckedChange = advanced.setHeadphoneControls,
        )
      }
      if (developer.enabled) {
        item { DeveloperHeader(onTurnOff = { developer.setEnabled(false) }) }
        playUpdates?.let { settings -> item { EarlyUpdatesRow(settings) } }
        widgetDesigns?.let { designs -> item { WidgetDesignsRow(designs) } }
      }
      item {
        AppVersionRow(
          version = playUpdates?.version,
          developer = developer.enabled,
          onUnlock = { developer.setEnabled(true) },
        )
      }
    }
  }
}

@Composable
private fun SettingsHeading(title: String) {
  Text(
    title,
    style = MaterialTheme.typography.titleMedium,
    fontWeight = FontWeight.SemiBold,
    color = MaterialTheme.colorScheme.primary,
    modifier = Modifier.padding(top = 8.dp).semantics { heading() },
  )
}

/** A setting that is on or off: the whole row toggles it, not just the switch. */
@Composable
private fun SwitchRow(
  title: String,
  summary: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier =
      Modifier.fillMaxWidth()
        .heightIn(min = 48.dp)
        .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
  ) {
    Column(Modifier.weight(1f).padding(end = 16.dp)) {
      Text(title, style = MaterialTheme.typography.titleSmall)
      Text(
        summary,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Switch(checked = checked, onCheckedChange = null)
  }
}

/**
 * Fetches the widgets' designs now rather than waiting for the daily refresh: to pick up a design
 * or season just published, or to check what the widgets draw.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WidgetDesignsRow(designs: WidgetDesigns) {
  val scope = rememberCoroutineScope()
  var refreshing by remember { mutableStateOf(false) }
  var result by remember { mutableStateOf<String?>(null) }
  Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
    Column(Modifier.weight(1f)) {
      Text("Widget designs", style = MaterialTheme.typography.titleSmall)
      Text(
        result ?: "Seasonal looks for the home-screen widgets, updated daily.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    if (refreshing) {
      LoadingIndicator(Modifier.size(28.dp))
    } else {
      TextButton(
        onClick = {
          refreshing = true
          scope.launch {
            result =
              try {
                designs.refresh()
              } finally {
                refreshing = false
              }
          }
        },
      ) {
        Text("Refresh")
      }
    }
  }
}

/** Taps on [AppVersionRow] that turn on developer mode. */
private const val DEVELOPER_TAPS = 7

/**
 * The installed version, or the app name where it is not known (desktop, web). Tapping it
 * [DEVELOPER_TAPS] times turns on developer mode, which shows the options most people never need.
 */
@Composable
private fun AppVersionRow(version: String?, developer: Boolean, onUnlock: () -> Unit) {
  var taps by remember { mutableStateOf(0) }
  Text(
    version?.let { "Version $it" } ?: "Cadence",
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier =
      Modifier.clickable(enabled = !developer) {
          taps++
          if (taps >= DEVELOPER_TAPS) {
            taps = 0
            onUnlock()
          }
        }
        .padding(vertical = 8.dp),
  )
}

/** Starts the developer options, with the switch that hides them again. */
@Composable
private fun DeveloperHeader(onTurnOff: () -> Unit) {
  Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
    Column(Modifier.weight(1f)) {
      Text(
        "Developer options",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.semantics { heading() },
      )
      Text(
        "For testing and diagnostics. Tap the version 7 times to show them again.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Switch(checked = true, onCheckedChange = { if (!it) onTurnOff() })
  }
}

/**
 * Opts this device in to Play updates as soon as they appear (internal testers); otherwise updates
 * are offered once they are a week old.
 */
@Composable
private fun EarlyUpdatesRow(settings: PlayUpdateSettings) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Column(Modifier.weight(1f)) {
      Text("Early updates", style = MaterialTheme.typography.titleSmall)
      Text(
        "Update as soon as a new version is on Play, for testers.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    Switch(checked = settings.tester, onCheckedChange = settings.onTesterChange)
  }
}
