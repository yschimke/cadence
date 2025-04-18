plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    kotlin("plugin.serialization").version(libs.versions.kotlin)
    id("com.gradleup.compat.patrouille").version("0.0.0")
    id("dev.zacsweers.metro").version("0.1.2")
    id("com.squareup.wire").version("5.3.1")
}

kotlin {
    androidTarget {
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation("androidx.core:core-splashscreen:1.0.1")
            implementation("me.saket.modernstorage:modernstorage-permissions:1.0.0-alpha09")
            implementation("me.saket.modernstorage:modernstorage-storage:1.0.0-alpha09")
            implementation("androidx.documentfile:documentfile:1.0.1")
        }
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.material3AdaptiveNavigationSuite)
            implementation("org.jetbrains.androidx.navigation:navigation-compose:2.9.0-alpha16")
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodel)
            implementation(libs.androidx.lifecycle.runtime.compose)
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
            implementation("com.squareup.okio:okio:3.10.2")
            implementation("androidx.datastore:datastore:1.1.4")
        }
        androidInstrumentedTest.dependencies {
            implementation(libs.junit)
            implementation(libs.kotlin.test.junit)
            implementation(libs.androidx.test.junit)
            implementation(libs.androidx.espresso.core)
            implementation("androidx.test:runner:1.6.2")
        }
    }
}

android {
    namespace = "ee.schimke.shokz"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "ee.schimke.shokz"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
        }
    }
    buildFeatures {
        compose = true
    }
}

compatPatrouille {
    java(17)
    kotlin(embeddedKotlinVersion)
}

wire {
    sourcePath {
        srcDir("src/commonMain/proto")
    }

    kotlin {}
}

metro {
    debug.set(true)
    reportsDestination.set(layout.buildDirectory.dir("metro/reports"))
}

//dependencies {
//    debugImplementation(compose.uiTooling)
//}

