package ee.schimke.cadence.preview

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ee.schimke.cadence.theme.CadenceTheme

// A theme-foundation showcase: colour roles, a tonal-elevation strip and a type
// ramp rendered as a single tile, for the custom CadenceTheme and the untinted
// Material 3 baseline (light + dark each). Published in the design catalog so
// the theme foundations sit alongside the screens and components that use them.

@Composable
private fun RowScope.Swatch(label: String, color: Color, onColor: Color) {
  Box(
    Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(8.dp)).background(color),
    contentAlignment = Alignment.Center,
  ) {
    Text(label, color = onColor, style = MaterialTheme.typography.labelSmall)
  }
}

@Composable
private fun ThemeFoundation(title: String, tagline: String) {
  val cs = MaterialTheme.colorScheme
  Surface(color = cs.background) {
    Column(
      Modifier.fillMaxWidth().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Column {
        Text(title, style = MaterialTheme.typography.titleLarge, color = cs.primary)
        Text(tagline, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
      }
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Swatch("P", cs.primary, cs.onPrimary)
        Swatch("PC", cs.primaryContainer, cs.onPrimaryContainer)
        Swatch("S", cs.secondary, cs.onSecondary)
        Swatch("T", cs.tertiary, cs.onTertiary)
        Swatch("Sv", cs.surfaceVariant, cs.onSurfaceVariant)
        Swatch("E", cs.error, cs.onError)
      }
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (elevation in listOf(0, 1, 2, 3, 6, 12)) {
          Surface(
            tonalElevation = elevation.dp,
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(1.dp, cs.outlineVariant),
            modifier = Modifier.weight(1f).height(32.dp),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Text("${elevation}dp", style = MaterialTheme.typography.labelSmall)
            }
          }
        }
      }
      Column {
        Text("Display", style = MaterialTheme.typography.displaySmall, color = cs.onBackground)
        Text("Headline", style = MaterialTheme.typography.headlineSmall, color = cs.onBackground)
        Text("Title", style = MaterialTheme.typography.titleMedium, color = cs.onBackground)
        Text(
          "Body — the quick brown fox jumps over the lazy dog",
          style = MaterialTheme.typography.bodyMedium,
          color = cs.onSurfaceVariant,
        )
        Text("LABEL", style = MaterialTheme.typography.labelLarge, color = cs.primary)
      }
    }
  }
}

@Preview(name = "Foundation — Cadence light", showBackground = true)
@Composable
internal fun FoundationCadenceLightPreview() {
  CadenceTheme(darkTheme = false) {
    ThemeFoundation("Cadence", "Light · Coastal Blue · Manrope / Inter")
  }
}

@Preview(name = "Foundation — Cadence dark", showBackground = true)
@Composable
internal fun FoundationCadenceDarkPreview() {
  CadenceTheme(darkTheme = true) {
    ThemeFoundation("Cadence", "Dark · Coastal Blue · Manrope / Inter")
  }
}

@Preview(name = "Foundation — Material 3 light", showBackground = true)
@Composable
internal fun FoundationMaterial3LightPreview() {
  MaterialTheme(colorScheme = lightColorScheme()) {
    ThemeFoundation("Material 3", "Light · baseline (untinted) reference")
  }
}

@Preview(name = "Foundation — Material 3 dark", showBackground = true)
@Composable
internal fun FoundationMaterial3DarkPreview() {
  MaterialTheme(colorScheme = darkColorScheme()) {
    ThemeFoundation("Material 3", "Dark · baseline (untinted) reference")
  }
}
