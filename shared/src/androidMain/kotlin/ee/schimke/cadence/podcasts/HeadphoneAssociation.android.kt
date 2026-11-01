package ee.schimke.cadence.podcasts

import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ee.schimke.cadence.bluetooth.OpenSwimProName

private fun Context.associationPrefs() = getSharedPreferences("headphone-associations", Context.MODE_PRIVATE)
private fun associationKey(target: String) = digest(target)
private fun optedOutKey(target: String) = "optout-" + associationKey(target)
private fun automaticKey(target: String) = "automatic-" + associationKey(target)

internal fun linkedHeadphoneAddress(context: Context, target: String): String? =
  target.takeIf { it.isNotBlank() }?.let { context.associationPrefs().getString(associationKey(it), null) }

data class HeadphoneCandidate(val address: String, val name: String)

private fun pairedHeadphones(context: Context): List<HeadphoneCandidate> =
  runCatching {
    (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter?.bondedDevices.orEmpty()
      .mapNotNull { device ->
        val name = device.name.orEmpty()
        if (name.contains(OpenSwimProName, ignoreCase = true)) HeadphoneCandidate(device.address, name) else null
      }.sortedBy { it.address }
  }.getOrDefault(emptyList())

@Composable
actual fun AutomaticallyMatchHeadphones(target: String) {
  val context = LocalContext.current.applicationContext
  var generation by remember { mutableIntStateOf(0) }
  DisposableEffect(context) {
    val receiver = object : BroadcastReceiver() {
      override fun onReceive(context: Context?, intent: Intent?) { generation++ }
    }
    val filter = IntentFilter(Intent.ACTION_MEDIA_MOUNTED).apply { addDataScheme("file") }
    ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    onDispose { context.unregisterReceiver(receiver) }
  }
  LaunchedEffect(target, generation) {
    if (target.isBlank() || linkedHeadphoneAddress(context, target) != null) return@LaunchedEffect
    val preferences = context.associationPrefs()
    val candidate = runCatching {
      val volumeId = DocumentsContract.getTreeDocumentId(Uri.parse(target)).substringBefore(':')
      val volumes = (context.getSystemService(Context.STORAGE_SERVICE) as StorageManager).storageVolumes
      val isMatchingVolume = volumes.any { it.uuid.equals(volumeId, true) && it.getDescription(context).equals("SWIM PRO", true) }
      val usb = context.getSystemService(Context.USB_SERVICE) as UsbManager
      val matchingDevices = usb.deviceList.values.count { it.vendorId == 1507 && it.productId == 1889 }
      automaticHeadphoneCandidate(isMatchingVolume, matchingDevices, pairedHeadphones(context).map { it.address }, preferences.getBoolean(optedOutKey(target), false))
    }.getOrNull() ?: return@LaunchedEffect
    preferences.edit().putString(associationKey(target), candidate).putBoolean(automaticKey(target), true).apply()
    PodcastSyncWidget.updateAll(context)
  }
}

@Composable
actual fun HeadphoneAssociation(target: String) {
  val context = LocalContext.current.applicationContext
  var linked by remember(target) { mutableStateOf(linkedHeadphoneAddress(context, target)) }
  var editing by remember(target) { mutableStateOf(linked == null) }
  val candidates = remember(editing) { pairedHeadphones(context) }
  var automatic by remember(target) { mutableStateOf(context.associationPrefs().getBoolean(automaticKey(target), false)) }
  DisposableEffect(context, target) {
    val preferences = context.associationPrefs()
    val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
      if (key == associationKey(target)) {
        linked = linkedHeadphoneAddress(context, target)
        editing = linked == null
      }
      if (key == automaticKey(target)) automatic = preferences.getBoolean(automaticKey(target), false)
    }
    preferences.registerOnSharedPreferenceChangeListener(listener)
    onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
  }
  HeadphoneAssociationContent(
    linked = linked,
    candidates = candidates,
    editing = editing,
    automatic = automatic,
    onEdit = { editing = true },
    onLink = { address ->
      context.associationPrefs().edit().putString(associationKey(target), address).remove(optedOutKey(target)).remove(automaticKey(target)).apply()
      linked = address
      automatic = false
      editing = false
      PodcastSyncWidget.updateAll(context)
    },
    onRemove = {
      context.associationPrefs().edit().remove(associationKey(target)).remove(automaticKey(target)).putBoolean(optedOutKey(target), true).apply()
      linked = null
      automatic = false
      editing = true
      PodcastSyncWidget.updateAll(context)
    },
  )
}

@Composable
fun HeadphoneAssociationContent(
  linked: String?,
  candidates: List<HeadphoneCandidate>,
  editing: Boolean,
  onEdit: () -> Unit,
  onLink: (String) -> Unit,
  onRemove: () -> Unit,
  automatic: Boolean = false,
) {
  OutlinedCard(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text("Match USB storage to headphones", style = MaterialTheme.typography.titleSmall)
      if (linked != null && !editing) {
        val name = candidates.firstOrNull { it.address == linked }?.name ?: "Paired headphones"
        Text("${if (automatic) "Likely match" else "Linked"}: $name · ${linked.takeLast(5)}", style = MaterialTheme.typography.bodyMedium)
        if (automatic) Text("Matched using the connected storage and your only paired compatible headset. You can change or remove this match.", style = MaterialTheme.typography.bodySmall)
        Row {
          TextButton(onClick = onEdit) { Text("Change") }
          TextButton(onClick = onRemove) { Text("Remove match") }
        }
      } else {
        Text("USB storage does not identify its Bluetooth device. Choose the matching paired headphones; you can change this later.", style = MaterialTheme.typography.bodyMedium)
        if (candidates.isEmpty()) Text("Pair headphones and allow Bluetooth access to choose a match.", style = MaterialTheme.typography.bodySmall)
        candidates.forEach { candidate ->
          OutlinedButton(onClick = { onLink(candidate.address) }) {
            Text("${if (candidates.size == 1) "Suggested: " else ""}${candidate.name} · ${candidate.address.takeLast(5)}")
          }
        }
        if (linked != null) TextButton(onClick = onRemove) { Text("Remove match") }
      }
    }
  }
}
