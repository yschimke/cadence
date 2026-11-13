@file:Suppress("RestrictedApi", "RestrictedApiAndroidX")

package ee.schimke.cadence.swim.androidx

import androidx.compose.remote.creation.compose.layout.RemoteOffset
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.StrokeCap

// The AndroidX alpha's spelling of what ../SwimCompat.kt names for rc-players' creation API. The
// rest of this package is generated from the drawing next to that file (generateAndroidxBinding).

internal val FillStyle = PaintingStyle.Fill
internal val StrokeStyle = PaintingStyle.Stroke
internal val RoundCap = StrokeCap.Round

internal fun offset(x: Float, y: Float) = RemoteOffset(x, y)
