package ee.schimke.cadence.metro

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModelProvider

@Composable
actual fun metroViewModelProviderFactory(): ViewModelProvider.Factory = LocalPortableFactory.current
