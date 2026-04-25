package ee.schimke.cadence.sync

import androidx.datastore.core.DataStore
import dev.zacsweers.metro.Inject
import ee.schimke.cadence.datastore.proto.NetworkConstraint
import ee.schimke.cadence.datastore.proto.Settings
import ee.schimke.cadence.datastore.proto.SourceKind
import ee.schimke.cadence.datastore.proto.SyncPreferences
import ee.schimke.cadence.datastore.proto.SyncProfile
import ee.schimke.cadence.datastore.proto.SyncSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Inject
class SyncRepo(private val dataStore: DataStore<Settings>) {

    val sources: Flow<List<SyncSource>> = dataStore.data.map { it.sync_sources }
    val profiles: Flow<List<SyncProfile>> = dataStore.data.map { it.sync_profiles }
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
                sync_profiles = settings.sync_profiles.map { profile ->
                    profile.copy(source_ids = profile.source_ids.filterNot { it == id })
                },
            )
        }
    }

    suspend fun addProfile(
        name: String,
        sourceIds: List<String>,
        refreshIntervalMinutes: Int = 60,
        networkConstraint: NetworkConstraint = NetworkConstraint.UNMETERED,
    ): SyncProfile {
        val id = Uuid.random().toString()
        val subpath = sanitizeSubpath(name).ifBlank { id }
        var created: SyncProfile = SyncProfile()
        dataStore.updateData { settings ->
            // First profile created defaults to auto-refresh on; subsequent profiles
            // default to manual-only so the user explicitly opts in to extra background work.
            val isFirst = settings.sync_profiles.isEmpty()
            val profile = SyncProfile(
                id = id,
                name = name.ifBlank { "Untitled" },
                source_ids = sourceIds,
                staging_subpath = subpath,
                refresh_interval_minutes = refreshIntervalMinutes,
                network_constraint = networkConstraint,
                auto_refresh = isFirst,
            )
            created = profile
            settings.copy(sync_profiles = settings.sync_profiles + profile)
        }
        return created
    }

    suspend fun updateProfile(id: String, transform: (SyncProfile) -> SyncProfile) {
        dataStore.updateData { settings ->
            settings.copy(sync_profiles = settings.sync_profiles.map {
                if (it.id == id) transform(it) else it
            })
        }
    }

    suspend fun removeProfile(id: String) {
        dataStore.updateData { settings ->
            settings.copy(sync_profiles = settings.sync_profiles.filterNot { it.id == id })
        }
    }

    suspend fun setAutoRefresh(id: String, enabled: Boolean) {
        updateProfile(id) { it.copy(auto_refresh = enabled) }
    }

    suspend fun recordRefreshSuccess(id: String, isoTimestamp: String) {
        updateProfile(id) { it.copy(last_refreshed_at = isoTimestamp, last_error = "") }
    }

    suspend fun recordRefreshFailure(id: String, error: String) {
        updateProfile(id) { it.copy(last_error = error) }
    }

    suspend fun updatePreferences(transform: (SyncPreferences) -> SyncPreferences) {
        dataStore.updateData { settings ->
            settings.copy(sync_preferences = transform(settings.sync_preferences ?: SyncPreferences()))
        }
    }

    suspend fun getSource(id: String): SyncSource? = sources.first().firstOrNull { it.id == id }
    suspend fun getProfile(id: String): SyncProfile? = profiles.first().firstOrNull { it.id == id }
    suspend fun listProfiles(): List<SyncProfile> = profiles.first()

    private fun sanitizeSubpath(name: String): String =
        name.lowercase()
            .replace(Regex("[^a-z0-9._-]+"), "-")
            .trim('-', '.')
            .take(48)
}
