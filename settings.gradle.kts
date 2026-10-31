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
