package ee.schimke.shokz.devices

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.google.modernstorage.storage.toOkioPath
import okio.Path


@Composable
actual fun rememberFileExplorerOpenLauncher(onGranted: (Path) -> Unit): () -> Unit {
    val context = LocalContext.current
    val contentResolver = context.contentResolver

    val launcher = rememberLauncherForActivityResult(
        contract = object : ActivityResultContracts.OpenDocumentTree() {
            override fun createIntent(
                context: android.content.Context,
                input: android.net.Uri?
            ): Intent {
                return super.createIntent(context, input).apply {
                    addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                                or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                                or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
                    )
                }
            }
        },
        onResult = { uri ->
            if (uri != null) {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                            or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )

                onGranted(uri.toOkioPath())
            }
        }
    )

    return {
        launcher.launch(input = null)
    }
}