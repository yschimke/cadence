package ee.schimke.cadence.podcasts

import androidx.compose.runtime.Composable

@Composable
actual fun CloudSyncHomeCard(
  status: CloudSyncStatus,
  onOpen: () -> Unit,
  onRefresh: () -> Unit,
  onDownload: () -> Unit,
  onProcess: () -> Unit,
) = CloudSyncHomeFallback(status, onOpen, onRefresh, onDownload, onProcess)
