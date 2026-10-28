# Cadence design system

## One theme

Cadence follows the device: **Material 3 dynamic colour** from the wallpaper,
light or dark with the system, and **Roboto Flex** for every type role
(`AndroidMaterialTheme`). There is no theme setting. Desktop and the browser
have no wallpaper colours, so they use the Material 3 baseline scheme
(`PortableApp`).

Roboto Flex is loaded through the Google Fonts downloadable provider, so it adds
nothing to the APK. Material 3 sizes and tracking are kept; only the family
changes.

The fixed `CadencePalette` remains only for surfaces that can't follow the app
theme, such as the home screen widget.

## Content first

- **The home screen is the library.** It shows what each source holds: podcast
  episodes with progress, and the files in each folder. Nothing else competes
  with it.
- **One way to add things.** "Add source" offers three kinds: a folder, files
  from another app (storage apps appear in the same system folder picker), or
  podcasts. Each podcast service shows only its own form.
- **Empty means one prompt.** With no sources, the home screen only offers
  those three choices. Once something is added, the choices move behind the
  "Add source" button.
- **No modes to manage.** Folders join a single automatic "Library" profile and
  are staged as soon as they're added. Profiles, NFS shares and USB matching
  stay in **Manage sources**, off the main path.
- **Headphones appear when connected.** The bottom bar shows only while USB or
  Bluetooth headphones are connected, with the one useful action.
- **Advanced settings are collapsed.** Audio splitting and cross-device backup
  sit in collapsed sections below the episodes.

## Component conventions

- **Source section** — `OutlinedCard` with a header row (icon, name, one-line
  summary, overflow menu for Refresh / Remove) and `ListItem` rows below,
  separated by `HorizontalDivider`. Folders show five files, then "Show all".
- **Episode row** — title, time left or total length, and a
  `LinearProgressIndicator` once started.
- **Add source** — `ModalBottomSheet` with transparent `ListItem`s, the same
  rows as the empty state.
- **Collapsed options** — `OutlinedCard` whose header toggles the content.
- Toggle rows put `toggleable` on the whole row and keep a 48 dp minimum
  height, so the label is the accessible name and the target is large enough.

## Do / don't

- **Do** map every state to a Material 3 role (`primary`, `errorContainer`,
  `secondaryContainer`, …); never hardcode colours at call sites.
- **Do** report results in the content itself or a snackbar, not a permanent
  status line.
- **Don't** add settings for things the system already decides, such as theme
  or dark mode.
- **Don't** show options for sources the user hasn't chosen.

## Files

```
composeApp/src/main/kotlin/ee/schimke/cadence/AndroidMaterialTheme.kt  # Dynamic colour + Roboto Flex
shared/src/commonMain/kotlin/ee/schimke/cadence/PortableApp.kt          # Baseline scheme off Android
shared/src/commonMain/kotlin/ee/schimke/cadence/home/HomeScreen.kt      # Library, add-source sheet
shared/src/commonMain/kotlin/ee/schimke/cadence/podcasts/PodcastScreen.kt # Add podcasts, episodes
```

`Settings.appearance` (proto field 7) is no longer read; it stays in the schema
so existing settings still parse.

## Visual reference

The `@Preview`s in `composeApp/src/main/kotlin/ee/schimke/cadence/preview/`
render every screen state; CI publishes them to the preview catalog on each
pull request.
