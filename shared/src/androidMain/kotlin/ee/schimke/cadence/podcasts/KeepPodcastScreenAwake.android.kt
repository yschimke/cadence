package ee.schimke.cadence.podcasts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

@Composable
internal actual fun KeepPodcastScreenAwake(active: Boolean) {
  val view = LocalView.current
  DisposableEffect(view, active) {
    val previous = view.keepScreenOn
    if (active) view.keepScreenOn = true
    onDispose { view.keepScreenOn = previous }
  }
}
