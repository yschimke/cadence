@file:OptIn(DelicateMetroGradleApi::class)

import com.github.triplet.gradle.androidpublisher.ReleaseStatus
import com.gradleup.tapmoc.Severity
import dev.zacsweers.metro.gradle.DelicateMetroGradleApi

plugins {
  alias(libs.plugins.androidApplication)
  alias(libs.plugins.composeMultiplatform)
  alias(libs.plugins.composeCompiler)
  alias(libs.plugins.tapmoc)
  alias(libs.plugins.metro)
  alias(libs.plugins.playPublisher)
}

// Configuration is injected before Gradle starts in release CI. Firebase's
// provider initializes Analytics and uncaught-crash reporting automatically.
val firebaseEnabled = file("google-services.json").isFile

if (firebaseEnabled) {
  apply(plugin = "com.google.gms.google-services")
  apply(plugin = "com.google.firebase.crashlytics")
}

val appVersionName = "0.1.7" // x-release-please-version

// Pack MAJOR.MINOR.PATCH into a monotonic int. Caps at major < 22.
val appVersionCode: Int = run {
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
  if (firebaseEnabled) {
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
  }

  implementation(project(":shared"))
  implementation(project(":rcsp"))
  implementation(project(":wearCompanionContract"))
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.play.services.wearable)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.core.splashscreen)
  implementation(libs.androidx.lifecycle.viewmodel)
  // Required on the application module's classpath because the Metro
  // dependency graph (CadenceAppGraph) is generated here and aggregates
  // bindings whose types reference DataStore / okio / WorkManager / ktor.
  implementation(libs.androidx.datastore)
  implementation(libs.androidx.work.runtime)
  implementation(libs.okio)

  // Compose deps used only by the preview composables in src/main/.../preview/.
  implementation(libs.compose.runtime)
  implementation(libs.compose.foundation)
  implementation(libs.compose.material3)
  implementation(libs.material.icons.extended)
  implementation(libs.compose.ui)
  implementation(libs.compose.ui.tooling.preview)
  implementation(libs.androidx.ui.text.google.fonts)
  implementation(libs.ktor.client.core) // io.ktor.http.Url, used by BookmarksScreen previews

  androidTestImplementation(libs.junit)
  androidTestImplementation(libs.androidx.test.junit)
  androidTestImplementation(libs.androidx.runner)
}

play {
  track.set("internal")
  defaultToAppBundles.set(true)
  releaseStatus.set(ReleaseStatus.COMPLETED)
  // Skip API calls in CI runs that build but don't publish (e.g. PRs).
  enabled.set(System.getenv("ANDROID_PUBLISHER_CREDENTIALS") != null)
}

tapmoc {
  // Java 17 bytecode (class-file v61) so the JDK-17 preview render daemon
  // (Robolectric, preview.coo.ee) can load this module's classes; the build
  // toolchain stays on JDK 21. v65 (JDK 21) bytecode throws
  // UnsupportedClassVersionError in the render daemon.
  java(17)
  kotlin(libs.versions.kotlin.get())
  checkDependencies()
  checkKotlinStdlibs(Severity.ERROR)
}

metro {
  debug.set(false)
  reportsDestination.set(layout.buildDirectory.dir("metro/reports"))
}
