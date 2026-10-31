package ee.schimke.cadence.sync

/**
 * Picks a backing [ManifestStore] at each call based on a flag. Lets DesktopServices wire the
 * Drive-backed store without changing PortableSync's constructor when the user toggles cross-device
 * sync on and off.
 */
class SwitchingManifestStore(private val chooser: () -> ManifestStore) : ManifestStore {
  override suspend fun load(deviceId: String) = chooser().load(deviceId)

  override suspend fun recordAll(deviceId: String, entries: Map<String, ManifestEntry>) {
    chooser().recordAll(deviceId, entries)
  }

  override suspend fun syncRemote(deviceId: String) {
    chooser().syncRemote(deviceId)
  }
}
