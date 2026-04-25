package ee.schimke.cadence.sync

import android.content.Context
import android.net.Uri
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import ee.schimke.cadence.datastore.proto.SourceKind
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import okio.Path
import okio.buffer
import okio.source

@ContributesBinding(AppScope::class, binding = binding<RefreshOrchestrator>())
@SingleIn(AppScope::class)
@Inject
class AndroidRefreshOrchestrator(
    private val context: Context,
    private val syncRepo: SyncRepo,
    private val stagingArea: StagingArea,
    private val sourceBrowser: SourceBrowser,
) : RefreshOrchestrator {

    private val _progress = MutableStateFlow(RefreshProgress())
    override val progress = _progress.asStateFlow()

    override suspend fun refresh(profileId: String) {
        val profile = syncRepo.getProfile(profileId) ?: run {
            _progress.value = RefreshProgress(running = false, lastError = "Profile not found")
            return
        }
        coroutineScope {
            _progress.value = RefreshProgress(
                running = true,
                profileId = profile.id,
                profileName = profile.name,
            )
            try {
                // Collect every browsed item across the profile's sources.
                val items = profile.source_ids.flatMap { sourceId ->
                    val source = syncRepo.getSource(sourceId) ?: return@flatMap emptyList()
                    if (source.kind != SourceKind.LOCAL_DIRECTORY) {
                        // NFS or future kinds: skip but record a non-fatal note.
                        return@flatMap emptyList()
                    }
                    sourceBrowser.browse(sourceId)
                }

                // Wipe and re-materialise the staging directory each refresh. Simple
                // and predictable; no diffing yet.
                stagingArea.clearProfile(profile.staging_subpath)
                val targetRoot = stagingArea.profileDir(profile.staging_subpath)

                _progress.value = _progress.value.copy(filesTotal = items.size)
                var done = 0
                for (item in items) {
                    if (!isActive) break
                    _progress.value = _progress.value.copy(currentFileName = item.displayName)
                    copy(item, targetRoot)
                    done++
                    _progress.value = _progress.value.copy(filesCompleted = done)
                }

                syncRepo.recordRefreshSuccess(profile.id, currentIso8601())
                _progress.value = _progress.value.copy(running = false, currentFileName = null)
            } catch (t: Throwable) {
                val message = t.message ?: t.javaClass.simpleName
                syncRepo.recordRefreshFailure(profile.id, message)
                _progress.value = _progress.value.copy(running = false, lastError = message)
                throw t
            }
        }
    }

    private fun copy(item: BrowsedItem, targetRoot: Path) {
        val segments = item.relativePath.split('/').filter { it.isNotBlank() }
            .ifEmpty { listOf(item.displayName) }
        val parent = segments.dropLast(1).fold(targetRoot) { acc, segment -> acc / segment }
        if (!stagingArea.fileSystem.exists(parent)) {
            stagingArea.fileSystem.createDirectories(parent)
        }
        val target = parent / segments.last()

        context.contentResolver.openInputStream(Uri.parse(item.sourceUri)).use { input ->
            requireNotNull(input) { "Source unreadable: ${item.sourceUri}" }
            stagingArea.fileSystem.sink(target).buffer().use { sink ->
                input.source().buffer().use { source -> sink.writeAll(source) }
            }
        }
    }

    private fun currentIso8601(): String {
        // Avoid pulling kotlinx-datetime; use the system clock in ISO format.
        val now = java.time.Instant.now()
        return now.toString()
    }
}

