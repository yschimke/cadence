plugins {
  alias(libs.plugins.androidLibrary)
  alias(libs.plugins.composeMultiplatform)
  alias(libs.plugins.composeCompiler)
}

// Swimming-themed Remote Compose drawing shared by the launcher widget (:shared) and the Wear
// widgets (:wearApp). Everything here stays inside the operations both PROFILE_WIDGETS (v7) and
// PROFILE_WEAR_WIDGETS accept: canvas, paths, matrix transforms, float expressions over time and
// loops — no clip paths, particles or touch expressions.
android {
  namespace = "ee.schimke.cadence.swim"
  compileSdk = libs.versions.android.compileSdk.get().toInt()
  defaultConfig { minSdk = libs.versions.android.minSdk.get().toInt() }
}

dependencies {
  api(libs.compose.remote.creation.compose)
  implementation(libs.compose.remote.creation)
  implementation(libs.compose.remote.core)
  implementation(libs.compose.runtime)
  implementation(libs.compose.ui)
  implementation(libs.compose.foundation)
  implementation(libs.compose.ui.tooling.preview)
  implementation(libs.compose.remote.tooling.preview)
  implementation(libs.composeai.preview.annotations)

  testImplementation(libs.junit)
  testImplementation(libs.kotlin.test.junit)
}
