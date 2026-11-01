import com.github.triplet.gradle.androidpublisher.ReleaseStatus

plugins {
  alias(libs.plugins.androidApplication)
  alias(libs.plugins.composeMultiplatform)
  alias(libs.plugins.composeCompiler)
  alias(libs.plugins.playPublisher)
}

val appVersionName = "0.1.12" // x-release-please-version
val appVersionCode =
  appVersionName
    .split(".", "-")
    .mapNotNull { it.toIntOrNull() }
    .let { parts ->
      1_000_000_000 +
        (parts.getOrNull(0) ?: 0) * 10_000 +
        (parts.getOrNull(1) ?: 0) * 100 +
        (parts.getOrNull(2) ?: 0)
    }

android {
  namespace = "ee.schimke.cadence.wear"
  sourceSets.getByName("main").res.directories.add(rootProject.file("branding/src/main/res").path)
  compileSdk = libs.versions.android.compileSdk.get().toInt()

  defaultConfig {
    // Data Layer endpoints must share the phone app's package name and signing key.
    applicationId = "ee.schimke.cadence"
    minSdk = libs.versions.android.minSdk.get().toInt()
    targetSdk = libs.versions.android.compileSdk.get().toInt()
    versionCode = appVersionCode
    versionName = appVersionName
  }

  val releaseKeystorePath = System.getenv("CADENCE_KEYSTORE_PATH")
  signingConfigs {
    if (releaseKeystorePath != null) {
      create("release") {
        storeFile = file(releaseKeystorePath)
        storePassword = System.getenv("CADENCE_KEYSTORE_PASSWORD")
        keyAlias = System.getenv("CADENCE_KEY_ALIAS")
        keyPassword = System.getenv("CADENCE_KEY_PASSWORD")
      }
    }
  }
  buildTypes {
    getByName("release") {
      if (releaseKeystorePath != null) {
        signingConfig = signingConfigs.getByName("release")
      }
    }
  }
}

play {
  track.set("wear:qa")
  defaultToAppBundles.set(true)
  releaseStatus.set(ReleaseStatus.COMPLETED)
  enabled.set(System.getenv("ANDROID_PUBLISHER_CREDENTIALS") != null)
}

dependencies {
  implementation(project(":rcsp"))
  implementation(project(":wearCompanionContract"))
  implementation(project(":swimUi"))
  implementation(libs.composeai.preview.annotations)
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
