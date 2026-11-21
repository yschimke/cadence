package ee.schimke.cadence.podcasts

import androidx.compose.runtime.Composable

@Composable
actual fun PodcastSyncHomeCard(
  copied: String,
  available: String,
  battery: String?,
  ready: Boolean,
  showSwimming: Boolean,
  headline: String?,
  source: String?,
  attention: Boolean,
) {
  PodcastSyncHomeFallback(copied, available, battery, headline, source)
}
