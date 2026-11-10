plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.composeMultiplatform)
  alias(libs.plugins.composeCompiler)
}

val desktopVersion = "0.1.28" // x-release-please-version
val distributionVersion = providers.gradleProperty("desktopVersion").getOrElse(desktopVersion)

// Google OAuth credentials baked into release builds. CI passes
// `-PcadenceGoogleClientId=...` / `-PcadenceGoogleClientSecret=...` from GitHub secrets so end
// users don't need their own Google Cloud project. Dev builds leave them empty; the
// Cross-device sync card then shows the self-build setup instructions.
val bundledGoogleClientId = providers.gradleProperty("cadenceGoogleClientId").getOrElse("")
val bundledGoogleClientSecret = providers.gradleProperty("cadenceGoogleClientSecret").getOrElse("")

val generateBuildConfig =
  tasks.register("generateBuildConfig") {
    val outDir = layout.buildDirectory.dir("generated/source/buildConfig/jvmMain/kotlin")
    val id = bundledGoogleClientId
    val secret = bundledGoogleClientSecret
    inputs.property("googleClientId", id)
    inputs.property("googleClientSecret", secret)
    outputs.dir(outDir)
    doLast {
      val file = outDir.get().file("ee/schimke/cadence/desktop/BuildConfig.kt").asFile
      file.parentFile.mkdirs()
      file.writeText(
        """
        package ee.schimke.cadence.desktop

        /** Generated from gradle properties. Empty strings mean "not bundled by this build". */
        internal object BuildConfig {
          const val GOOGLE_CLIENT_ID: String = "${id.replace("\"", "\\\"")}"
          const val GOOGLE_CLIENT_SECRET: String = "${secret.replace("\"", "\\\"")}"
        }
        """
          .trimIndent() + "\n",
      )
    }
  }

kotlin {
  jvm()
  sourceSets {
    jvmMain {
      kotlin.srcDir(generateBuildConfig.map { it.outputs.files.singleFile })
      dependencies {
        implementation(projects.shared)
        implementation(libs.androidx.lifecycle.viewmodel)
        // Dispatchers.Main for viewModelScope/stateIn on the AWT event thread. Compose Desktop
        // does not bring it in transitively; without it the app starts on an "Error" window.
        implementation(libs.kotlinx.coroutines.swing)
        implementation(compose.desktop.currentOs)
      }
    }
    jvmTest {
      dependencies {
        implementation(libs.kotlin.test)
        implementation(libs.kotlin.test.junit)
      }
    }
  }
}

compose.desktop {
  application {
    mainClass = "ee.schimke.cadence.desktop.MainKt"
    if (System.getProperty("os.name").contains("Linux")) {
      // Main.kt names the X11 window class (WM_CLASS) so docks match the window to
      // cadence-desktop.desktop.
      jvmArgs += "--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED"
    }
    nativeDistributions {
      targetFormats(
        org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
        org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
        org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb,
      )
      packageName = "Cadence"
      // CI passes the release tag; release-please keeps the local default in sync.
      packageVersion = distributionVersion
      // Include modules loaded through reflection by storage, networking and Compose.
      includeAllModules = true
      description = "Manage audio on mounted USB storage"
      vendor = "Yuri Schimke"
      // Icons come from scripts/generate-desktop-icons.py; never hand-edit them.
      linux {
        packageName = "cadence"
        iconFile.set(project.file("src/jvmMain/resources/cadence.png"))
      }
      macOS {
        bundleID = "ee.schimke.cadence"
        iconFile.set(project.file("icons/cadence.icns"))
        // JDK 21 jpackage rejects a zero major in CFBundleVersion. Offset every
        // release major so 0.x packages build and upgrades remain monotonic.
        packageVersion =
          distributionVersion.split(".").let { parts ->
            listOf((parts[0].toInt() + 1).toString(), parts[1], parts[2]).joinToString(".")
          }
      }
      windows {
        iconFile.set(project.file("icons/cadence.ico"))
        menu = true
        shortcut = true
        upgradeUuid = "a82a09d9-e668-46c5-9928-8c6e2bc8f91e"
      }
    }
  }
}
