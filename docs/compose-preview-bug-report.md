# Bug report: compose-preview does not recognise the new KMP `androidLibrary` plugin

## TL;DR

`compose-preview` cannot discover `@Preview` functions in modules that use
the new `com.android.kotlin.multiplatform.library` plugin (the recommended
KMP replacement for `com.android.library` introduced in AGP 8.7+). The
plugin only attaches to modules applying `com.android.application` or
`com.android.library`, so a Compose Multiplatform project where the UI
lives in `:shared` (KMP `androidLibrary`) cannot apply the plugin to that
module and have it work.

Verified across 0.8.2, 0.8.4, 0.8.5, and 0.8.6. 0.8.5 fixed an unrelated
direct-vs-transitive dependency-check issue but introduced a config-time
classpath-resolution regression; 0.8.6 fixed that regression but the core
KMP-plugin-recognition issue remains.

## Status by version (on `:shared`, KMP `androidLibrary`)

| Plugin version | Behaviour on `:shared`                                  |
| -------------- | ------------------------------------------------------- |
| 0.8.2          | `discoverPreviews` registers; reports 0 previews        |
| 0.8.4          | Same                                                    |
| 0.8.5          | `discoverPreviews` no longer registers at all           |
| 0.8.6          | Same as 0.8.5                                           |

In every case, `previews.json` is empty even when `.class` files containing
the `@Preview` annotation are present under
`shared/build/classes/kotlin/android/main/...` and
`shared/build/intermediates/runtime_library_classes_dir/androidMain/...`.

## Workaround used in this repo

Applying the plugin to the thin `:composeApp` (`com.android.application`)
module instead, and moving the `@Preview` functions there, **does work on
0.8.6**. The cost was non-trivial:

- Lift visibility of the stateless `*Content` composables (`HomeContent`,
  `BluetoothControlsContent`, `BookmarksScreen`, `DeviceFilesContent`,
  `FileSyncContent`) from `internal` to public so `:composeApp` can call
  them across the module boundary.
- Add Compose Multiplatform plugin + Compose runtime/foundation/material3/
  ui/preview/icons deps directly to `:composeApp`, since they had been
  `implementation` (not `api`) on `:shared` and were therefore invisible
  to `:composeApp`'s compile classpath.
- Harden the app's `AppComponentFactory` so the Robolectric renderer
  (which instantiates activities before the Application object is
  initialised) doesn't crash on a `lateinit` access.

This is workable, but it forces architectural concessions that wouldn't
be needed if the plugin understood the KMP `androidLibrary` module type.

## Why the workaround is unsatisfying

The "UI in `:shared`, thin app in `:composeApp`" layout is what JetBrains
and Google promote in current Compose Multiplatform docs, with KMP
`androidLibrary` as the recommended replacement for nesting
`com.android.library` inside the KMP block. As that migration spreads,
more projects will hit this exact wall, and "move all your @Preview
functions to a different module + relax visibility on a bunch of internal
APIs" is a meaningful cost.

## Earlier 0.8.5-only regression (now fixed in 0.8.6)

For history — 0.8.5 added `AndroidPreviewSupport.hasTransitivePreviewDependency`,
which correctly walks transitive dependencies (an improvement). But that
helper resolved `debugRuntimeClasspath` at configuration time, and the same
`configure` lambda then mutated the configuration's hierarchy by injecting
`androidx.compose.ui:ui-test-manifest` and `ui-test-junit4` into
`testImplementation`. AGP rejected this with:

```
Cannot mutate the hierarchy of configuration ':composeApp:debugRuntimeClasspath'
after the configuration was resolved.

	at ee.schimke.composeai.plugin.AndroidPreviewSupport.hasTransitivePreviewDependency(AndroidPreviewSupport.kt:145)
	at ee.schimke.composeai.plugin.AndroidPreviewSupport.hasPreviewDependency$gradle_plugin(AndroidPreviewSupport.kt:104)
	at ee.schimke.composeai.plugin.AndroidPreviewSupport.configure$lambda$1(AndroidPreviewSupport.kt:75)
```

This is **not** present on 0.8.6 — the build configures cleanly and the
discovery/render tasks run. Just noting it in case the underlying
eager-resolution path is still around for some other code path.

## Environment

- `compose-preview` plugin: `ee.schimke.composeai.preview` 0.8.2 / 0.8.4 / 0.8.5 / 0.8.6
- AGP: 8.x (released)
- Kotlin: 2.x
- KMP plugin: `org.jetbrains.kotlin.multiplatform`
- Android KMP plugin: `com.android.kotlin.multiplatform.library` (alias
  `androidKotlinMultiplatformLibrary`, the new replacement for
  `com.android.library` inside KMP)
- JDK: 21 (Temurin)

## Project layout (representative)

```
:shared      → org.jetbrains.kotlin.multiplatform
              + com.android.kotlin.multiplatform.library
              + Compose Multiplatform compiler/runtime
              + androidx.compose.ui:ui-tooling-preview (in androidMain)
              ← @Preview functions live here

:composeApp  → com.android.application
              + project(":shared")  (transitive Compose tooling)
              ← no direct compose-* dependencies
```

## Symptom 1 — plugin applied to `:shared` registers tasks but discovers nothing

Apply the plugin on the shared module:

```kotlin
// shared/build.gradle.kts
plugins {
  alias(libs.plugins.kotlinMultiplatform)
  alias(libs.plugins.androidKotlinMultiplatformLibrary) // <-- new KMP plugin
  alias(libs.plugins.composeMultiplatform)
  alias(libs.plugins.composeCompiler)
  id("ee.schimke.composeai.preview") version "0.8.2"
}

composePreview {
  variant.set("debug")
  sdkVersion.set(35)
  enabled.set(true)
}
```

Run `compose-preview show` or `./gradlew :shared:discoverPreviews`:

```
> Task :shared:discoverPreviews
Discovered 0 preview(s) in module 'shared':
```

Even with multiple `@Preview` functions present in `shared/src/androidMain/kotlin/...`
(verified — the corresponding `.class` files contain the `@Preview` annotation
and are present under `shared/build/classes/kotlin/android/main/...` and
`shared/build/intermediates/runtime_library_classes_dir/androidMain/...`),
the discovery scan walks an empty input and emits an empty `previews.json`.

`./gradlew :shared:outgoingVariants` shows the only AGP-style variant exposed is
`androidMain` / `androidApiElements` — there is no `debug` variant on this
module, because the new KMP `androidLibrary` plugin doesn't synthesise
classic AGP build variants the way `com.android.library` does.

### Likely root cause

Looking at the plugin jar (0.8.2) `ee/schimke/composeai/plugin/ComposePreviewPlugin.class`:

```
javap -p -c .../ComposePreviewPlugin.class | grep -iE "ldc.*com\.android"

  140: ldc  #134  // String com.android.application
  165: ldc  #147  // String com.android.library
```

The plugin only attaches to those two plugin IDs. The Android KMP plugin
applies a different ID (`com.android.kotlin.multiplatform.library`), so the
plugin's variant-aware setup branch never runs and discovery is wired to an
empty input.

## Symptom 2 — plugin applied to `:composeApp` skips task registration

To work around symptom 1, apply the plugin to the `com.android.application`
module instead:

```kotlin
// composeApp/build.gradle.kts
plugins {
  alias(libs.plugins.androidApplication)
  id("ee.schimke.composeai.preview") version "0.8.2"
}

composePreview {
  variant.set("debug")
  sdkVersion.set(35)
  enabled.set(true)
}

dependencies {
  implementation(project(":shared"))   // Compose preview tooling is transitive
}
```

`./gradlew :composeApp:discoverPreviews` now fails:

```
> Task ':composeApp:discoverPreviews' not found in project ':composeApp'.
```

`./gradlew :composeApp:tasks --all` lists only `composePreviewApplied`. Running
with `--info` shows why:

```
compose-preview: no known @Preview dependency declared in module ':composeApp';
skipping task registration. Add one of
  androidx.compose.ui:ui-tooling-preview,
  androidx.compose.ui:ui-tooling-preview-android,
  androidx.wear.tiles:tiles-tooling-preview,
  org.jetbrains.compose.components:components-ui-tooling-preview,
  org.jetbrains.compose.ui:ui-tooling-preview
(or remove the plugin from this module) to opt in.
```

The `app` module pulls Compose UI (and `ui-tooling-preview`) transitively
through `project(":shared")` — they are present on the runtime classpath and
in the merged DEX. But the plugin's opt-in check appears to walk only the
**direct** dependency declarations and so concludes that no Compose preview
dependency exists.

## Symptom 3 — 0.8.5 fixes detection but breaks Gradle config-time invariants

0.8.5 adds `AndroidPreviewSupport.hasTransitivePreviewDependency` (visible in
the stack trace below), so the opt-in check from symptom 2 now sees the
transitive Compose tooling. The plugin proceeds to inject the test deps it
needs, logging:

```
compose-ai-tools: inject[androidx.compose.ui:ui-test-manifest] APPLIED → testImplementation
compose-ai-tools: inject[androidx.compose.ui:ui-test-junit4]   APPLIED → testImplementation
```

But configuration fails with:

```
Configuration 'debugRuntimeClasspath' was resolved during configuration time.
This is a build performance and scalability issue.

> Cannot mutate the hierarchy of configuration ':composeApp:debugRuntimeClasspath'
  after the configuration was resolved. After a configuration has been
  observed, it should not be modified.

	at ee.schimke.composeai.plugin.AndroidPreviewSupport.hasTransitivePreviewDependency(AndroidPreviewSupport.kt:145)
	at ee.schimke.composeai.plugin.AndroidPreviewSupport.hasPreviewDependency$gradle_plugin(AndroidPreviewSupport.kt:104)
	at ee.schimke.composeai.plugin.AndroidPreviewSupport.configure$lambda$1(AndroidPreviewSupport.kt:75)
```

`hasTransitivePreviewDependency` resolves `debugRuntimeClasspath` eagerly at
configuration time. Later, the plugin's same `configure` lambda mutates that
configuration's hierarchy by adding to `testImplementation` (which feeds
into `debugRuntimeClasspath`). AGP's
`DependencyResolutionChecks` correctly rejects this, and the build fails
before any task runs. Confirmed with
`./gradlew :composeApp:tasks --all --no-configuration-cache` — same failure.

This is reproducible without any other plugin involvement: it happens with
just `id("com.android.application")` and `id("ee.schimke.composeai.preview") version "0.8.5"`
on a module that depends on a Compose-bearing project.

## Combined effect

| Plugin version | `:shared` (KMP `androidLibrary`)         | `:composeApp` (`com.android.application`)            |
| -------------- | ---------------------------------------- | ---------------------------------------------------- |
| 0.8.2 / 0.8.4  | Task registers, discovers 0 previews     | Skips task registration (direct-deps-only check)     |
| 0.8.5          | No discovery task registered at all      | Build fails: configuration resolved at config time   |

There is no module in this project layout where the plugin both registers and
discovers. CI workflows from the SKILL.md (e.g. `preview-baselines.yml`,
`preview-comment.yml`) consequently fail with `exit code 3` ("no previews")
on every run on 0.8.4, and now fail at configuration on 0.8.5.

## Suggested fix

**Recognise `com.android.kotlin.multiplatform.library`.** Hook
`pluginManager.withPlugin("com.android.kotlin.multiplatform.library") { … }`
alongside the existing `com.android.application` /
`com.android.library` hooks. The KMP plugin still publishes AGP's
`AndroidComponentsExtension` (or its multiplatform equivalent) — using
`androidComponents.onVariants { … }` to drive task wiring would make the
support uniform across all three plugin types and avoid the classic-AGP
"variant is `debug` or `release`" assumption baked into
`composePreview { variant.set("debug") }`. KMP `androidLibrary` exposes a
single `androidMain` variant rather than `debug`/`release`, so either
auto-detecting the available variant or accepting a variant override
(`composePreview { variant.set("androidMain") }`) would also be needed.

## Reproducer

`yschimke/cadence` PR #25 (branch `agent/curate-sync-redesign-preview`).
History on that branch shows the failed `:shared`-side configuration; the
current tip uses the `:composeApp` workaround described above. Either
state demonstrates the missing KMP-plugin recognition — applying the
plugin to `:shared` produces only the `composePreviewApplied` task and an
empty `previews.json`.
