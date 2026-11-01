package ee.schimke.cadence.podcasts

import androidx.compose.runtime.Composable

/** Keep an active foreground preparation or copy from being suspended by screen timeout. */
@Composable internal expect fun KeepPodcastScreenAwake(active: Boolean)
