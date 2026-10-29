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
    cadence-backend.js     # shares settings through the install's kv service, then loads cadence.js
    cadence.js, cadence-platform.js, *.wasm
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

## Sharing settings across browsers

There is no `app.wasm`. On a Wasmo that enables its per-install services for this install name
(the fork's `WASMO_SERVICE_APPS` allow-list, as for Terrazzo), the owner's settings follow them
to every browser they open Cadence in. The `.wasmo` build's `app.html` loads
`shared/wasmo/cadence-backend.js` in place of `cadence.js`:

1. It probes `/api/wasmo/v1/capabilities` (one longer retry if the Wasmo doesn't answer).
2. With `kv` and `kvImport`, it reads the install's values and seeds `localStorage` with them
   before the app starts, so the app's synchronous reads see the shared values. The first browser
   on an empty install uploads what it has with `POST kv/import`, which only an empty install
   accepts; if another browser got there first, its values win.
3. The app reports each write through `globalThis.cadenceStorage.changed` (set only by this
   script; the static web build has none) and the script `PUT`s it. A failed write stays queued,
   is retried with the next change or when the browser reconnects, and shows a notice.
4. If the install's values can't be read, the page offers a reload rather than starting from this
   browser's copy, which would fork the owner's settings.

| Kept on the Wasmo (`kv`) | Kept per browser |
| --- | --- |
| `settings`: the whole settings proto (devices, sources, sync profiles and preferences, bookmarks) | Chosen folder handles (IndexedDB): the browser grants them to itself only |
| `podcasts`: the podcast library and gpodder server/user | Staged audio (origin-private file system): too large for `kv`'s 1 MiB per install |
| | Pocket Casts tokens and Cadence cloud keys: Wasmo's generic settings must not hold credentials |
| | Download target, readiness and positions sent from this device |

A profile or source that refers to a folder picked in another browser asks for the folder to be
chosen again here. Settings are read once at startup; two browsers open at the same time don't see
each other's changes until a reload, and the last write wins. `kv` takes values up to 256 KiB; a
podcast library larger than that stays in the browser, with a notice.

Without the service (hosted wasmo.com, or an install name not on the list) the page keeps
everything in the browser, as the static web build does, and says so in a dismissible notice.

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
2. In Wasmo, choose **Install App**, paste the URL, and name it `cadence` (or whichever name the
   operator lists in `WASMO_SERVICE_APPS`; other names work but keep settings in the browser).
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
