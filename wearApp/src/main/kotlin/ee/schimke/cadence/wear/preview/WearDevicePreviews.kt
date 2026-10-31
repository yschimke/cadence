package ee.schimke.cadence.wear.preview

import androidx.compose.ui.tooling.preview.Preview

/** Pixel Watch 3 45 mm: 456 × 456 px at xhdpi. */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION)
@Preview(
  name = "Pixel Watch 3 · 45 mm",
  group = "Physical watches",
  device = "spec:width=228dp,height=228dp,dpi=320,isRound=true",
  showBackground = true,
  backgroundColor = 0xFF000000,
  showSystemUi = false,
)
annotation class PixelWatch45Preview

/** Galaxy Watch 7 44 mm: 480 × 480 px at xhdpi. */
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION)
@Preview(
  name = "Galaxy Watch 7 · 44 mm",
  group = "Physical watches",
  device = "spec:width=240dp,height=240dp,dpi=320,isRound=true",
  showBackground = true,
  backgroundColor = 0xFF000000,
  showSystemUi = false,
)
annotation class GalaxyWatch44Preview
