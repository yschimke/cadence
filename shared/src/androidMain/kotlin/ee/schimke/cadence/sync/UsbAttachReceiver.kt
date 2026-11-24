package ee.schimke.cadence.sync

import android.app.ForegroundServiceStartNotAllowedException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.util.Log
import ee.schimke.cadence.metro.AppGraphProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Triggered by the system when a USB device matching xml/usb_device_filter is attached. Starts
 * [SyncForegroundService] when auto-sync is enabled.
 */
class UsbAttachReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != UsbManager.ACTION_USB_DEVICE_ATTACHED) return
    val device: UsbDevice? =
      intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
    val app = context.applicationContext as? AppGraphProvider ?: return
    val syncRepo = app.appGraph.syncRepo

    val pending = goAsync()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    scope.launch {
      try {
        val prefs = syncRepo.preferences.first()
        if (prefs == null || !prefs.auto_sync_on_usb || prefs.target_device_id.isBlank()) {
          return@launch
        }

        val expected = prefs.usb_match.takeIf { it.isNotBlank() }
        if (expected != null && device != null) {
          val actual = "%04X:%04X".format(device.vendorId, device.productId)
          if (!actual.equals(expected, ignoreCase = true)) return@launch
        }

        try {
          SyncForegroundService.start(context, prefs.target_device_id)
        } catch (e: ForegroundServiceStartNotAllowedException) {
          Log.w(TAG, "Cannot start SyncForegroundService from background on USB attach", e)
        }
      } finally {
        scope.cancel()
        pending.finish()
      }
    }
  }

  private companion object {
    const val TAG = "UsbAttachReceiver"
  }
}
