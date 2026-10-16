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

  jvm("desktop")
  wasmJs {
    browser { commonWebpackConfig { outputFileName = "cadence.js" } }
    binaries.executable()
  }

  sourceSets {
    val jvmAndAndroidMain by creating {
      dependsOn(commonMain.get())
      dependencies { implementation(libs.androidx.datastore) }
    }
    androidMain.get().dependsOn(jvmAndAndroidMain)
    val desktopMain by getting {
      dependsOn(jvmAndAndroidMain)
      dependencies {
        implementation(compose.desktop.currentOs)
        implementation(libs.usb4java)
      }
    }
    commonTest.dependencies { implementation(libs.kotlin.test) }
    androidMain.dependencies {
      implementation(libs.compose.webview.multiplatform)
      implementation(libs.compose.ui.tooling.preview)
      implementation(libs.androidx.activity.compose)
      implementation(libs.androidx.core.splashscreen)
      implementation(libs.androidx.documentfile)
      implementation(libs.androidx.work.runtime)
    }
    commonMain.dependencies {
      implementation(libs.kotlinx.coroutines.core)
      implementation(libs.compose.runtime)
      implementation(libs.compose.foundation)
      implementation(libs.compose.material3)
      implementation(libs.navigation.compose)
      implementation(libs.compose.ui)
      implementation(libs.compose.components.resources)
      implementation(libs.compose.components.ui.tooling.preview)
      implementation(libs.androidx.lifecycle.viewmodel)
      implementation(libs.androidx.lifecycle.viewmodel.compose)
      implementation(libs.androidx.lifecycle.runtime.compose)
      implementation(libs.kotlinx.serialization.json)
      implementation(libs.okio)
      implementation(libs.material.icons.core)
      implementation(libs.material.icons.extended)
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
  // Java 17 bytecode (class-file v61) so the JDK-17 preview render daemon
  // (Robolectric, preview.coo.ee) can load this module's classes; the build
  // toolchain stays on JDK 21. v65 (JDK 21) bytecode throws
  // UnsupportedClassVersionError in the render daemon.
  java(17)
  kotlin(libs.versions.kotlin.get())
}

wire {
  sourcePath { srcDir("src/commonMain/proto") }

  kotlin {}
}

metro {
  debug.set(false)
  reportsDestination.set(layout.buildDirectory.dir("metro/reports"))
}
