package ee.schimke.cadence.wear.datalayer

import android.content.Context
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import ee.schimke.cadence.wear.CompanionSyncStatus
import ee.schimke.cadence.wear.HeadsetAction
import ee.schimke.cadence.wear.HeadsetActionResult
import ee.schimke.cadence.wear.HeadsetConnection
import ee.schimke.cadence.wear.HeadsetGateway
import ee.schimke.cadence.wear.HeadsetSnapshot
import ee.schimke.cadence.wear.WearCompanionServices
import ee.schimke.cadence.wear.widgets.WearWidgets
import ee.schimke.cadence.wearcontract.CompanionCommand
import ee.schimke.cadence.wearcontract.WearCompanionProtocol
import ee.schimke.rcsp.RcspCapabilities
import ee.schimke.rcsp.RcspCommandId
import ee.schimke.rcsp.WorkingMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

/** Projects persistent phone Data Layer state into the semantic Wear gateway. */
class DataLayerHeadsetGateway(private val context: Context) : HeadsetGateway {
  private val state =
    MutableStateFlow(
      HeadsetSnapshot(
        connection = HeadsetConnection.Unavailable("Waiting for Cadence on your phone")
      )
    )
  override val snapshot: StateFlow<HeadsetSnapshot> = state.asStateFlow()

  init {
    Wearable.getDataClient(context).dataItems.addOnSuccessListener { items ->
      try {
        items.firstOrNull { it.uri.path == WearCompanionProtocol.SnapshotPath }?.let(::accept)
      } finally {
        items.release()
      }
    }
  }

  override suspend fun execute(action: HeadsetAction): HeadsetActionResult {
    if (!isSupported(action.command)) return HeadsetActionResult.Unsupported(action.command.id)
    val command =
      action.toCompanionCommand() ?: return HeadsetActionResult.Unsupported(action.command.id)
    return runCatching {
      val nodes = Wearable.getNodeClient(context).connectedNodes.await()
      if (nodes.isEmpty()) return HeadsetActionResult.Failed("Cadence phone is not reachable")
      val payload = WearCompanionProtocol.encode(command)
      nodes.forEach {
        Wearable.getMessageClient(context)
          .sendMessage(it.id, WearCompanionProtocol.CommandPath, payload)
          .await()
      }
      HeadsetActionResult.Sent
    }
      .getOrElse { HeadsetActionResult.Failed(it.message ?: "Could not send command to Cadence") }
  }

  fun accept(dataItem: com.google.android.gms.wearable.DataItem) {
    val map = DataMapItem.fromDataItem(dataItem).dataMap
    if (map.getInt(WearCompanionProtocol.SchemaVersionKey) != WearCompanionProtocol.SchemaVersion)
      return
    val connection =
      if (map.getString(WearCompanionProtocol.ConnectionKey) == "phone_connected") {
        HeadsetConnection.PhoneConnected(
          map.getString(WearCompanionProtocol.DeviceNameKey) ?: "OpenSwim Pro"
        )
      } else {
        HeadsetConnection.Disconnected
      }
    val mode =
      map.getString(WearCompanionProtocol.ModeKey)?.let {
        runCatching { WorkingMode.valueOf(it) }.getOrNull()
      } ?: WorkingMode.Unknown
    val capabilities =
      RcspCapabilities(
        map
          .getStringArrayList(WearCompanionProtocol.CapabilityIdsKey)
          .orEmpty()
          .mapNotNull { runCatching { RcspCommandId.valueOf(it) }.getOrNull() }
          .toSet()
      )
    state.value =
      HeadsetSnapshot(
        connection = connection,
        mode = mode,
        capabilities = capabilities,
        volumePercent = map.getInt(WearCompanionProtocol.VolumePercentKey).takeIf { it in 0..100 },
        sync = map.toSyncStatus(),
      )
    WearWidgets.updateAll(context)
  }
}

/** Receives phone snapshots even when the companion activity is not running. */
class WearDataLayerListenerService : WearableListenerService() {
  override fun onDataChanged(events: DataEventBuffer) {
    events
      .filter {
        it.type == DataEvent.TYPE_CHANGED &&
          it.dataItem.uri.path == WearCompanionProtocol.SnapshotPath
      }
      .forEach { event -> WearCompanionServices.gateway(applicationContext).accept(event.dataItem) }
  }
}

private fun HeadsetAction.toCompanionCommand(): CompanionCommand? =
  when (this) {
    HeadsetAction.Play -> CompanionCommand.Play
    HeadsetAction.Pause -> CompanionCommand.Pause
    HeadsetAction.PreviousTrack -> CompanionCommand.PreviousTrack
    HeadsetAction.NextTrack -> CompanionCommand.NextTrack
    is HeadsetAction.SelectMode -> CompanionCommand.SelectMode(mode)
    is HeadsetAction.SelectEqualizerPreset -> CompanionCommand.SelectEqualizerPreset(preset)
    is HeadsetAction.PlayLocalAudio -> CompanionCommand.PlayLocalAudio(trackId)
    is HeadsetAction.SetVolume -> CompanionCommand.SetVolume(percent)
    is HeadsetAction.SeekRelative -> null
  }

private fun com.google.android.gms.wearable.DataMap.toSyncStatus(): CompanionSyncStatus =
  when (getString(WearCompanionProtocol.SyncStateKey)) {
    "idle" -> CompanionSyncStatus.Idle
    "running" ->
      CompanionSyncStatus.Running(
        sourceName = getString(WearCompanionProtocol.SyncDetailKey),
        filesCompleted = getInt(WearCompanionProtocol.SyncFilesCompletedKey).coerceAtLeast(0),
        filesTotal = getInt(WearCompanionProtocol.SyncFilesTotalKey).coerceAtLeast(0),
        filesSkipped = getInt(WearCompanionProtocol.SyncFilesSkippedKey).coerceAtLeast(0),
      )
    "failed" ->
      CompanionSyncStatus.Failed(
        getString(WearCompanionProtocol.SyncDetailKey) ?: "Unknown sync error"
      )
    else -> CompanionSyncStatus.Unavailable
  }
