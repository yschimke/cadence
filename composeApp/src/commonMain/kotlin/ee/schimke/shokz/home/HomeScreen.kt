package ee.schimke.shokz.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import ee.schimke.shokz.home.Greeting
import ee.schimke.shokz.metro.metroViewModel
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.ui.tooling.preview.Preview

import shokz.composeapp.generated.resources.Res
import shokz.composeapp.generated.resources.compose_multiplatform

@Composable
fun HomeScreen(onFileExplorer: () -> Unit) {
    val viewModel = metroViewModel<HomeViewModel>()

        var showContent by remember { mutableStateOf(true) }
        Column(
            modifier = Modifier.fillMaxWidth()
                .safeContentPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(onClick = onFileExplorer) {
                Text("File Explorer")
            }
            AnimatedVisibility(visible = showContent) {
                val greeting = remember { viewModel.greeting() }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(painterResource(Res.drawable.compose_multiplatform), null)
                    Text("Compose: $greeting")
                }
            }
        }
}