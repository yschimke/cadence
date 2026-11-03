package ee.schimke.cadence.devices

/** Where a saved folder lives, in words: which drive, and which folder on it. */
data class StorageLocation(val drive: String, val folder: String?, val removable: Boolean)

/**
 * Reads an Android document-tree path such as
 * `content://com.android.externalstorage.documents/tree/1CFC-4AC3%3APodcasts%2FSwim`. Other paths
 * (desktop folders, the browser) are shown as they are.
 */
fun storageLocation(path: String): StorageLocation {
  val tree = path.substringAfter("/tree/", missingDelimiterValue = "")
  if (tree.isEmpty() || "externalstorage" !in path)
    return StorageLocation(
      drive = path.substringAfterLast('/').ifBlank { path },
      folder = null,
      removable = false,
    )
  val decoded = tree.replace("%3A", ":").replace("%2F", "/").replace("%20", " ")
  val volume = decoded.substringBefore(':')
  val folder = decoded.substringAfter(':', "").trim('/').ifBlank { null }
  val primary = volume == "primary"
  return StorageLocation(
    drive = if (primary) "Phone storage" else "USB drive $volume",
    folder = folder,
    removable = !primary,
  )
}
