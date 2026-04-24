package ee.schimke.shokz.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import ee.schimke.shokz.metro.metroViewModel
import org.jetbrains.compose.resources.painterResource
import shokz.composeapp.generated.resources.Res
import shokz.composeapp.generated.resources.compose_multiplatform

@Composable
fun HomeScreen(onFileExplorer: () -> Unit, onBookmarks: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel = metroViewModel<HomeViewModel>()

    HomeContent(onFileExplorer, onBookmarks, modifier)
}

@Composable
internal fun HomeContent(
    onFileExplorer: () -> Unit,
    onBookmarks: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth()
            .safeContentPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(onClick = onFileExplorer) {
            Text("File Explorer")
        }
        Button(onClick = onBookmarks) {
            Text("Bookmarks")
        }
    }
}