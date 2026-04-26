plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidKotlinMultiplatformLibrary)
  alias(libs.plugins.composeMultiplatform)
  alias(libs.plugins.composeCompiler)
  kotlin("plugin.serialization").version(libs.versions.kotlin)
  id("com.gradleup.tapmoc").version(libs.versions.tapmoc)
  id("dev.zacsweers.metro").version(libs.versions.metro)
  id("com.squareup.wire").version(libs.versions.wire)
  id("ee.schimke.composeai.preview").version("0.8.4")
}

composePreview {
  variant.set("debug")
  sdkVersion.set(35)
  enabled.set(true)
}

kotlin {
  androidLibrary {
    namespace = "ee.schimke.cadence.shared"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    minSdk = libs.versions.android.minSdk.get().toInt()

    withDeviceTestBuilder { sourceSetTreeName = "test" }
      .configure { instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
  }

  sourceSets {
    androidMain.dependencies {
      implementation(compose.preview)
      implementation(libs.androidx.activity.compose)
      implementation(libs.androidx.core.splashscreen)
      implementation(libs.modernstorage.permissions)
      implementation(libs.modernstorage.storage)
      implementation(libs.androidx.documentfile)
      implementation(libs.androidx.work.runtime)
    }
    commonMain.dependencies {
      implementation(compose.runtime)
      implementation(compose.foundation)
      implementation(compose.material3)
      implementation(compose.material3AdaptiveNavigationSuite)
      implementation(libs.navigation.compose)
      implementation(compose.ui)
      implementation(compose.components.resources)
      implementation(compose.components.uiToolingPreview)
      implementation(libs.androidx.lifecycle.viewmodel)
      implementation(libs.androidx.lifecycle.runtime.compose)
      implementation(libs.kotlinx.serialization.json)
      implementation("com.squareup.okio:okio:3.17.0")
      implementation(libs.androidx.datastore)
      implementation(libs.bonsai.core)
      implementation(libs.bonsai.file.system)
      implementation(libs.material.icons.core)
      implementation(libs.material.icons.extended)
      implementation(libs.compose.webview.multiplatform)
      implementation(libs.ktor.client.core)
    }
    getByName("androidDeviceTest").dependencies {
      implementation(libs.junit)
      implementation(libs.kotlin.test.junit)
      implementation(libs.androidx.test.junit)
      implementation(libs.androidx.espresso.core)
      implementation(libs.androidx.runner)
    }
  }
}

tapmoc {
  java(17)
  kotlin(libs.versions.kotlin.get())
}

wire {
  sourcePath { srcDir("src/commonMain/proto") }

  kotlin {}
}

metro {
  debug.set(true)
  reportsDestination.set(layout.buildDirectory.dir("metro/reports"))
}
