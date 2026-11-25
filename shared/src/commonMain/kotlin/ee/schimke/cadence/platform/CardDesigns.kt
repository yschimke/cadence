package ee.schimke.cadence.platform

import androidx.compose.runtime.staticCompositionLocalOf

/** The status card design Home draws until another is picked: the swimming pool. */
const val DEFAULT_CARD_DESIGN = "status-card-swimming"

/** A status card design Settings offers: its Livery design id and its name. */
data class CardDesign(val id: String, val title: String)

/**
 * The status card designs in the Livery catalog on this device: the newest signed download, else
 * the catalog built into the app. [list] never throws; it is empty where there is no catalog.
 */
class CardDesigns(val list: suspend () -> List<CardDesign>)

/** Provided by the Android app; null on desktop, web and in previews, which hide the picker. */
val LocalCardDesigns = staticCompositionLocalOf<CardDesigns?> { null }
