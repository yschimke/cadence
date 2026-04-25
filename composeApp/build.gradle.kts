plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    kotlin("plugin.serialization").version(libs.versions.kotlin)
    id("com.gradleup.compat.patrouille").version(libs.versions.patrouille)
    id("dev.zacsweers.metro").version(libs.versions.metro)
    id("com.squareup.wire").version(libs.versions.wire)
    id("ee.schimke.composeai.preview").version("0.7.0")
    alias(libs.plugins.playPublisher)
}

val appVersionName = "0.1.0" // x-release-please-version

// Pack MAJOR.MINOR.PATCH into a monotonic int. Caps at major < 22.
val appVersionCode: Int = run {
    val parts = appVersionName.split(".", "-").mapNotNull { it.toIntOrNull() }
    val major = parts.getOrNull(0) ?: 0
    val minor = parts.getOrNull(1) ?: 0
    val patch = parts.getOrNull(2) ?: 0
    major * 10_000 + minor * 100 + patch
}.coerceAtLeast(1)

composePreview {
    variant.set("debug")
    sdkVersion.set(35)
    enabled.set(true)
}

kotlin {
    androidTarget {
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
            implementation("com.squareup.okio:okio:3.11.0")
            implementation(libs.androidx.datastore)
            implementation(libs.bonsai.core)
            implementation(libs.bonsai.file.system)
            implementation(libs.material.icons.core)
            implementation(libs.material.icons.extended)
            implementation(libs.compose.webview.multiplatform)
            implementation(libs.ktor.client.core)
        }
        androidInstrumentedTest.dependencies {
            implementation(libs.junit)
            implementation(libs.kotlin.test.junit)
            implementation(libs.androidx.test.junit)
            implementation(libs.androidx.espresso.core)
            implementation(libs.androidx.runner)
        }
    }
}

android {
    namespace = "ee.schimke.cadence"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "ee.schimke.cadence"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    val releaseKeystorePath = System.getenv("CADENCE_KEYSTORE_PATH")
    signingConfigs {
        if (releaseKeystorePath != null) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = System.getenv("CADENCE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("CADENCE_KEY_ALIAS")
                keyPassword = System.getenv("CADENCE_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            if (releaseKeystorePath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    buildFeatures {
        compose = true
    }
}

play {
    track.set("internal")
    defaultToAppBundles.set(true)
    // Skip API calls in CI runs that build but don't publish (e.g. PRs).
    enabled.set(System.getenv("ANDROID_PUBLISHER_CREDENTIALS") != null)
}

compatPatrouille {
    java(17)
    kotlin(libs.versions.kotlin.get())
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
