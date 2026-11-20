plugins {
  alias(libs.plugins.androidTest)
  alias(libs.plugins.androidxBaselineProfile)
}

android {
  namespace = "ee.schimke.cadence.baseline.wear"
  compileSdk {
    version = release(libs.versions.android.compileSdk.get().toInt()) { minorApiLevel = 1 }
  }
  defaultConfig {
    minSdk = libs.versions.android.minSdk.get().toInt()
    targetSdk = libs.versions.android.targetSdk.get().toInt()
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }
  targetProjectPath = ":wearApp"
}

dependencies {
  implementation(libs.androidx.benchmark.macro.junit4)
  implementation(libs.androidx.test.junit)
  implementation(libs.androidx.runner)
}
