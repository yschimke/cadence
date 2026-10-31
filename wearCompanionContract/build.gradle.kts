plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidKotlinMultiplatformLibrary)
}

kotlin {
  androidLibrary {
    namespace = "ee.schimke.cadence.wearcontract"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    minSdk = libs.versions.android.minSdk.get().toInt()
  }

  jvm("desktop")

  sourceSets {
    commonMain.dependencies { implementation(project(":rcsp")) }
    commonTest.dependencies { implementation(libs.kotlin.test) }
  }
}
