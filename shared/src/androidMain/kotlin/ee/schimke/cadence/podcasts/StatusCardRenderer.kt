package ee.schimke.cadence.podcasts

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Draws Home's status card from a Livery design: the design's template for the card's size, with
 * [StatusCardValues] bound into it, as a document to play. Null when the design (or any template)
 * can't be had or bound, and Home draws the card itself.
 */
fun interface StatusCardRenderer {
  suspend fun render(
    designId: String,
    values: StatusCardValues,
    widthDp: Int,
    heightDp: Int,
    density: Float,
    fontScale: Float,
  ): ByteArray?
}

/** Provided by the Android app from its Livery catalog; null in previews. */
val LocalStatusCardRenderer = staticCompositionLocalOf<StatusCardRenderer?> { null }
