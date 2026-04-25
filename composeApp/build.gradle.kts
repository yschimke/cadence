plugins {
  alias(libs.plugins.androidApplication)
  id("com.gradleup.tapmoc").version(libs.versions.tapmoc)
  id("dev.zacsweers.metro").version(libs.versions.metro)
  alias(libs.plugins.playPublisher)
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
}

play {
  track.set("internal")
  defaultToAppBundles.set(true)
  // Skip API calls in CI runs that build but don't publish (e.g. PRs).
  enabled.set(System.getenv("ANDROID_PUBLISHER_CREDENTIALS") != null)
}

tapmoc {
  java(17)
  kotlin(libs.versions.kotlin.get())
}

metro {
  debug.set(true)
  reportsDestination.set(layout.buildDirectory.dir("metro/reports"))
}
