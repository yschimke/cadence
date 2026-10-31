plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidKotlinMultiplatformLibrary)
  `maven-publish`
}

group = "ee.schimke.cadence"

version = "0.1.0-SNAPSHOT"

kotlin {
  androidLibrary {
    namespace = "ee.schimke.rcsp"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    minSdk = libs.versions.android.minSdk.get().toInt()
  }

  jvm("desktop")

  sourceSets { commonTest.dependencies { implementation(libs.kotlin.test) } }
}
