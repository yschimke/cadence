package ee.schimke.cadence

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** Desktop destinations share the same commands between the sidebar and the native menu. */
enum class DesktopSection(
  val label: String,
  internal val routeName: String,
  val icon: ImageVector,
) {
  Library("Library", "Home", Icons.Default.LibraryMusic),
  Episodes("Episodes", "Podcasts", Icons.AutoMirrored.Filled.QueueMusic),
  Devices("Devices", "Devices", Icons.Default.Usb),
  Sources("Sources", "ManageSync", Icons.Default.Folder),
  Transfers("Transfers", "FileSync", Icons.Default.Sync),
  CloudBackup("Cloud backup", "CloudSync", Icons.Default.Cloud),
}

class DesktopNavigation {
  var activeSection by mutableStateOf(DesktopSection.Library)
    internal set

  internal var request by mutableStateOf(0L to DesktopSection.Library)
    private set

  fun select(section: DesktopSection) {
    request = request.first + 1 to section
  }
}

@Composable
internal fun DesktopSidebar(navigation: DesktopNavigation) {
  Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
    Column(
      Modifier.width(216.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text(
        "Cadence",
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
      )
      DesktopSection.entries.forEach { section ->
        NavigationDrawerItem(
          label = { Text(section.label) },
          selected = navigation.activeSection == section,
          icon = { Icon(section.icon, contentDescription = null) },
          onClick = { navigation.select(section) },
        )
      }
    }
  }
}
