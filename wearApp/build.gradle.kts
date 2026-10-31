plugins {
  alias(libs.plugins.androidApplication)
  alias(libs.plugins.composeMultiplatform)
  alias(libs.plugins.composeCompiler)
}

android {
  namespace = "ee.schimke.cadence.wear"
  compileSdk = libs.versions.android.compileSdk.get().toInt()

  defaultConfig {
    // Data Layer endpoints must share the phone app's package name and signing key.
    applicationId = "ee.schimke.cadence"
    minSdk = libs.versions.android.minSdk.get().toInt()
    targetSdk = libs.versions.android.compileSdk.get().toInt()
    versionCode = 1
    versionName = "0.1.0"
  }
}

dependencies {
  implementation(project(":rcsp"))
  implementation(project(":wearCompanionContract"))
  implementation(libs.androidx.activity.compose)
  implementation(libs.compose.runtime)
  implementation(libs.compose.foundation)
  implementation(libs.compose.ui)
  implementation(libs.compose.ui.tooling.preview)
  implementation(libs.material.icons.extended)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.play.services)
  implementation(libs.play.services.wearable)
  implementation(libs.horologist.media.ui.material3)
  implementation(libs.wear.compose.foundation)
  implementation(libs.wear.compose.material3)
  implementation(libs.wear.compose.ui.tooling)
  implementation(libs.compose.remote.creation)
  implementation(libs.compose.remote.creation.compose)
  implementation(libs.compose.remote.core)
  implementation(libs.compose.remote.tooling.preview)
  implementation(libs.glance.wear)
  implementation(libs.glance.wear.core)
  implementation(libs.glance.wear.tooling.preview)
  implementation(libs.wear.compose.remote.material3)

  testImplementation(libs.junit)
  testImplementation(libs.kotlin.test)
}
