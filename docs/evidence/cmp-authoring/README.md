# Compose-authored notification body, platform player

`PlatformPlayerTest` renders the bundled notification template with the platform's own Remote Compose
player (Robolectric, mdpi, 200x64dp notification body). `before-*` is the AndroidX-authored template on
`main`; `after-*` is the template authored with rc-players' CMP creation API (rc-players 3.1.3, Livery 0.11.0).

- `*-api36-*`, `*-api37-*`: Android 16 and Android 17 players.
- `downloading` has the progress bar and Cancel; `syncing` has the progress bar only.

Both versions draw the icon, the swim progress bar and the Cancel pill on both APIs. Text (title, body,
Cancel label) is blank on API 36 before and after; it is not caused by this port.

Reproduce: `./gradlew :composeApp:testDebugUnitTest --tests '*PlatformPlayerTest'`; PNGs land in
`composeApp/build/platform-player/sdk<N>/`.
