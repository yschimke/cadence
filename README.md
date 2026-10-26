Cadence manages audio on USB storage with a shared Compose Multiplatform UI for Android, desktop, and the browser (Kotlin/Wasm).

- `shared/commonMain` contains navigation, screens, ViewModels, theme/fonts, settings contracts, and the desktop/browser staging and sync workflow.
- `shared/androidMain` contains Android USB discovery, SAF storage, Bluetooth/media controls, WorkManager, and Metro application wiring.
- `shared/desktopMain` contains libusb discovery and filesystem adapters. `desktopApp` launches a native Compose window.
- `shared/wasmJsMain` contains WebUSB and File System Access adapters and the browser entry point. DataStore stays in `jvmAndAndroidMain`; browser settings use the same protobuf schema in localStorage.

## Run

Use JDK 21 and an Android SDK with API 37 (`platforms;android-37.0`) and build tools 37. Set `JAVA_HOME` and `ANDROID_HOME` (or `sdk.dir` in untracked `local.properties`). Gradle downloads the configured Kotlin/Node toolchains.

```sh
./gradlew :composeApp:assembleDebug             # Android APK
./gradlew :desktopApp:run                      # Linux/macOS/Windows desktop
./gradlew :desktopApp:createDistributable       # desktop bundle for the current OS
./gradlew :shared:wasmJsBrowserDevelopmentRun   # browser dev server; open its printed localhost URL
./gradlew :shared:wasmJsBrowserDistribution     # production website in shared/build/dist/wasmJs/productionExecutable
```

Serve the production directory over HTTPS (localhost is allowed for development). Open it in a browser with WebAssembly GC support. USB and writable directory selection require browser support independently of Wasm support; Chrome or Edge on desktop is the supported file-management route. The UI explains missing capabilities.

## Published builds

After the publishing workflows merge into `main`, the production browser app is
served at [yschimke.github.io/cadence/app/](https://yschimke.github.io/cadence/app/).
Every push to `main` rebuilds it. The Pages workflow renders `docs/` with Jekyll
and places the Wasm bundle under `app/`, retaining the existing privacy URL.
PRs build the same Pages artifact without deploying it.

The repository's Pages source is configured for **GitHub Actions**. Merging the
publishing workflow triggers its first deployment; the existing site stays
visible until that deployment succeeds. To retry, run **Publish browser app and
docs** on `main` from the Actions tab.

The next [GitHub release](https://github.com/yschimke/cadence/releases/latest)
includes Android APK/AAB files and self-contained desktop installers:
Linux x64 DEB, Windows x64 MSI, and macOS Intel/Apple Silicon DMGs. Installers
bundle Java, so users do not need a JDK. Names include the version, OS and
architecture. Desktop packaging also runs on PRs, uploading `desktop-*`
workflow artifacts; manually dispatching **Release** builds artifacts without
changing the public release. Existing releases are not backfilled automatically.

Desktop installers are currently unsigned and macOS builds are not notarized;
Windows/macOS may require an explicit OS approval to open them. Mounted-folder
sync is available on Apple Silicon even if usb4java's native discovery library
cannot load. Physical USB and each OS installation still require manual checks.

## Podcasts → headphones

Open **Podcast sync** on the home screen to import public RSS or a selected-episode snapshot, prepare timeline sections, mark sections played locally, and copy audio into an owned headphone folder. Android uses Media3 AAC/M4A clipping; desktop uses FFmpeg MP3/M4A; browser preparation is unsplit. Optional Supabase metadata sync supports cross-device configuration and listening continuity without uploading audio. Pocket Casts account access and backend position writes require a supported provider API and remain explicitly unavailable. See [setup, capabilities, cloud choices and verification](docs/podcast-sync.md).

## Manage USB audio

1. Plug in the device and let the OS mount its FAT32 volume.
2. Open **Devices**. Desktop discovers USB descriptors using usb4java/libusb. In the browser, **Connect USB** opens the browser's device permission chooser for `05e3:0761`; cancellation and unplugging update connection status.
3. Choose **Manage New Device** and select the mounted volume. Directory selection is a separate permission from USB discovery: the app cannot infer a mount path from a USB descriptor.
4. In **Manage sync**, add a source directory, create a profile, and select the registered target device. Refresh the profile to stage its files, then start sync. Existing files with matching paths are replaced; unrelated files remain. Sources with duplicate relative names in one profile are rejected.

The observed device is USB mass storage (class `0x08`, SCSI transparent, Bulk-Only Transport), with no vendor USB control interface. WebUSB **cannot claim this protected interface**. The browser therefore uses WebUSB for discovery and status, and the File System Access directory picker for actual audio copies. It does not implement a fictitious RCSP-over-USB transport or raw block writes. See [platform capabilities and dependency audit](docs/multiplatform.md) and [device research](research/usb.md).

Desktop stores settings/staging in `~/.cadence`. Browser settings are origin-local, directory handles are stored in IndexedDB, and staged files are stored in Origin Private File System (OPFS), subject to the browser's storage quota. If directory permission expires, choose that directory again; its stored handle is reused. Clear browser site data to remove settings, handles and staging.

## Verify and format

```sh
./gradlew :shared:desktopTest :desktopApp:jvmJar :composeApp:assembleDebug
./gradlew :shared:wasmJsBrowserDistribution
node --test tests/browser-platform.test.cjs
./gradlew ktfmtFormat
./gradlew ktfmtCheck
./gradlew installGitHooks
```

For the optional production UI smoke, install Playwright outside the repository, install its Chromium browser, serve the production directory on localhost, and point `NODE_PATH` at that installation:

```sh
npm install --prefix /tmp/cadence-smoke playwright
/tmp/cadence-smoke/node_modules/.bin/playwright install chromium
python3 -m http.server 8765 --bind 127.0.0.1 --directory shared/build/dist/wasmJs/productionExecutable
# In another terminal:
NODE_PATH=/tmp/cadence-smoke/node_modules node tests/wasm-smoke.cjs
```

Physical USB permissions, filesystem drivers, and real headphone firmware still need a hardware smoke test. Automated tests cover path validation, staging/copy behavior, chooser activation, cancellation/disconnects, and unsupported-browser handling.
