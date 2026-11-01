@file:OptIn(DelicateMetroGradleApi::class)

import dev.zacsweers.metro.gradle.DelicateMetroGradleApi
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

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
      dependencies {
        implementation(libs.androidx.datastore)
        // OkHttp drives the Google OAuth refresh + Drive v3 calls. Android ships neither
        // java.net.http nor a sync HTTP client of its own, so one dep covers both platforms.
        implementation(libs.okhttp)
      }
    }
    androidMain.get().dependsOn(jvmAndAndroidMain)
    val desktopMain by getting {
      dependsOn(jvmAndAndroidMain)
      dependencies {
        implementation(compose.desktop.currentOs)
        implementation(libs.usb4java)
      }
    }
    commonTest.dependencies {
      implementation(libs.kotlin.test)
      implementation(libs.appstate.transform.testing)
    }
    androidMain.dependencies {
      implementation(project(":rcsp"))
      implementation(project(":swimUi"))
      implementation(libs.media3.transformer)
      implementation(libs.media3.exoplayer)
      implementation(libs.media3.session)
      implementation(libs.compose.webview.multiplatform)
      implementation(libs.compose.ui.tooling.preview)
      implementation(libs.androidx.activity.compose)
      implementation(libs.androidx.core.splashscreen)
      implementation(libs.androidx.documentfile)
      implementation(libs.androidx.work.runtime)
      implementation(libs.rc.widget.launcher)
      // api so the AppAuth RedirectUriReceiverActivity reference in composeApp's manifest resolves
      // during Android lint — not just at runtime.
      api(libs.appauth)
    }
    commonMain.dependencies {
      implementation(libs.kotlinx.coroutines.core)
      implementation(libs.compose.runtime)
      implementation(libs.appstate.transform)
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

// The Wasm web build as a Wasmo app (static resources only); see docs/wasmo-app.md. The directory
// form installs in Wasmo's dev mode; the archive is the distributable .wasmo file.
val appVersion = "0.1.9" // x-release-please-version
val wasmoVersion =
  appVersion.split(".").map(String::toLong).let { (major, minor, patch) ->
    major * 1_000_000 + minor * 1_000 + patch
  }
val wasmoAppDirectory =
  tasks.register<Sync>("wasmoAppDirectory") {
    val version = wasmoVersion
    inputs.property("version", version)
    into(layout.buildDirectory.dir("wasmo/dir/cadence.wasmo"))
    from("wasmo/wasmo-manifest.toml") { expand("version" to version) }
    // The app page is www/app.html, not index.html: www-public/index.html answers "/" for every
    // visitor (Wasmo serves www-public/ first) and sends the owner on to app.html.
    into("www") {
      from(tasks.named("wasmJsBrowserDistribution"))
      from("wasmo/wasm-mime-fallback.js", "wasmo/cadence-backend.js")
      filesMatching("index.html") {
        filter { line ->
          line
            // cadence-backend.js seeds settings from the Wasmo install, then loads cadence.js.
            .replace(
              "<script src=\"cadence.js\"></script>",
              "<script src=\"cadence-backend.js\"></script>",
            )
            .replace(
              "<script src=\"cadence-platform.js\">",
              "<script src=\"wasm-mime-fallback.js\"></script><script src=\"cadence-platform.js\">",
            )
            .replace(
              "<title>Cadence</title>",
              "<title>Cadence</title>\n" +
                "<link rel=\"manifest\" href=\"/manifest.webmanifest\">\n" +
                "<link rel=\"icon\" href=\"/icon-192.png\">\n" +
                "<link rel=\"apple-touch-icon\" href=\"/apple-touch-icon.png\">\n" +
                "<meta name=\"theme-color\" content=\"#1a1b2e\">",
            )
        }
        name = "app.html"
      }
    }
    val icon = rootProject.file("composeApp/src/main/play/listings/en-GB/graphics/icon/icon.png")
    into("www/assets") { from(icon) }
    // Public: the landing page, the web app manifest and its icons. The browser fetches a manifest
    // without credentials, so it must not sit behind the owner-only www/.
    into("www-public") {
      from("wasmo/www-public")
      from(icon) { rename { "icon-512.png" } }
    }
  }

// A plain Zip task writes a directory entry for every folder ("www/", "www/assets/", …). Wasmo's
// installer writes each zip entry to its object store as-is, so a directory entry lands as a file
// that blocks the files beneath it and the install fails. Write only file entries, as Wasmo's own
// packager does.
tasks.register("wasmoApp") {
  description = "Packages the Wasm web build as an installable .wasmo archive."
  // Locals, not top-level script vals: a doLast action that captures a top-level `val` pulls in the
  // build-script object, which the configuration cache cannot serialize.
  val inputDir = layout.buildDirectory.dir("wasmo/dir/cadence.wasmo")
  val archiveFile = layout.buildDirectory.file("wasmo/cadence.wasmo")
  dependsOn(wasmoAppDirectory)
  inputs.dir(inputDir)
  outputs.file(archiveFile)
  doLast {
    val root = inputDir.get().asFile
    val archive = archiveFile.get().asFile
    archive.parentFile.mkdirs()
    ZipOutputStream(archive.outputStream().buffered()).use { zip ->
      root
        .walkTopDown()
        .filter { it.isFile }
        .sortedBy { it.relativeTo(root).invariantSeparatorsPath }
        .forEach { file ->
          // A fixed timestamp keeps the archive byte-for-byte reproducible across builds.
          val entry = ZipEntry(file.relativeTo(root).invariantSeparatorsPath)
          entry.time = 0L
          zip.putNextEntry(entry)
          file.inputStream().use { it.copyTo(zip) }
          zip.closeEntry()
        }
    }
  }
}
