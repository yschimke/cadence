# Multiplatform architecture and capabilities

Verified against publisher documentation and Maven metadata on 2026-10-11.

| Capability | Android | Desktop | Browser/Wasm |
| --- | --- | --- | --- |
| Navigation, screens, appearance | Shared Compose UI; system dynamic colors retained | Shared UI, system light/dark or Cadence branding | Shared UI, system light/dark or Cadence branding |
| USB discovery | USB host API, attach/detach broadcasts | usb4java/libusb descriptors, polling every 1.5 seconds | WebUSB granted devices and device chooser; attach/detach events |
| Device files | Persisted SAF document-tree permission | User-selected OS-mounted directory | User-selected File System Access directory handle |
| Refresh/staging | Android staging directory | `~/.cadence/sync-staging` | OPFS staging; browser quota applies |
| Automatic refresh | WorkManager, including network constraints | While app is open; network constraints are not enforced | Manual only; background refresh is unavailable |
| Sync on USB attach | Existing Android receiver | While app is open, enabled preference and VID/PID match | Manual only; no silent permission acquisition |
| Bluetooth/media | Existing OS integration and command feedback | Explicitly unavailable; use Android | Explicitly unavailable; Web Bluetooth does not expose this device's Classic RFCOMM channel |
| Source providers | SAF providers | Local or already mounted network directories | Picker-accessible mounted directories |
| Bookmarks | Embedded WebView | Opens system browser | Opens browser tab |

## USB and browser restrictions

[The observed USB descriptors](../research/usb.md) expose a single class `0x08` mass-storage interface with SCSI Bulk-Only Transport. USB is the audio content channel, not the Bluetooth RCSP command channel. The VID/PID identifies a Genesys storage bridge, not a unique headphone; its serial is not a reliable per-unit identity. Users explicitly select the mounted directory to avoid guessing a volume.

The [WebUSB specification](https://wicg.github.io/webusb/#usb-interface) marks mass storage as a protected class. A permission grant does not permit claiming it. The adapter detects this limitation and directs users to the mounted directory. It never detaches kernel drivers, claims storage endpoints, or emits unconfirmed vendor commands. [Chrome's WebUSB guidance](https://developer.chrome.com/docs/capabilities/usb) requires a secure context and user activation for `requestDevice`; the chooser is called directly from the Compose click, before an asynchronous boundary.

[File System Access](https://developer.chrome.com/docs/capabilities/web-apis/file-system-access) also requires secure context and user activation for the writable directory picker. Chromium-based desktop browsers expose the required picker; Firefox/Safari may run the Wasm UI but do not expose this writable directory-selection API. Permissions may expire on reload: the browser backend checks permission and reports that the directory must be selected again. Reselecting the same directory reuses its saved handle key, so existing profiles and targets continue to refer to it. Browser permissions, enterprise policies and iframe `Permissions-Policy` can further disable USB. Deploy as a top-level page. Browser staging is private to its origin and can be evicted; refresh again if staging is empty.

Desktop USB discovery uses the [usb4java libusb binding](https://github.com/usb4java/usb4java). Mounted-volume access remains available if native discovery is unavailable. Linux device-node permissions can affect discovery; do not run Cadence as root or replace mass-storage drivers. Windows keeps its mass-storage driver. The bundled usb4java native artifacts cover common x86 platforms; native discovery on ARM machines may need a matching libusb4java build. No raw interface claim is needed for audio copying.

The shared portable sync implementation validates relative paths, refuses duplicate source filenames, stages a snapshot before syncing, and streams copies through the platform backend. Desktop writes a temporary sibling file before atomic replacement; the browser uses `createWritable()` and commits the write through `pipeTo`. Cancellation stops before the next file; an in-flight browser copy may finish. Sync never deletes unrelated target files. Mount NFS externally and add it as a local directory on desktop/browser; no NFS client has been invented for the browser. Android's existing NFS placeholder behavior is retained.

## Stable dependency selection

Kotlin moves from 2.4.20 to [2.4.21](https://github.com/JetBrains/kotlin/releases/tag/v2.4.21), with the matching Compose compiler. [Compose Multiplatform 1.12.1](https://github.com/JetBrains/compose-multiplatform/releases/tag/v1.12.1) is the current stable framework, but its default Material 3 coordinates are `1.12.0-alpha03`; Cadence explicitly selects stable Material 3 **1.9.0**. Navigation remains the latest stable **2.9.2**, rather than the framework's beta default. Lifecycle is **2.11.0**.

[AGP 9.4.1](https://developer.android.com/reference/tools/gradle-api) requires at least [Gradle 9.6](https://developer.android.com/build/releases/about-agp); the existing stable Gradle **9.8.0** wrapper is retained. JDK 21 satisfies Metro's compiler-plugin requirement, while Android/shared bytecode stays at Java 17 for preview rendering. Android API levels remain compile 37, target 36, minimum 34.

The unpublished-stable modernstorage dependency is removed: Cadence already owned its SAF FileSystem implementation and needed only URI/path helpers and metadata extras. Those small helpers now live in Android code, preserving persisted URI normalization. Bonsai is replaced with a common Compose expandable file tree, and the WebView library is isolated to Android. DataStore has no Wasm variant, so browser persistence implements the small settings contract using the same Wire-generated protobuf schema. Stable Metro/Wire plugins remain at 1.4.5/7.1.0. Snapshot repositories are removed.

| Dependency | Selected | Latest stable in publisher metadata | Source |
| --- | --- | --- | --- |
| `org.jetbrains.compose.material3:material3` | 1.9.0 | 1.9.0 | [metadata](https://repo.maven.apache.org/maven2/org/jetbrains/compose/material3/material3/maven-metadata.xml) |
| `com.squareup.okio:okio` | 3.18.2 | 3.18.2 | [metadata](https://repo.maven.apache.org/maven2/com/squareup/okio/okio/maven-metadata.xml) |
| `org.usb4java:usb4java` | 1.3.0 | 1.3.0 | [metadata](https://repo.maven.apache.org/maven2/org/usb4java/usb4java/maven-metadata.xml) |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core` | 1.11.0 | 1.11.0 | [metadata](https://repo.maven.apache.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-core/maven-metadata.xml) |
| `androidx.activity:activity-compose` | 1.13.0 | 1.13.0 | [metadata](https://dl.google.com/dl/android/maven2/androidx/activity/activity-compose/maven-metadata.xml) |
| `androidx.core:core-splashscreen` | 1.2.0 | 1.2.0 | [metadata](https://dl.google.com/dl/android/maven2/androidx/core/core-splashscreen/maven-metadata.xml) |
| `androidx.datastore:datastore` | 1.2.1 | 1.2.1 | [metadata](https://dl.google.com/dl/android/maven2/androidx/datastore/datastore/maven-metadata.xml) |
| `androidx.documentfile:documentfile` | 1.1.0 | 1.1.0 | [metadata](https://dl.google.com/dl/android/maven2/androidx/documentfile/documentfile/maven-metadata.xml) |
| `androidx.test.espresso:espresso-core` | 3.7.0 | 3.7.0 | [metadata](https://dl.google.com/dl/android/maven2/androidx/test/espresso/espresso-core/maven-metadata.xml) |
| `org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose` | 2.11.0 | 2.11.0 | [metadata](https://repo.maven.apache.org/maven2/org/jetbrains/androidx/lifecycle/lifecycle-runtime-compose/maven-metadata.xml) |
| `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose` | 2.11.0 | 2.11.0 | [metadata](https://repo.maven.apache.org/maven2/org/jetbrains/androidx/lifecycle/lifecycle-viewmodel-compose/maven-metadata.xml) |
| `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel` | 2.11.0 | 2.11.0 | [metadata](https://repo.maven.apache.org/maven2/org/jetbrains/androidx/lifecycle/lifecycle-viewmodel/maven-metadata.xml) |
| `androidx.test:runner` | 1.7.0 | 1.7.0 | [metadata](https://dl.google.com/dl/android/maven2/androidx/test/runner/maven-metadata.xml) |
| `androidx.test.ext:junit` | 1.3.0 | 1.3.0 | [metadata](https://dl.google.com/dl/android/maven2/androidx/test/ext/junit/maven-metadata.xml) |
| `androidx.compose.ui:ui-text-google-fonts` | 1.12.1 | 1.12.1 | [metadata](https://dl.google.com/dl/android/maven2/androidx/compose/ui/ui-text-google-fonts/maven-metadata.xml) |
| `androidx.work:work-runtime-ktx` | 2.12.0 | 2.12.0 | [metadata](https://dl.google.com/dl/android/maven2/androidx/work/work-runtime-ktx/maven-metadata.xml) |
| `io.github.kevinnzou:compose-webview-multiplatform` | 2.0.3 | 2.0.3 | [metadata](https://repo.maven.apache.org/maven2/io/github/kevinnzou/compose-webview-multiplatform/maven-metadata.xml) |
| `junit:junit` | 4.13.2 | 4.13.2 | [metadata](https://repo.maven.apache.org/maven2/junit/junit/maven-metadata.xml) |
| `org.jetbrains.kotlin:kotlin-test` | 2.4.21 | 2.4.21 | [metadata](https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/kotlin-test/maven-metadata.xml) |
| `org.jetbrains.kotlin:kotlin-test-junit` | 2.4.21 | 2.4.21 | [metadata](https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/kotlin-test-junit/maven-metadata.xml) |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.11.0 | 1.11.0 | [metadata](https://repo.maven.apache.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-android/maven-metadata.xml) |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.11.0 | 1.11.0 | [metadata](https://repo.maven.apache.org/maven2/org/jetbrains/kotlinx/kotlinx-serialization-json/maven-metadata.xml) |
| `io.ktor:ktor-client-core` | 3.6.0 | 3.6.0 | [metadata](https://repo.maven.apache.org/maven2/io/ktor/ktor-client-core/maven-metadata.xml) |
| `org.jetbrains.compose.material:material-icons-core` | 1.7.3 | 1.7.3 | [metadata](https://repo.maven.apache.org/maven2/org/jetbrains/compose/material/material-icons-core/maven-metadata.xml) |
| `org.jetbrains.compose.material:material-icons-extended` | 1.7.3 | 1.7.3 | [metadata](https://repo.maven.apache.org/maven2/org/jetbrains/compose/material/material-icons-extended/maven-metadata.xml) |
| `org.jetbrains.androidx.navigation:navigation-compose` | 2.9.2 | 2.9.2 | [metadata](https://repo.maven.apache.org/maven2/org/jetbrains/androidx/navigation/navigation-compose/maven-metadata.xml) |

## Verification in this change

On Linux with JDK 21 and Android SDK 37, the combined `:composeApp:assembleDebug`, `:desktopApp:jvmJar`, `:shared:desktopTest`, `:shared:wasmJsBrowserDistribution`, and `ktfmtCheck` invocation passes. Five Kotlin tests cover relative path validation, staged sync, source collisions, and profile isolation. Seven Node tests cover WebUSB discovery/permissions and browser directory copies.

The native desktop app starts and renders under Xvfb with software rendering. The production Wasm bundle starts in Chromium without JavaScript errors; UI smoke tests verify a real canvas click retains user activation for the mocked WebUSB chooser, reports protected mass storage, and shows unsupported-browser directory feedback. USB descriptors, browser chooser results and file handles are fixtures in automated tests. No physical headphone, Android emulator, macOS, Windows or ARM runtime was available in this session; installer builds are now checked by a Linux/Windows/macOS CI matrix, while installation and those hardware/OS combinations still require manual verification.
