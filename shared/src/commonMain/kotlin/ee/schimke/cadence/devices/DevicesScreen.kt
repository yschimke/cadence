package ee.schimke.cadence.devices

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ee.schimke.cadence.datastore.proto.Device
import ee.schimke.cadence.home.SwipeAction
import ee.schimke.cadence.home.SwipeActionRow
import ee.schimke.cadence.metro.metroViewModel
import ee.schimke.cadence.usb.UsbConnection
import ee.schimke.cadence.usb.UsbDevice
import kotlinx.coroutines.launch
import okio.Path
import okio.Path.Companion.toPath

@Composable
fun DevicesScreen(
  modifier: Modifier = Modifier,
  podcastTarget: String = "",
  onDeviceClick: ((Device) -> Unit)?,
) {
  val viewModel = metroViewModel<DevicesViewModel>()
  val uiState by viewModel.uiState.collectAsState()
  val connection by viewModel.usbManager.connection.collectAsState(UsbConnection())
  val permissionCheck =
    rememberFileExplorerOpenLauncher(onGranted = { name, path -> viewModel.addDevice(path, name) })
  val openInFiles = rememberOpenInFilesApp()
  DevicesScreen(
    modifier = modifier,
    uiState = uiState,
    permissionCheck = permissionCheck,
    onDeviceClick = onDeviceClick,
    podcastTarget = podcastTarget,
    onForget = { viewModel.forgetDevice(it.id) },
    onOpenInFiles = openInFiles?.let { open -> { device: Device -> open(device.path.toPath()) } },
    usbConnection =
      if (viewModel.usbManager.explicitConnection)
        UsbConnectionUi(connection, viewModel.usbManager::requestConnection)
      else null,
  )
}

/** Only where connecting USB is a step people take (the browser, desktop). */
class UsbConnectionUi(val connection: UsbConnection, val connect: () -> Unit)

/**
 * The folders Cadence may open, on the phone or on plugged-in storage, and what is plugged in. Each
 * folder says which drive it is on, so several folders on the same headphones read as such.
 */
@Composable
fun DevicesScreen(
  modifier: Modifier = Modifier,
  uiState: DevicesViewModel.UiState,
  permissionCheck: () -> Unit,
  onDeviceClick: ((Device) -> Unit)?,
  podcastTarget: String = "",
  onForget: (Device) -> Unit = {},
  /**
   * Opens a folder in another app (Files); reports whether one took it. Null where there's none.
   */
  onOpenInFiles: ((Device) -> Boolean)? = null,
  usbConnection: UsbConnectionUi? = null,
) {
  val colors = MaterialTheme.colorScheme
  val snackbar = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  Box(modifier.fillMaxSize()) {
    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      item(key = "intro") {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(
            "Storage",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
          )
          Text(
            "Folders you've let Cadence open, on this device or on your headphones." +
              if (onDeviceClick != null) " Tap one to see its files." else "",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
          )
        }
      }
      when (uiState) {
        DevicesViewModel.UiState.Loading -> item(key = "loading") { Text("Loading…") }
        is DevicesViewModel.UiState.Devices -> {
          if (uiState.devices.isEmpty())
            item(key = "empty") {
              Text(
                "No folders yet.",
                style = MaterialTheme.typography.bodyMedium,
              )
            }
          else
            item(key = "folders") {
              Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                  "Swipe left on a folder to forget it",
                  style = MaterialTheme.typography.bodySmall,
                  color = colors.onSurfaceVariant,
                )
                OutlinedCard(Modifier.fillMaxWidth()) {
                  uiState.devices.forEachIndexed { index, device ->
                    key(device.id) {
                      if (index > 0) HorizontalDivider()
                      // Like Home's archive swipe; also offered as an accessibility action.
                      SwipeActionRow(
                        onSwipeRight = null,
                        onSwipeLeft =
                          SwipeAction("Forget", Icons.Filled.LinkOff) {
                            onForget(device)
                            scope.launch {
                              snackbar.showSnackbar(
                                "Forgot ${device.name.ifBlank { "the folder" }}. Add it again " +
                                  "to use it.",
                              )
                            }
                            true
                          },
                      ) {
                        FolderRow(
                          device,
                          usedForPodcasts =
                            podcastTarget.isNotBlank() && device.path == podcastTarget,
                          onClick = onDeviceClick?.let { { it(device) } },
                          onOpenInFiles =
                            onOpenInFiles?.let { open ->
                              {
                                if (!open(device))
                                  scope.launch {
                                    snackbar.showSnackbar("No app here can open this folder")
                                  }
                              }
                            },
                        )
                      }
                    }
                  }
                }
              }
            }
          item(key = "add") {
            FilledTonalButton(onClick = permissionCheck) {
              Icon(Icons.Filled.Add, contentDescription = null, Modifier.size(18.dp))
              Spacer(Modifier.width(8.dp))
              Text("Add a folder")
            }
          }
          item(key = "plugged-in") { PluggedIn(uiState.usbDevices, usbConnection) }
        }
      }
    }
    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
  }
}

@Composable
private fun FolderRow(
  device: Device,
  usedForPodcasts: Boolean,
  onClick: (() -> Unit)?,
  onOpenInFiles: (() -> Unit)? = null,
) {
  val location = storageLocation(device.path)
  val title = location.folder?.substringAfterLast('/') ?: device.name.ifBlank { location.drive }
  ListItem(
    leadingContent = {
      Icon(
        when {
          location.folder != null -> Icons.Filled.Folder
          location.removable -> Icons.Filled.Usb
          else -> Icons.Filled.PhoneAndroid
        },
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
      )
    },
    headlineContent = {
      Text(
        title,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    },
    supportingContent = {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          if (location.folder == null) "${location.drive} · whole drive"
          else "${location.drive} · ${location.folder}",
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (usedForPodcasts)
          AssistChip(
            onClick = onClick ?: {},
            label = { Text("Podcasts are copied here") },
            leadingIcon = {
              Icon(Icons.Filled.Headphones, contentDescription = null, Modifier.size(18.dp))
            },
          )
      }
    },
    trailingContent =
      onOpenInFiles?.let { open ->
        {
          IconButton(onClick = open) {
            // Named per folder, so a screen reader can tell the buttons apart.
            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open $title in Files")
          }
        }
      },
    // Opaque, so the swipe background only shows where the row has moved away.
    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
    modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
  )
}

/** What is plugged in by USB, in words; the raw ids only matter to debugging. */
@Composable
private fun PluggedIn(devices: List<UsbDevice>, connection: UsbConnectionUi?) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
      "Plugged in",
      style = MaterialTheme.typography.titleMedium,
      modifier = Modifier.semantics { heading() },
    )
    if (devices.isEmpty())
      Text(
        "Nothing is plugged in by USB.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    devices.forEach { device ->
      val headphones = device.vendorId == HEADPHONE_VENDOR && device.productId == HEADPHONE_PRODUCT
      PluggedInRow(
        icon = if (headphones) Icons.Filled.Headphones else Icons.Filled.Usb,
        title = if (headphones) "Your headphones" else device.productName ?: "USB device",
        detail =
          if (headphones) "USB storage. Add its folder above to browse it."
          else listOfNotNull(device.manufacturerName, "USB storage").joinToString(" · "),
      )
    }
    if (connection != null) {
      Text(
        connection.connection.message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      FilledTonalButton(
        enabled = connection.connection.available && !connection.connection.busy,
        onClick = connection.connect,
      ) {
        Text("Connect USB")
      }
    }
  }
}

@Composable
private fun PluggedInRow(icon: ImageVector, title: String, detail: String) {
  ListItem(
    leadingContent = { Icon(icon, contentDescription = null) },
    headlineContent = { Text(title) },
    supportingContent = { Text(detail) },
    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    modifier = Modifier.padding(horizontal = 0.dp),
  )
}

// The USB-to-storage bridge in the supported headphones (xml/usb_device_filter).
private const val HEADPHONE_VENDOR = 1507
private const val HEADPHONE_PRODUCT = 1889

/**
 * Opens a folder Cadence was granted in the platform's file manager, such as Files by Google; the
 * result says whether an app took it. Null where folders can't be handed to another app.
 */
@Composable expect fun rememberOpenInFilesApp(): ((Path) -> Boolean)?

@Composable
expect fun rememberFileExplorerOpenLauncher(
  onGranted: (name: String, path: Path) -> Unit,
): () -> Unit
