@file:OptIn(DelicateMetroGradleApi::class)

import dev.zacsweers.metro.gradle.DelicateMetroGradleApi

plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidKotlinMultiplatformLibrary)
  alias(libs.plugins.composeMultiplatform)
  alias(libs.plugins.composeCompiler)
  kotlin("plugin.serialization").version(libs.versions.kotlin)
  id("com.gradleup.tapmoc").version(libs.versions.tapmoc)
  id("dev.zacsweers.metro").version(libs.versions.metro)
  id("com.squareup.wire").version(libs.versions.wire)
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

// modernstorage-storage 1.0.0-alpha11 declares espresso-intents as a runtime
// dependency, which drags androidx.test:runner onto the production classpath
// and causes a strict-version collision with the catalog runner under AGP's
// consistent resolution. Strip it everywhere — neither runtime code nor the
// device-test source set (which uses espresso-core directly) needs it.
configurations.configureEach {
  exclude(group = "androidx.test.espresso", module = "espresso-intents")
}

tapmoc {
  java(21)
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
