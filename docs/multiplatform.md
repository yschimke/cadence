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

## USB and browser restrictions

[The observed USB descriptors](../research/usb.md) expose a single class `0x08` mass-storage interface with SCSI Bulk-Only Transport. USB is the audio content channel, not the Bluetooth RCSP command channel. The VID/PID identifies a Genesys storage bridge, not a unique headphone; its serial is not a reliable per-unit identity. Users explicitly select the mounted directory to avoid guessing a volume.

The [WebUSB specification](https://wicg.github.io/webusb/#usb-interface) marks mass storage as a protected class. A permission grant does not permit claiming it. The adapter detects this limitation and directs users to the mounted directory. It never detaches kernel drivers, claims storage endpoints, or emits unconfirmed vendor commands. [Chrome's WebUSB guidance](https://developer.chrome.com/docs/capabilities/usb) requires a secure context and user activation for `requestDevice`; the chooser is called directly from the Compose click, before an asynchronous boundary.

[File System Access](https://developer.chrome.com/docs/capabilities/web-apis/file-system-access) also requires secure context and user activation for the writable directory picker. Chromium-based desktop browsers expose the required picker; Firefox/Safari may run the Wasm UI but do not expose this writable directory-selection API. Permissions may expire on reload: the browser backend checks permission and reports that the directory must be selected again. Reselecting the same directory reuses its saved handle key, so existing profiles and targets continue to refer to it. Browser permissions, enterprise policies and iframe `Permissions-Policy` can further disable USB. Deploy as a top-level page. Browser staging is private to its origin and can be evicted; refresh again if staging is empty.

Desktop USB discovery uses the [usb4java libusb binding](https://github.com/usb4java/usb4java). Mounted-volume access remains available if native discovery is unavailable. Linux device-node permissions can affect discovery; do not run Cadence as root or replace mass-storage drivers. Windows keeps its mass-storage driver. The bundled usb4java native artifacts cover common x86 platforms; native discovery on ARM machines may need a matching libusb4java build. No raw interface claim is needed for audio copying.

The shared portable sync implementation validates relative paths, refuses duplicate source filenames, stages a snapshot before syncing, and streams copies through the platform backend. Desktop writes a temporary sibling file before atomic replacement; the browser uses `createWritable()` and commits the write through `pipeTo`. Cancellation stops before the next file; an in-flight browser copy may finish. Sync never deletes unrelated target files. Mount NFS externally and add it as a local directory on desktop/browser; no NFS client has been invented for the browser. Android's existing NFS placeholder behavior is retained.

## Stable dependency selection

Exact versions live in [`gradle/libs.versions.toml`](../gradle/libs.versions.toml) and [`gradle/wrapper/gradle-wrapper.properties`](../gradle/wrapper/gradle-wrapper.properties); Renovate bumps them, so this page records only the choices that are not the framework defaults:

- Compose Multiplatform's default Material 3 coordinates are an alpha. Cadence selects the latest stable Compose Multiplatform Material 3 explicitly (`composeMaterial3`), and only Android uses the newer `androidx.compose.material3` (`androidx-compose-material3`) for `MaterialExpressiveTheme`/`MotionScheme`.
- Navigation is pinned to the latest stable `navigation-compose` (`navigationCompose`) rather than the framework's beta default.
- The Gradle wrapper must satisfy AGP's [minimum Gradle version](https://developer.android.com/build/releases/about-agp).
- JDK 21 satisfies Metro's compiler-plugin requirement, while Android/shared bytecode stays at Java 17 for preview rendering. Android API levels are set by `android-compileSdk`, `android-targetSdk` and `android-minSdk`.
- Only stable releases are selected; snapshot repositories are not used.

The unpublished-stable modernstorage dependency is removed: Cadence already owned its SAF FileSystem implementation and needed only URI/path helpers and metadata extras. Those small helpers now live in Android code, preserving persisted URI normalization. Bonsai is replaced with a common Compose expandable file tree, and the WebView library is isolated to Android. DataStore has no Wasm variant, so browser persistence implements the small settings contract using the same Wire-generated protobuf schema.

## Verification in this change

On Linux with JDK 21 and Android SDK 37, the combined `:composeApp:assembleDebug`, `:desktopApp:jvmJar`, `:shared:desktopTest`, `:shared:wasmJsBrowserDistribution`, and `ktfmtCheck` invocation passes. Five Kotlin tests cover relative path validation, staged sync, source collisions, and profile isolation. Seven Node tests cover WebUSB discovery/permissions and browser directory copies.

The native desktop app starts and renders under Xvfb with software rendering. The production Wasm bundle starts in Chromium without JavaScript errors; UI smoke tests verify a real canvas click retains user activation for the mocked WebUSB chooser, reports protected mass storage, and shows unsupported-browser directory feedback. USB descriptors, browser chooser results and file handles are fixtures in automated tests. No physical headphone, Android emulator, macOS, Windows or ARM runtime was available in this session; installer builds are now checked by a Linux/Windows/macOS CI matrix, while installation and those hardware/OS combinations still require manual verification.

macOS installer metadata uses the release version with its major increased by one
(for example, release `0.1.3` uses package version `1.1.3`). JDK 21's `jpackage`
requires a positive first component in `CFBundleVersion`; applying the offset to
every release preserves upgrade ordering when Cadence reaches `1.x`. Release tags
and downloaded installer filenames continue to use Cadence's release version.
