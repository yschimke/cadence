package ee.schimke.cadence.platform

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The home-screen widgets' designs, which Cadence publishes from its repository as a signed Livery
 * catalog. The app downloads a new catalog about once a day; [refresh] fetches it now, redraws the
 * widgets, and returns a line for the user saying what happened.
 */
class WidgetDesigns(val refresh: suspend () -> String)

/** Provided by the Android app; null on desktop, web and in previews, which hide the row. */
val LocalWidgetDesigns = staticCompositionLocalOf<WidgetDesigns?> { null }
