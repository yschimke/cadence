rootProject.name = "Cadence"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
  repositories {
    google {
      mavenContent {
        includeGroupAndSubgroups("androidx")
        includeGroupAndSubgroups("com.android")
        includeGroupAndSubgroups("com.google")
      }
    }
    mavenCentral()
    gradlePluginPortal()
    // Livery snapshots from a local checkout (`./gradlew publishToMavenLocal` in livery), until
    // its first release is on Maven Central.
    mavenLocal { mavenContent { includeModuleByRegex("ee\\.schimke\\.composeai.*", ".*livery.*") } }
  }
}

dependencyResolutionManagement {
  repositories {
    google {
      mavenContent {
        includeGroupAndSubgroups("androidx")
        includeGroupAndSubgroups("com.android")
        includeGroupAndSubgroups("com.google")
      }
    }
    // Livery snapshots from a local checkout (`./gradlew publishToMavenLocal` in livery), until
    // its first release is on Maven Central.
    mavenLocal { mavenContent { includeModuleByRegex("ee\\.schimke\\.composeai.*", ".*livery.*") } }
    mavenCentral()
    maven {
      url = uri("https://jogamp.org/deployment/maven")
      mavenContent { includeGroupAndSubgroups("org.jogamp") }
    }
  }
}

include(":composeApp")

include(":shared", ":desktopApp", ":rcsp")

// Shared, transport-neutral schema for the phone ↔ Wear companion Data Layer.
include(":wearCompanionContract")

// Wear owns presentation and Tiles; :rcsp supplies verified device facts and capability checks.
include(":wearApp")

// Swimming-themed Remote Compose drawing shared by the launcher and Wear widgets.
include(":swimUi")

// Each application has its own profile generator and target device.
include(":mobileBaselineProfile", ":wearBaselineProfile")
