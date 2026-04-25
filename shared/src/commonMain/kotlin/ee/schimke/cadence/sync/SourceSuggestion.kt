package ee.schimke.cadence.sync

interface SourceSuggestionsProvider {
  suspend fun list(): List<SourceSuggestion>

  /**
   * Opens the suggested app if installed, otherwise launches the Play Store (or the web Play Store
   * as a fallback).
   */
  fun open(suggestion: SourceSuggestion)
}

data class SourceSuggestion(
  val packageName: String,
  val displayName: String,
  val description: String,
  val capabilities: List<String>,
  val installed: Boolean = false,
)

/**
 * Curated catalogue of widely-used apps that register a SAF DocumentsProvider. Once installed, each
 * appears in the standard "Add directory" picker without any extra integration on Shokz' side.
 */
object SourceSuggestionsCatalogue {
  val all: List<SourceSuggestion> =
    listOf(
      SourceSuggestion(
        packageName = "com.google.android.apps.docs",
        displayName = "Google Drive",
        description = "Cloud storage. Folders are exposed via SAF on recent Drive versions.",
        capabilities = listOf("Cloud"),
      ),
      SourceSuggestion(
        packageName = "com.microsoft.skydrive",
        displayName = "OneDrive",
        description = "Microsoft cloud storage with full SAF tree access.",
        capabilities = listOf("Cloud"),
      ),
      SourceSuggestion(
        packageName = "com.dropbox.android",
        displayName = "Dropbox",
        description = "Dropbox cloud storage exposed via the system file picker.",
        capabilities = listOf("Cloud"),
      ),
      SourceSuggestion(
        packageName = "com.nextcloud.client",
        displayName = "Nextcloud",
        description = "Self-hosted cloud / WebDAV. Surfaces folders through SAF.",
        capabilities = listOf("Cloud", "WebDAV"),
      ),
      SourceSuggestion(
        packageName = "com.box.android",
        displayName = "Box",
        description = "Box cloud storage with SAF integration.",
        capabilities = listOf("Cloud"),
      ),
      SourceSuggestion(
        packageName = "com.pcloud.pcloud",
        displayName = "pCloud",
        description = "pCloud cloud storage with SAF integration.",
        capabilities = listOf("Cloud"),
      ),
      SourceSuggestion(
        packageName = "pl.solidexplorer2",
        displayName = "Solid Explorer",
        description = "File manager that adds SMB, FTP, SFTP and WebDAV to the SAF picker.",
        capabilities = listOf("SMB", "FTP", "SFTP", "WebDAV"),
      ),
      SourceSuggestion(
        packageName = "com.cxinventor.file.explorer",
        displayName = "CX File Explorer",
        description = "Free file manager exposing SMB, FTP and WebDAV via SAF.",
        capabilities = listOf("SMB", "FTP", "WebDAV"),
      ),
      SourceSuggestion(
        packageName = "com.lonelycatgames.Xplore",
        displayName = "X-plore File Manager",
        description = "Adds SMB, FTP, SFTP, WebDAV and DLNA to the SAF picker.",
        capabilities = listOf("SMB", "FTP", "SFTP", "WebDAV"),
      ),
      SourceSuggestion(
        packageName = "me.zhanghai.android.files",
        displayName = "Material Files",
        description = "Open-source file manager with SMB, FTP and SFTP exposed via SAF.",
        capabilities = listOf("SMB", "FTP", "SFTP"),
      ),
      SourceSuggestion(
        packageName = "at.bitfire.davdroid",
        displayName = "DAVx5",
        description = "WebDAV / CalDAV / CardDAV client; mounts WebDAV as a SAF source.",
        capabilities = listOf("WebDAV"),
      ),
      SourceSuggestion(
        packageName = "io.github.x0b.rcx",
        displayName = "RCX (rclone for Android)",
        description = "Mounts any rclone remote (S3, B2, SFTP, Mega, ~70 backends) as SAF.",
        capabilities = listOf("rclone", "S3", "SFTP"),
      ),
    )
}
