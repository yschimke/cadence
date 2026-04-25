package ee.schimke.shokz.sync

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import ee.schimke.shokz.data.DevicesRepo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okio.buffer

@ContributesBinding(AppScope::class, binding = binding<SyncOrchestrator>())
@SingleIn(AppScope::class)
@Inject
class AndroidSyncOrchestrator(
    private val context: Context,
    private val syncRepo: SyncRepo,
    private val devicesRepo: DevicesRepo,
    private val stagingArea: StagingArea,
) : SyncOrchestrator {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _progress = MutableStateFlow(SyncProgress())
    override val progress = _progress

    private var currentJob: Job? = null

    override suspend fun startSync(targetDeviceId: String?) {
        if (currentJob?.isActive == true) return

        val deviceId = targetDeviceId?.takeIf { it.isNotBlank() }
            ?: syncRepo.preferences.first()?.target_device_id?.takeIf { it.isNotBlank() }
            ?: return reportError("No target device selected")

        val device = devicesRepo.getDevice(deviceId) ?: return reportError("Target device not found")
        val profiles = syncRepo.listProfiles().filter { it.source_ids.isNotEmpty() }
        if (profiles.isEmpty()) return reportError("No sync profiles configured")

        val plan: List<Pair<ee.schimke.shokz.datastore.proto.SyncProfile, List<StagedEntry>>> =
            profiles.map { profile ->
                profile to stagingArea.listProfileFiles(profile.staging_subpath)
            }.filter { it.second.isNotEmpty() }

        if (plan.isEmpty()) {
            _progress.value = SyncProgress(
                running = false,
                lastError = "Staging is empty — refresh a profile first",
            )
            return
        }

        val totalFiles = plan.sumOf { it.second.size }

        currentJob = scope.launch {
            _progress.value = SyncProgress(running = true, filesTotal = totalFiles)
            val rootUri = Uri.parse(device.path)
            val rootDoc = DocumentFile.fromTreeUri(context, rootUri)
                ?: return@launch reportError("Cannot open device tree URI")

            var completed = 0
            for ((profile, entries) in plan) {
                if (!isActive) break
                _progress.value = _progress.value.copy(currentProfileName = profile.name)
                val profileRootSegments = profile.staging_subpath.split('/').filter { it.isNotBlank() }
                val profileRoot = ensureDirectories(rootDoc, profileRootSegments)
                for (entry in entries) {
                    if (!isActive) break
                    _progress.value = _progress.value.copy(
                        currentFileName = entry.relativePath,
                        currentBytes = 0,
                        currentTotal = entry.sizeBytes,
                    )
                    runCatching { copyOne(entry, profileRoot) }
                        .onFailure {
                            _progress.value = _progress.value.copy(
                                lastError = "${entry.relativePath}: ${it.message ?: it.javaClass.simpleName}",
                            )
                        }
                    completed++
                    _progress.value = _progress.value.copy(filesCompleted = completed)
                }
            }
            _progress.value = _progress.value.copy(running = false, currentFileName = null, currentProfileName = null)
        }
    }

    private fun copyOne(entry: StagedEntry, profileRoot: DocumentFile) {
        val segments = entry.relativePath.split('/').filter { it.isNotBlank() }
            .ifEmpty { listOf(entry.absolutePath.name) }
        val parent = ensureDirectories(profileRoot, segments.dropLast(1))
        val name = segments.last()
        parent.findFile(name)?.delete()
        val mime = guessMimeType(name)
        val target = parent.createFile(mime, name)
            ?: throw IllegalStateException("Could not create $name on device")

        stagingArea.fileSystem.source(entry.absolutePath).buffer().use { source ->
            context.contentResolver.openOutputStream(target.uri, "wt").use { output ->
                requireNotNull(output) { "Target not writable: ${target.uri}" }
                val buffer = ByteArray(64 * 1024)
                var transferred = 0L
                while (true) {
                    val read = source.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    transferred += read
                    _progress.value = _progress.value.copy(currentBytes = transferred)
                }
                output.flush()
            }
        }
    }

    private fun ensureDirectories(root: DocumentFile, segments: List<String>): DocumentFile {
        var current = root
        for (segment in segments) {
            val existing = current.findFile(segment)
            current = when {
                existing != null && existing.isDirectory -> existing
                existing != null -> throw IllegalStateException("Path conflict: $segment is a file")
                else -> current.createDirectory(segment) ?: throw IllegalStateException("createDirectory failed for $segment")
            }
        }
        return current
    }

    private fun guessMimeType(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "mp3" -> "audio/mpeg"
            "m4a", "aac" -> "audio/mp4"
            "flac" -> "audio/flac"
            "ogg" -> "audio/ogg"
            "wav" -> "audio/wav"
            "opus" -> "audio/opus"
            else -> "application/octet-stream"
        }
    }

    private fun reportError(message: String) {
        _progress.value = SyncProgress(running = false, lastError = message)
    }

    override fun cancel() {
        currentJob?.cancel()
    }
}
