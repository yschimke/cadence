package ee.schimke.cadence.data

import android.net.Uri
import okio.Path
import okio.Path.Companion.toPath

/** Okio normalizes the double slash in content URIs; restore it when crossing into Android. */
fun Path.toUri(): Uri {
  val value = toString().replace("content:/", "content://").replace("android.resource:/", "android.resource://")
  val uri = Uri.parse(value)
  return if (uri.isPhysicalPath()) Uri.fromFile(toFile()) else uri
}
fun Uri.toOkioPath(): Path = if (isPhysicalPath()) requireNotNull(path).toPath() else toString().toPath()
private fun Uri.isPhysicalPath(): Boolean =
  (scheme == null || scheme == "file") && authority.isNullOrBlank() && path?.startsWith('/') == true && pathSegments.isNotEmpty() && pathSegments.first() != "android_asset"

internal object MetadataExtras {
  data class DisplayName(val value: String?)
  data class FilePath(val value: String?)
  data class MimeType(val value: String?)
}
