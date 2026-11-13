package ee.schimke.cadence.preview

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import ee.schimke.composeai.rccreation.compose.capture.RemoteCreationDisplayInfo
import ee.schimke.composeai.rccreation.compose.capture.captureCommonRemoteDocument
import ee.schimke.composeai.rccreation.compose.layout.RemoteComposable
import ee.schimke.composeai.rcplayer.compose.RcComposePlayer
import kotlin.math.roundToInt
import kotlinx.coroutines.runBlocking

/**
 * A preview of Remote Compose [content] written against rc-players' Compose Multiplatform creation
 * API (`ee.schimke.composeai.rccreation`): captured at the preview's size and played by rc-players'
 * Compose player. The counterpart of AndroidX's `RemoteContentPreview`, which previews content
 * written against AndroidX's creation API (the podcast and cloud sync widgets).
 *
 * The capture is taken while composing, so a renderer's first frame has the document: this is for
 * previews only. A new [key] (or size) captures again, which is how an animated preview moves.
 */
@Composable
internal fun RcContentPreview(
  modifier: Modifier = Modifier,
  key: Any? = null,
  content: @Composable @RemoteComposable () -> Unit,
) {
  BoxWithConstraints(modifier) {
    val densityDpi = (LocalDensity.current.density * 160).roundToInt()
    val width = constraints.maxWidth.coerceAtLeast(1)
    val height = constraints.maxHeight.coerceAtLeast(1)
    val document =
      remember(width, height, densityDpi, key) {
        runBlocking {
          captureCommonRemoteDocument(
            RemoteCreationDisplayInfo(width, height, densityDpi),
            content = content,
          )
        }
      }
    RcComposePlayer(document, Modifier.fillMaxSize())
  }
}
