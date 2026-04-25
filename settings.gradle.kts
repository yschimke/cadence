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
        maven {
            url = uri("https://oss.sonatype.org/content/repositories/snapshots/")
            mavenContent {
                includeGroupAndSubgroups("dev.zacsweers.metro")
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
        maven {
            url = uri("https://oss.sonatype.org/content/repositories/snapshots/")
            mavenContent {
                includeGroupAndSubgroups("dev.zacsweers.metro")
            }
        }
        mavenCentral()
    }
}

include(":composeApp")
include(":shared")