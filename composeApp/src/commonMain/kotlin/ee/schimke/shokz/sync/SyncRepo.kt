package ee.schimke.shokz.sync

import androidx.datastore.core.DataStore
import dev.zacsweers.metro.Inject
import ee.schimke.shokz.datastore.proto.SourceKind
import ee.schimke.shokz.datastore.proto.Settings
import ee.schimke.shokz.datastore.proto.StagedFile
import ee.schimke.shokz.datastore.proto.StagedStatus
import ee.schimke.shokz.datastore.proto.SyncPreferences
import ee.schimke.shokz.datastore.proto.SyncSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Inject
class SyncRepo(private val dataStore: DataStore<Settings>) {

    val sources: Flow<List<SyncSource>> = dataStore.data.map { it.sync_sources }
    val stagedFiles: Flow<List<StagedFile>> = dataStore.data.map { it.staged_files }
    val preferences: Flow<SyncPreferences?> = dataStore.data.map { it.sync_preferences }

    suspend fun addLocalDirectorySource(name: String, treeUri: String, subpath: String = "") {
        val source = SyncSource(
            id = Uuid.random().toString(),
            name = name,
            kind = SourceKind.LOCAL_DIRECTORY,
            location = treeUri,
            subpath = subpath,
        )
        dataStore.updateData { it.copy(sync_sources = it.sync_sources + source) }
    }

    suspend fun addNfsSource(name: String, host: String, exportPath: String) {
        val source = SyncSource(
            id = Uuid.random().toString(),
            name = name,
            kind = SourceKind.NFS_SHARE,
            location = "nfs://$host$exportPath",
            subpath = "",
        )
        dataStore.updateData { it.copy(sync_sources = it.sync_sources + source) }
    }

    suspend fun removeSource(id: String) {
        dataStore.updateData { settings ->
            settings.copy(
                sync_sources = settings.sync_sources.filterNot { it.id == id },
                staged_files = settings.staged_files.filterNot { it.source_id == id },
            )
        }
    }

    suspend fun stageFiles(items: List<StagedFile>) {
        dataStore.updateData { it.copy(staged_files = it.staged_files + items) }
    }

    suspend fun removeStaged(id: String) {
        dataStore.updateData { settings ->
            settings.copy(staged_files = settings.staged_files.filterNot { it.id == id })
        }
    }

    suspend fun clearCompleted() {
        dataStore.updateData { settings ->
            settings.copy(staged_files = settings.staged_files.filterNot { it.status == StagedStatus.COMPLETED })
        }
    }

    suspend fun resetAllToPending() {
        dataStore.updateData { settings ->
            settings.copy(staged_files = settings.staged_files.map {
                it.copy(status = StagedStatus.PENDING, error_message = "", bytes_transferred = 0)
            })
        }
    }

    suspend fun updateStaged(id: String, transform: (StagedFile) -> StagedFile) {
        dataStore.updateData { settings ->
            settings.copy(staged_files = settings.staged_files.map {
                if (it.id == id) transform(it) else it
            })
        }
    }

    suspend fun updatePreferences(transform: (SyncPreferences) -> SyncPreferences) {
        dataStore.updateData { settings ->
            settings.copy(sync_preferences = transform(settings.sync_preferences ?: SyncPreferences()))
        }
    }

    suspend fun getSource(id: String): SyncSource? = sources.first().firstOrNull { it.id == id }
    suspend fun getStaged(id: String): StagedFile? = stagedFiles.first().firstOrNull { it.id == id }
    suspend fun pendingFiles(): List<StagedFile> =
        stagedFiles.first().filter { it.status == StagedStatus.PENDING || it.status == StagedStatus.FAILED }

    fun newStagedId(): String = Uuid.random().toString()
}
