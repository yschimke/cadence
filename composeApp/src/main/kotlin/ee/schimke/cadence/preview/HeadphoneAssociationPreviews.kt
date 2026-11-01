package ee.schimke.cadence.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import ee.schimke.cadence.AndroidMaterialTheme
import ee.schimke.cadence.podcasts.HeadphoneAssociationContent
import ee.schimke.cadence.podcasts.HeadphoneCandidate

@Preview(name = "Suggested headphone match", widthDp = 360, showBackground = true)
@Composable
internal fun HeadphoneAssociationPreview() {
  AndroidMaterialTheme {
    HeadphoneAssociationContent(
      linked = null,
      candidates = listOf(HeadphoneCandidate("00:11:22:33:44:55", "OpenSwim Pro by Shokz")),
      editing = true,
      onEdit = {},
      onLink = {},
      onRemove = {},
    )
  }
}
