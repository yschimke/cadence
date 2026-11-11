package ee.schimke.cadence.devices

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import ee.schimke.cadence.data.toOkioPath
import ee.schimke.cadence.data.toUri
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

/**
 * Hands a granted folder to the file manager as a directory document. Files by Google and the
 * system Files app open `vnd.android.document/directory`; a whole drive is also offered as its
 * root, which some file managers only take that way.
 */
@Composable
actual fun rememberOpenInFilesApp(): ((Path) -> Boolean)? {
  val context = LocalContext.current
  return { path ->
    val uri = path.toUri()
    val document =
      if (android.provider.DocumentsContract.isTreeUri(uri))
        android.provider.DocumentsContract.buildDocumentUriUsingTree(
          uri,
          android.provider.DocumentsContract.getTreeDocumentId(uri),
        )
      else uri
    val documentId =
      runCatching { android.provider.DocumentsContract.getDocumentId(document) }.getOrNull()
    val root =
      documentId
        ?.takeIf { it.endsWith(":") }
        ?.let { android.provider.DocumentsContract.buildRootUri(document.authority, it.dropLast(1)) }
    listOfNotNull(
        document to android.provider.DocumentsContract.Document.MIME_TYPE_DIR,
        root?.let { it to "vnd.android.document/root" },
      )
      .any { (data, type) ->
        val intent =
          Intent(Intent.ACTION_VIEW)
            .setDataAndType(data, type)
            .addFlags(
              Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_ACTIVITY_NEW_TASK,
            )
        try {
          context.startActivity(intent)
          true
        } catch (_: android.content.ActivityNotFoundException) {
          false
        }
      }
  }
}
