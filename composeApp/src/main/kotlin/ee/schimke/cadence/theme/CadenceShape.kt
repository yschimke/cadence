package ee.schimke.cadence.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Cadence shapes — slightly softer than Material 3 defaults so cards and pills
 * feel rounded enough to read as friendly without losing the technical edge.
 *
 * Shared between [CadenceTheme] and [AndroidMaterialTheme] (via the same
 * Material 3 [Shapes] type) so theme switching only swaps colour and type, not
 * geometry.
 */
internal val CadenceShapes: Shapes =
  Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
  )
