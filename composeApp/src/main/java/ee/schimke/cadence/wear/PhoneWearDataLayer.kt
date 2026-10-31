package ee.schimke.cadence.wear

import android.content.Context
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import ee.schimke.cadence.bluetooth.BluetoothController
import ee.schimke.cadence.bluetooth.WorkingMode as PhoneWorkingMode
import ee.schimke.cadence.sync.SyncOrchestrator
import ee.schimke.cadence.sync.SyncProgress
import ee.schimke.cadence.wearcontract.CompanionCommand
import ee.schimke.cadence.wearcontract.WearCompanionProtocol
import ee.schimke.rcsp.RcspCommandId
import ee.schimke.rcsp.WorkingMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Publishes only phone-owned, verified configuration and sync facts to the paired watch. */
class PhoneWearDataPublisher(
  private val context: Context,
  private val bluetoothController: BluetoothController,
  private val syncOrchestrator: SyncOrchestrator,
) {
  fun start() {
    scope.launch {
      combine(bluetoothController.state, syncOrchestrator.progress) { bluetooth, sync ->
          bluetooth to sync
        }
        .collect { (bluetooth, sync) -> publish(bluetooth, sync) }
    }
  }

  private fun publish(
    bluetooth: ee.schimke.cadence.bluetooth.BluetoothState,
    sync: SyncProgress,
  ) {
    val openSwim = bluetooth.connectedDevice?.takeIf { it.name.contains("OpenSwim Pro", true) }
    val localAudio = bluetooth.workingMode == PhoneWorkingMode.Mp3
    val request = PutDataMapRequest.create(WearCompanionProtocol.SnapshotPath)
    request.dataMap.apply {
      putInt(WearCompanionProtocol.SchemaVersionKey, WearCompanionProtocol.SchemaVersion)
      putLong(WearCompanionProtocol.RevisionKey, System.currentTimeMillis())
      putString(
        WearCompanionProtocol.ConnectionKey,
        if (openSwim == null) "disconnected" else "phone_connected",
      )
      putString(WearCompanionProtocol.DeviceNameKey, openSwim?.name.orEmpty())
      putString(
        WearCompanionProtocol.ModeKey,
        if (localAudio) WorkingMode.LocalAudio.name else WorkingMode.Bluetooth.name,
      )
      putStringArrayList(
        WearCompanionProtocol.CapabilityIdsKey,
        ArrayList(
          if (openSwim != null && localAudio) {
            listOf(
              RcspCommandId.Play.name,
              RcspCommandId.Pause.name,
              RcspCommandId.PreviousTrack.name,
              RcspCommandId.NextTrack.name,
              RcspCommandId.SelectWorkingMode.name,
            )
          } else if (openSwim != null) {
            listOf(RcspCommandId.SelectWorkingMode.name)
          } else {
            emptyList()
          }
        ),
      )
      putInt(WearCompanionProtocol.VolumePercentKey, bluetooth.volumePercent)
      putString(WearCompanionProtocol.SyncStateKey, sync.wearState)
      putString(WearCompanionProtocol.SyncDetailKey, sync.wearDetail)
      putInt(WearCompanionProtocol.SyncFilesCompletedKey, sync.filesCompleted)
      putInt(WearCompanionProtocol.SyncFilesTotalKey, sync.filesTotal)
      putInt(WearCompanionProtocol.SyncFilesSkippedKey, sync.filesSkipped)
    }
    Wearable.getDataClient(context).putDataItem(request.asPutDataRequest().setUrgent())
  }

  private companion object {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  }
}

/** Receives semantic watch commands; no Bluetooth protocol is exposed through the Data Layer. */
class PhoneWearCommandService : WearableListenerService() {
  override fun onMessageReceived(event: MessageEvent) {
    if (event.path != WearCompanionProtocol.CommandPath) return
    val command = WearCompanionProtocol.decodeCommand(event.data) ?: return
    val controller =
      (applicationContext as ee.schimke.cadence.CadenceApplication).appGraph.bluetoothController
    commandScope.launch {
      when (command) {
        CompanionCommand.Play -> controller.play()
        CompanionCommand.Pause -> controller.pause()
        CompanionCommand.PreviousTrack -> controller.previous()
        CompanionCommand.NextTrack -> controller.next()
        is CompanionCommand.SelectMode ->
          when (command.mode) {
            WorkingMode.LocalAudio -> controller.setWorkingMode(PhoneWorkingMode.Mp3)
            WorkingMode.Bluetooth -> controller.setWorkingMode(PhoneWorkingMode.Bluetooth)
            else -> Unit
          }
        // These RCSP commands are not captured for OpenSwim Pro, so the bridge declines them.
        is CompanionCommand.SelectEqualizerPreset,
        is CompanionCommand.PlayLocalAudio,
        is CompanionCommand.SetVolume -> Unit
      }
    }
  }

  private companion object {
    val commandScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  }
}

private val SyncProgress.wearState: String
  get() = if (lastError != null) "failed" else if (running) "running" else "idle"

private val SyncProgress.wearDetail: String
  get() =
    when {
      lastError != null -> lastError.orEmpty()
      running -> currentProfileName ?: "Syncing"
      filesTotal > 0 -> "$filesCompleted of $filesTotal files ready"
      else -> "Up to date"
    }
