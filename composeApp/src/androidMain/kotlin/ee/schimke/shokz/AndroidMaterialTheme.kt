package ee.schimke.shokz

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext

@Composable
fun AndroidMaterialTheme(content: @Composable () -> Unit) {
    val isDark = isSystemInDarkTheme()

    MaterialTheme(
        colorScheme = if (isDark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(
            LocalContext.current
        )
    ) {
        content()
    }
}