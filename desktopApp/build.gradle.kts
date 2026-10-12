plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.composeMultiplatform)
  alias(libs.plugins.composeCompiler)
}

val desktopVersion = "0.1.2" // x-release-please-version

kotlin {
  jvm()
  sourceSets {
    jvmMain.dependencies {
      implementation(projects.shared)
      implementation(libs.androidx.lifecycle.viewmodel)
      implementation(compose.desktop.currentOs)
    }
  }
}

compose.desktop {
  application {
    mainClass = "ee.schimke.cadence.desktop.MainKt"
    nativeDistributions {
      targetFormats(
        org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
        org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
        org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb,
      )
      packageName = "Cadence"
      // CI passes the release tag; release-please keeps the local default in sync.
      packageVersion = providers.gradleProperty("desktopVersion").getOrElse(desktopVersion)
      // Include modules loaded through reflection by storage, networking and Compose.
      includeAllModules = true
      description = "Manage audio on mounted USB storage"
      vendor = "Yuri Schimke"
      linux { packageName = "cadence" }
      macOS { bundleID = "ee.schimke.cadence" }
      windows {
        menu = true
        shortcut = true
        upgradeUuid = "a82a09d9-e668-46c5-9928-8c6e2bc8f91e"
      }
    }
  }
}
