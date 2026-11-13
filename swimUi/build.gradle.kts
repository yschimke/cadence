import com.android.build.api.variant.LibraryAndroidComponentsExtension

plugins {
  alias(libs.plugins.androidLibrary)
  alias(libs.plugins.composeMultiplatform)
  alias(libs.plugins.composeCompiler)
}

// Swimming-themed Remote Compose drawing shared by the Livery templates and the launcher widget
// (:shared) and the Wear widgets (:wearApp). Everything here stays inside the operations both
// PROFILE_WIDGETS (v7) and PROFILE_WEAR_WIDGETS accept: canvas, paths, matrix transforms, float
// expressions over time and loops — no clip paths, particles or touch expressions.
//
// One source, two bindings. The drawing (Water.kt, SwimLane.kt, PoolSeasonDrawing.kt) is written
// against rc-players' Compose Multiplatform creation API (ee.schimke.composeai.rccreation), which
// the Livery templates are captured with. The surfaces whose hosts take AndroidX's
// remote-creation-compose types (the podcast launcher widget captured on the device, Glance Wear)
// get the same files with their packages rewritten, in ee.schimke.cadence.swim.androidx
// (generateAndroidxBinding), as rc-players builds rc-widget-creation-androidx and Livery its
// livery-capture. Nothing generated is committed. The colours, seasons and laps are plain data,
// shared by both.

/**
 * Copies Kotlin sources into [toPackage], replacing each package prefix in [packages] (where an
 * identifier starts and the prefix is not part of a longer name) with its value. A copy imports
 * [fromPackage], for what the files left behind declare.
 */
@CacheableTask
abstract class RewritePackages : DefaultTask() {
  @get:InputFiles
  @get:PathSensitive(PathSensitivity.NAME_ONLY)
  abstract val sources: ConfigurableFileCollection

  /** In order: a longer prefix goes before the one it starts with. */
  @get:Input abstract val packages: ListProperty<Pair<String, String>>

  @get:Input abstract val fromPackage: Property<String>

  @get:Input abstract val toPackage: Property<String>

  @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

  @TaskAction
  fun rewrite() {
    val out = outputDirectory.get().asFile
    out.deleteRecursively()
    val directory = out.resolve(toPackage.get().replace('.', '/')).apply { mkdirs() }
    val from = "package ${fromPackage.get()}\n"
    val rules =
      packages.get().map { (prefix, to) ->
        Regex("(?<![A-Za-z0-9_.])" + Regex.escape(prefix) + "(?![A-Za-z0-9_])") to to
      }
    sources.files
      .sortedBy { it.name }
      .forEach { file ->
        val source = file.readText()
        check(from in source) { "${file.name} is not in ${fromPackage.get()}" }
        val text =
          rules
            .fold(source) { text, (prefix, to) -> prefix.replace(text, to) }
            .replace(from, "package ${toPackage.get()}\n\nimport ${fromPackage.get()}.*\n")
        directory
          .resolve(file.name)
          .writeText("// Generated from ${file.name} by $name. Do not edit.\n$text")
      }
  }
}

val drawing = layout.projectDirectory.dir("src/main/kotlin/ee/schimke/cadence/swim")

val generateAndroidxBinding by
  tasks.registering(RewritePackages::class) {
    description = "Writes the swim drawing against AndroidX's remote-creation-compose."
    sources.from(
      drawing.file("Water.kt"),
      drawing.file("SwimLane.kt"),
      drawing.file("PoolSeasonDrawing.kt"),
    )
    // AndroidX's alpha keeps RemotePath in its Java creation module.
    packages.add(
      "ee.schimke.composeai.rccreation.compose.path.RemotePath" to
        "androidx.compose.remote.creation.RemotePath",
    )
    packages.add(
      "ee.schimke.composeai.rccreation.compose" to "androidx.compose.remote.creation.compose",
    )
    fromPackage.set("ee.schimke.cadence.swim")
    toPackage.set("ee.schimke.cadence.swim.androidx")
    outputDirectory.set(layout.buildDirectory.dir("generated/androidxBinding/kotlin"))
  }

extensions.configure<LibraryAndroidComponentsExtension> {
  onVariants { variant ->
    variant.sources.kotlin?.addGeneratedSourceDirectory(
      generateAndroidxBinding,
      RewritePackages::outputDirectory,
    )
  }
}

android {
  namespace = "ee.schimke.cadence.swim"
  compileSdk = libs.versions.android.compileSdk.get().toInt()
  defaultConfig { minSdk = libs.versions.android.minSdk.get().toInt() }
}

dependencies {
  // The drawing's own API: rc-players' Compose Multiplatform creation.
  api(platform(libs.rc.players.bom))
  api(libs.rc.creation.compose)
  // The generated AndroidX binding's, for the launcher and Wear widget hosts.
  api(libs.compose.remote.creation.compose)
  implementation(libs.compose.remote.creation)
  implementation(libs.compose.remote.core)
  implementation(libs.compose.runtime)
  implementation(libs.compose.ui)
  implementation(libs.compose.foundation)
  implementation(libs.compose.ui.tooling.preview)
  implementation(libs.compose.remote.tooling.preview)
  implementation(platform(libs.composeai.daemon.bom))
  implementation(libs.composeai.preview.annotations)
  implementation(libs.glance.wear)

  testImplementation(libs.junit)
  testImplementation(libs.kotlin.test.junit)
}
