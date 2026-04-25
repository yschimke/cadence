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
import ee.schimke.shokz.datastore.proto.StagedStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@ContributesBinding(AppScope::class, binding = binding<SyncOrchestrator>())
@SingleIn(AppScope::class)
@Inject
class AndroidSyncOrchestrator(
    private val context: Context,
    private val syncRepo: SyncRepo,
    private val devicesRepo: DevicesRepo,
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
        val pending = syncRepo.pendingFiles()
        if (pending.isEmpty()) {
            _progress.value = SyncProgress(running = false, lastError = "Nothing to sync")
            return
        }

        currentJob = scope.launch {
            _progress.value = SyncProgress(running = true, filesTotal = pending.size)
            val rootUri = Uri.parse(device.path)
            val rootDoc = DocumentFile.fromTreeUri(context, rootUri)
                ?: return@launch reportError("Cannot open device tree URI")

            var completed = 0
            for (file in pending) {
                if (!isActive) break
                _progress.value = _progress.value.copy(
                    currentFileName = file.display_name,
                    currentBytes = 0,
                    currentTotal = file.size_bytes,
                )
                syncRepo.updateStaged(file.id) { it.copy(status = StagedStatus.IN_PROGRESS, bytes_transferred = 0) }
                try {
                    copyOne(file, rootDoc)
                    syncRepo.updateStaged(file.id) {
                        it.copy(status = StagedStatus.COMPLETED, bytes_transferred = file.size_bytes)
                    }
                } catch (t: Throwable) {
                    syncRepo.updateStaged(file.id) {
                        it.copy(status = StagedStatus.FAILED, error_message = t.message ?: t.javaClass.simpleName)
                    }
                }
                completed++
                _progress.value = _progress.value.copy(filesCompleted = completed)
            }
            _progress.value = _progress.value.copy(running = false, currentFileName = null)
        }
    }

    private fun copyOne(
        file: ee.schimke.shokz.datastore.proto.StagedFile,
        rootDoc: DocumentFile,
    ) {
        val targetSegments = file.target_relative_path.split('/').filter { it.isNotBlank() }
            .ifEmpty { listOf(file.display_name.ifBlank { "file" }) }
        val parent = ensureDirectories(rootDoc, targetSegments.dropLast(1))
        val name = targetSegments.last()
        // Replace any existing entry to keep behaviour predictable.
        parent.findFile(name)?.delete()
        val mime = guessMimeType(name)
        val target = parent.createFile(mime, name)
            ?: throw IllegalStateException("Could not create $name on device")

        context.contentResolver.openInputStream(Uri.parse(file.source_uri)).use { input ->
            requireNotNull(input) { "Source not readable: ${file.source_uri}" }
            context.contentResolver.openOutputStream(target.uri, "wt").use { output ->
                requireNotNull(output) { "Target not writable: ${target.uri}" }
                val buffer = ByteArray(64 * 1024)
                var transferred = 0L
                while (true) {
                    val read = input.read(buffer)
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
