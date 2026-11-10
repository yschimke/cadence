package ee.schimke.cadence.devices

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import ee.schimke.cadence.data.toOkioPath
import okio.Path

@Composable
actual fun rememberFileExplorerOpenLauncher(
  onGranted: (name: String, path: Path) -> Unit,
): () -> Unit {
  val context = LocalContext.current
  val contentResolver = context.contentResolver

  val launcher =
    rememberLauncherForActivityResult(
      contract =
        object : ActivityResultContracts.OpenDocumentTree() {
          override fun createIntent(
            context: android.content.Context,
            input: android.net.Uri?,
          ): Intent {
            return super.createIntent(context, input).apply {
              addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                  Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                  Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                  Intent.FLAG_GRANT_PREFIX_URI_PERMISSION,
              )
            }
          }
        },
      onResult = { uri ->
        if (uri != null) {
          contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
          )

          onGranted(
            androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)?.name ?: "Device",
            uri.toOkioPath(),
          )
        }
      },
    )

  return { launcher.launch(input = headphoneVolumeRoot(context)) }
}

/**
 * The root of the plugged-in headphone volume (SWIM PRO, else the only removable volume), so the
 * picker opens on it and choosing it takes one tap. Android only lets an app write to USB storage
 * the user granted through this picker, so the tap itself can't be skipped.
 */
internal fun headphoneVolumeRoot(context: android.content.Context): android.net.Uri? {
  val volumes =
    context
      .getSystemService(android.os.storage.StorageManager::class.java)
      ?.storageVolumes
      .orEmpty()
      .filter { it.isRemovable && it.uuid != null && it.state == android.os.Environment.MEDIA_MOUNTED }
  val volume =
    volumes.firstOrNull { it.getDescription(context).equals("SWIM PRO", ignoreCase = true) }
      ?: volumes.singleOrNull()
      ?: return null
  return android.provider.DocumentsContract.buildDocumentUri(
    "com.android.externalstorage.documents",
    "${volume.uuid}:",
  )
}
