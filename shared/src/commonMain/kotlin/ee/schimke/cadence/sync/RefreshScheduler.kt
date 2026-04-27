package ee.schimke.cadence.sync

interface RefreshScheduler {
  /** Schedule (or update) the periodic refresh for [profile]. */
  fun schedule(profile: ee.schimke.cadence.datastore.proto.SyncProfile)

  /** Cancel any periodic work for the given profile id. */
  fun cancel(profileId: String)

  /** Enqueue a one-off refresh that runs immediately, ignoring network constraints. */
  fun runNow(profileId: String)
}
