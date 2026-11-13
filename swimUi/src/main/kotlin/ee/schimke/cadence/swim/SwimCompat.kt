package ee.schimke.cadence.swim

import ee.schimke.composeai.rccreation.compose.layout.RemoteOffset
import ee.schimke.composeai.rccreation.compose.state.RemotePaintingStyle
import ee.schimke.composeai.rccreation.compose.state.RemoteStrokeCap
import ee.schimke.composeai.rccreation.compose.state.rf

// What rc-players' creation API (as androidx-main has it) and the pinned AndroidX alpha spell
// differently, for the drawing both are built from: the generated AndroidX binding has its own copy
// of this file (androidx/SwimCompat.kt), which generateAndroidxBinding leaves alone.

internal val FillStyle = RemotePaintingStyle.Fill
internal val StrokeStyle = RemotePaintingStyle.Stroke
internal val RoundCap = RemoteStrokeCap.Round

internal fun offset(x: Float, y: Float) = RemoteOffset(x.rf, y.rf)
