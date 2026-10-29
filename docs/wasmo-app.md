# Cadence as a Wasmo app

[Wasmo](https://github.com/wasmcomputercompany/wasmo) is a "cloud computer": it installs apps
packaged as `.wasmo` files and serves each one from its own origin,
`https://<app>-<computer>.wasmo.com` (or `*.wasmo.localhost:8080` when run locally).
`:shared:wasmoApp` packages the Kotlin/Wasm browser build as one of those apps, so a Wasmo owner
can open Cadence from their Wasmo launcher. The packaging mirrors
[Terrazzo's](https://github.com/yschimke/homeassistant-remotecompose/blob/main/docs/architecture/wasmo-app.md).

```
./gradlew :shared:wasmoApp            # shared/build/wasmo/cadence.wasmo (ZIP archive)
./gradlew :shared:wasmoAppDirectory   # shared/build/wasmo/dir/cadence.wasmo/ (dev-mode directory)
```

## Shape

```
cadence.wasmo/
  wasmo-manifest.toml      # shared/wasmo/wasmo-manifest.toml, version from the app version
  www/                     # the wasmJsBrowserDistribution output, served to the owner only
    app.html               # the dist's index.html, renamed; + wasm-mime-fallback.js and the
                           #   manifest/icon links
    cadence.js, cadence-platform.js, *.wasm, composeResources/…
    assets/icon.png        # launcher icon
  www-public/              # shared/wasmo/www-public/, served to everyone
    index.html             # landing page: sends the owner to /app.html, explains installing
    manifest.webmanifest   # web app manifest (start_url /app.html), so the app can be installed
    icon-192.png, icon-512.png, apple-touch-icon.png
```

The manifest's integer `version` is `major * 1_000_000 + minor * 1_000 + patch` of `appVersion` in
`shared/build.gradle.kts`, which release-please bumps with the other version strings.

## Public landing page and installing to a device

Wasmo serves `www-public/` to everyone and checks it **before** the owner-only `www/`, and it has
no sign-in redirect: anyone who isn't the signed-in owner gets a bare 404 for every `www/` path.
So `/` is `www-public/index.html` for every visitor, and the app itself is `www/app.html` (the
launcher's `home_path`). The landing page fetches `/app.html`; a 200 means the visitor is the
owner, and it replaces itself with the app, keeping the query and hash. Anyone else, including the
owner signed out of Wasmo, sees what Cadence is and how to install it, instead of a 404.

The app page links `manifest.webmanifest`, so once open the owner can **Install app** from the
browser. The manifest and its icons sit in `www-public/` because browsers fetch a manifest without
credentials, which the owner-only `www/` would answer with 404. There is no service worker.

## Storage and browser capabilities

There is no `app.wasm` and no server-side state. Cadence keeps everything where the static web
build already does: settings in `localStorage`, chosen directory handles in IndexedDB and staged
audio in the origin-private file system. All of it belongs to the install's origin, so it is per
browser and changes if the app is reinstalled under another name. Wasmo's per-install key-value
service (`/api/wasmo/v1/kv`) is not used: a fork-based Wasmo only enables it for install names on
its operator's allow-list.

Each install is a top-level HTTPS page on its own origin, which is what WebUSB and the File System
Access directory picker need ([multiplatform.md](multiplatform.md#usb-and-browser-restrictions)).
Desktop Chrome or Edge is still required for USB discovery and writing to the device's folder.

## Installing

Wasmo's computer menu has an **Install App** item that takes an app address and a name of your
choice.

1. Use the published `cadence.wasmo`. Each release attaches it (the
   [`Release`](../.github/workflows/release-build.yml) workflow builds `:shared:wasmoApp` and
   uploads it under an unversioned name), so the latest build is always at the stable URL
   `https://github.com/yschimke/cadence/releases/latest/download/cadence.wasmo`.
   To host it yourself instead, serve `shared/build/wasmo/cadence.wasmo` from any HTTPS URL Wasmo
   can reach.
2. In Wasmo, choose **Install App**, paste the URL, and name it `cadence`.
3. Open it from the launcher, or `https://cadence-<computer>.wasmo.com/`.

The **Wasmo server**, not your browser, fetches the URL, so it must be reachable from wherever
Wasmo runs. Wasmo does not update an app installed from a URL when the file changes, so updating
means installing again under the same name.

## Wasmo gaps worked around here

- **`.wasm` content type.** Wasmo's content-type table has no `wasm` entry, so `.wasm` may be
  served without `application/wasm`, and `WebAssembly.instantiateStreaming` rejects it.
  `shared/wasmo/wasm-mime-fallback.js` falls back to compiling from bytes for those responses.
- **Directory entries.** Wasmo's installer writes every zip entry as a file, so a directory entry
  blocks the files beneath it. `wasmoApp` writes file entries only, with fixed timestamps.
