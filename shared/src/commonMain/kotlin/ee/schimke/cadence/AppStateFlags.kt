package ee.schimke.cadence

/**
 * Whether HomeViewModel and FileSyncViewModel build their UI state with `androidx.appstate`'s
 * `transform` (true) or with the `combine`/`stateIn` chains they used before (false).
 *
 * Off while the MainActivity render shows the Library with a blank body on the transform path:
 * HomeViewModel's `loaded` never turns true there, for a reason not yet found.
 */
internal const val UseAppStateTransform = false
