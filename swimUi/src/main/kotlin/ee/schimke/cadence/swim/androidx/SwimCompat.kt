@file:Suppress("RestrictedApi", "RestrictedApiAndroidX")

package ee.schimke.cadence.swim.androidx

import androidx.compose.remote.creation.compose.layout.RemoteOffset
import androidx.compose.remote.creation.compose.state.RemotePaintingStyle
import androidx.compose.remote.creation.compose.state.RemoteStrokeCap
import androidx.compose.remote.creation.compose.state.rf

// The AndroidX alpha's spelling of what ../SwimCompat.kt names for rc-players' creation API. The
// rest of this package is generated from the drawing next to that file (generateAndroidxBinding).

internal val FillStyle = RemotePaintingStyle.Fill
internal val StrokeStyle = RemotePaintingStyle.Stroke
internal val RoundCap = RemoteStrokeCap.Round

internal fun offset(x: Float, y: Float) = RemoteOffset(x.rf, y.rf)
