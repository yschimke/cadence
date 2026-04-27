@file:OptIn(DelicateMetroGradleApi::class)

import dev.zacsweers.metro.gradle.DelicateMetroGradleApi
import tapmoc.Severity

plugins {
  alias(libs.plugins.androidApplication)
  alias(libs.plugins.composeMultiplatform)
  alias(libs.plugins.composeCompiler)
  alias(libs.plugins.tapmoc)
  alias(libs.plugins.metro)
  alias(libs.plugins.playPublisher)
  alias(libs.plugins.composePreview)
}

composePreview {
  variant.set("debug")
  sdkVersion.set(35)
  enabled.set(true)
}

val appVersionName = "0.1.0" // x-release-please-version

// Pack MAJOR.MINOR.PATCH into a monotonic int. Caps at major < 22.
val appVersionCode: Int =
  run {
      val parts = appVersionName.split(".", "-").mapNotNull { it.toIntOrNull() }
      val major = parts.getOrNull(0) ?: 0
      val minor = parts.getOrNull(1) ?: 0
      val patch = parts.getOrNull(2) ?: 0
      major * 10_000 + minor * 100 + patch
    }
    .coerceAtLeast(1)

android {
  namespace = "ee.schimke.cadence"
  compileSdk = libs.versions.android.compileSdk.get().toInt()

  defaultConfig {
    applicationId = "ee.schimke.cadence"
    minSdk = libs.versions.android.minSdk.get().toInt()
    targetSdk = libs.versions.android.targetSdk.get().toInt()
    versionCode = appVersionCode
    versionName = appVersionName

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }
  packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }
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
      isMinifyEnabled = true
      isShrinkResources = true
      if (releaseKeystorePath != null) {
        signingConfig = signingConfigs.getByName("release")
      }
    }
  }
}

dependencies {
  implementation(project(":shared"))
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.core.splashscreen)
  implementation(libs.androidx.lifecycle.viewmodel)
  // Required on the application module's classpath because the Metro
  // dependency graph (CadenceAppGraph) is generated here and aggregates
  // bindings whose types reference DataStore / okio / WorkManager / ktor.
  implementation(libs.androidx.datastore)
  implementation(libs.androidx.work.runtime)
  implementation("com.squareup.okio:okio:3.17.0")

  // Compose deps used only by the preview composables in src/main/.../preview/.
  implementation(compose.runtime)
  implementation(compose.foundation)
  implementation(compose.material3)
  implementation(compose.materialIconsExtended)
  implementation(compose.ui)
  implementation(compose.preview)
  implementation(libs.androidx.ui.text.google.fonts)
  implementation(libs.ktor.client.core) // io.ktor.http.Url, used by BookmarksScreen previews

  androidTestImplementation(libs.junit)
  androidTestImplementation(libs.androidx.test.junit)
  androidTestImplementation(libs.androidx.runner)
}

play {
  track.set("internal")
  defaultToAppBundles.set(true)
  // Skip API calls in CI runs that build but don't publish (e.g. PRs).
  enabled.set(System.getenv("ANDROID_PUBLISHER_CREDENTIALS") != null)
}

tapmoc {
  java(21)
  kotlin(libs.versions.kotlin.get())
  checkDependencies()
  checkKotlinStdlibs(Severity.ERROR)
}

metro {
  debug.set(true)
  reportsDestination.set(layout.buildDirectory.dir("metro/reports"))
}
