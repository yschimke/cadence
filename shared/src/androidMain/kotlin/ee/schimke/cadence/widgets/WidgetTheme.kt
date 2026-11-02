package ee.schimke.cadence.widgets

import android.content.Context
import android.content.res.Configuration
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import ee.schimke.cadence.swim.WaterColors

/** The system's dynamic Material scheme for the current light or dark mode. */
fun Context.systemWidgetColors(): WidgetColors {
  val dark =
    resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
      Configuration.UI_MODE_NIGHT_YES
  return WidgetColors.from(
    if (dark) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this)
  )
}

/** Swim strip that sits on the widget surface: night water on dark chrome, pool on light. */
fun WidgetColors.waterColors(): WaterColors =
  if (isDark) WaterColors.Night else WaterColors.Pool
