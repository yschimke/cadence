# Cadence desktop workspace evidence

Captured from the running Linux X11 JVM app at 1364×1024, using an empty, isolated test profile.
No real library, cloud account, or USB device appears in these images.

- `01-library.png`: the persistent desktop sidebar alongside the existing Library content.
- `02-navigation-menu.png`: the native Go menu and Ctrl+1–6 shortcuts; Sources uses desktop folder guidance.
- `03-fullscreen.png`: the same workspace in full screen.

Run `./gradlew :desktopApp:run`. Use the sidebar or Go menu to switch among Library, Episodes,
Devices, Sources, Transfers, and Cloud backup. F11 enters/leaves full screen on Linux; the Window
menu exposes the same action. Opening Add a folder uses the existing Swing directory picker,
now owned by the app window. Its native-dialog replacement is a separate follow-up.

Validation: desktop, Android shared, and Wasm shared compilation; Kotlin formatting; manual
navigation through all six destinations, folder-picker open/cancel, and full-screen behavior.
macOS menu/Dock behavior and Wayland window management still need platform verification.
