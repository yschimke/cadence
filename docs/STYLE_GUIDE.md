# Cadence design system

Cadence ships **two themes side-by-side**:

- **System** (default) — Material You dynamic colours from the device wallpaper
  paired with **Roboto Flex** as the type stack. Native, neutral, follows the
  user's wallpaper.
- **Cadence** (opt-in) — a Coastal Blue palette paired with
  **Manrope** for display roles and **Inter** for body roles. Distinct identity
  for users who want the app to look like it belongs with the headphones, not
  with the rest of the OS.

The user picks between them in **Manage → Appearance**. Both themes share the
same Material 3 component shapes, so switching only changes colour and
typography — no layout reflow.

Visual reference renders are in [`docs/design-work/`](./design-work/).

## Why two themes

The app's job is two-sided:

- **Curate mode** (default home) is something the user lives in for minutes at
  a time on their phone — it should feel like a Material 3 app among other
  Material 3 apps. System dynamic colour matches that.
- **Sync mode** is a focused takeover triggered by plugging in the headphones —
  here the brand identity earns its keep, signalling "you're now interacting
  with your headphones, not your phone."

The Cadence theme leans into that second mode but applies uniformly across
both screens so people who prefer the brand identity get it everywhere.

## Cadence palette

Three seeds drive the Material 3 tonal palette:

| Role | Seed | Hex | Use |
| --- | --- | --- | --- |
| `primary` | Coastal Blue | `#1B5174` | Deep, water-at-depth — references the headphone body finish |
| `secondary` | Amber accent | `#A23A0F` (light) / `#FFB59A` (dark) | Warm action / heads-up surfaces |
| `tertiary` | Pool Aqua | `#2D7FB8` | "Safe to disconnect" / sync-complete states |

Expanded into M3 roles:

| Role | Light | Dark |
| --- | --- | --- |
| `primary` | `#1B5174` | `#9CCBF0` |
| `onPrimary` | `#FFFFFF` | `#003352` |
| `primaryContainer` | `#CFE5FA` | `#003352` |
| `onPrimaryContainer` | `#001E33` | `#CFE5FA` |
| `secondary` | `#A23A0F` | `#FFB59A` |
| `onSecondary` | `#FFFFFF` | `#5C1A00` |
| `secondaryContainer` | `#FFDBCC` | `#822A04` |
| `onSecondaryContainer` | `#3A0E00` | `#FFDBCC` |
| `tertiary` | `#2D7FB8` | `#9CCBE8` |
| `tertiaryContainer` | `#CFE6F5` | `#003952` |
| `error` | `#BA1A1A` | `#FFB4AB` |
| `surface` | `#F8FAFD` | `#0F1416` |
| `surfaceVariant` | `#DDE3EA` | `#3F484F` |

### Where each role shows up

- `primary` / `onPrimary` — sync-mode top bar, primary buttons (`Sync now`,
  `Switch to sync mode`), sync-progress bar fills, the auto-refresh switch
  thumb when on.
- `primaryContainer` / `onPrimaryContainer` — profile-card folder badge,
  device-hero icon background, USB-attach banner.
- `secondaryContainer` — reserved for "live action / heads-up" UI surfaces; not
  used yet in the redesign but earmarked for upcoming sync-error retry banners
  so they read as "act, not danger."
- `tertiaryContainer` / `onTertiaryContainer` — sync-complete top bar and
  "All caught up · safe to disconnect" hero card. The aqua differentiation is
  the cleanest signal for "different state, also good."
- `errorContainer` / `error` — profile cards whose last refresh failed. Same
  errored profile becomes a disabled row in the sync checklist with a
  "fix in Curate" hint.
- `surface` / `onSurface` — page backgrounds and card surfaces.
- `surfaceVariant` / `onSurfaceVariant` — secondary text, profile-card chips,
  Bluetooth peek bar at the home bottom.

## Typography

### System theme — Roboto Flex

Material 3's "Expressive" recommendation. Variable axis covers all weights,
optical sizing handles small-to-large rendering. Loaded via the Google Fonts
downloadable provider so it weighs ~0 KB to the APK.

Material 3 default sizes / tracking are kept as-is — only the family is
swapped from the platform default.

### Cadence theme — Manrope + Inter

Two families, both variable, both via the Google Fonts downloadable provider:

- **Display / headlines / titles → Manrope.** Geometric sans with humanist
  warmth. Reads as "athletic device, deliberate" without feeling aggressive.
  Used for `display*`, `headline*`, `title*` roles.
- **Body / labels → Inter.** Workhorse UI sans with best-in-class small-size
  legibility. Used for `body*` and `label*` roles. Tabular figures matter for
  the sync progress numerals (`5 of 12 files`, `14 MB / 38 MB`) and Inter's
  defaults handle this well.

| Role | Family | Weight | Size / Line | Tracking |
| --- | --- | --- | --- | --- |
| `displayLarge` | Manrope | SemiBold | 57 / 64 | -0.25 |
| `displayMedium` | Manrope | SemiBold | 45 / 52 | 0 |
| `displaySmall` | Manrope | SemiBold | 36 / 44 | 0 |
| `headlineLarge` | Manrope | SemiBold | 32 / 40 | 0 |
| `headlineMedium` | Manrope | SemiBold | 28 / 36 | 0 |
| `headlineSmall` | Manrope | SemiBold | 24 / 32 | 0 |
| `titleLarge` | Manrope | SemiBold | 22 / 28 | 0 |
| `titleMedium` | Manrope | SemiBold | 16 / 24 | 0.15 |
| `titleSmall` | Manrope | SemiBold | 14 / 20 | 0.1 |
| `bodyLarge` | Inter | Normal | 16 / 24 | 0.5 |
| `bodyMedium` | Inter | Normal | 14 / 20 | 0.25 |
| `bodySmall` | Inter | Normal | 12 / 16 | 0.4 |
| `labelLarge` | Inter | Medium | 14 / 20 | 0.1 |
| `labelMedium` | Inter | Medium | 12 / 16 | 0.5 |
| `labelSmall` | Inter | Medium | 11 / 16 | 0.5 |

## Shape

Shared between both themes — slightly softer than M3 defaults so cards and
pills feel rounded enough to read as friendly without losing the technical
edge.

| Token | Radius |
| --- | --- |
| `extraSmall` | 6 dp |
| `small` | 10 dp |
| `medium` | 14 dp |
| `large` | 20 dp |
| `extraLarge` | 28 dp |

## Component conventions

These come out of the redesign in PR #25 / #28 and stay consistent across
themes:

- **Profile cards (Curate)** — `ElevatedCard` at `medium` shape. Folder badge
  is a 36 dp circle filled with `primaryContainer` (or `errorContainer` when
  the profile has a last-refresh error). Footer row hosts the `OutlinedButton`
  refresh + `Switch` for auto.
- **Sync ready row** — `ElevatedCard` with a `Checkbox` left, profile name +
  last-refresh metadata right. Errored profiles are `enabled = false` with a
  red error-coloured hint string.
- **Status pills (Curate top bar)** — `Surface` at `RoundedCornerShape(50)`,
  background `primaryContainer` when emphasised (USB / BT connected) or
  `surfaceVariant` when not. Icon on the left, label on the right.
- **Pinned bottom CTA** — `Surface` with `tonalElevation = 4 dp`, full-width
  `Button` (or `OutlinedButton` while a sync is running, to switch to Cancel).
- **Sync top bar** — `Surface` filled with `primaryContainer` while syncing,
  switching to `tertiaryContainer` once the sync completes. Title text uses
  `onPrimaryContainer` / `onTertiaryContainer` accordingly.

## Do / don't

- **Do** map every visual state to a Material 3 role (`primary`,
  `errorContainer`, `tertiaryContainer`, …) — never hardcode a hex value at a
  call site. Both themes must work without code changes.
- **Do** keep `MaterialTheme.typography.titleMedium` / `bodySmall` at call
  sites — the type swap between System and Cadence happens only at the theme
  level.
- **Don't** hardcode `Color.Black` / `Color.White` on text — use
  `onSurface` / `onPrimary` / `onPrimaryContainer` so dark mode and Cadence
  dark both render legibly.
- **Don't** introduce a third theme. If a screen needs an emphasised look,
  use `tertiaryContainer` (which Cadence sets to Pool Aqua) or
  `secondaryContainer` (Cadence's amber accent family).

## Files

```
composeApp/src/main/kotlin/ee/schimke/cadence/
├── AndroidMaterialTheme.kt            # System dynamic + Roboto Flex
└── theme/
    ├── CadenceTheme.kt                # Entry point: CadenceTheme { … }
    ├── CadenceThemeHost.kt            # Reads pref, picks System or Cadence
    ├── CadenceColor.kt                # Light + dark colour schemes
    ├── CadenceTypography.kt           # Manrope display + Inter body
    └── CadenceShape.kt                # Shared shape tokens

composeApp/src/main/res/values/
└── font_certs.xml                     # GoogleFont downloadable provider certs

shared/src/commonMain/kotlin/ee/schimke/cadence/
├── appearance/AppearanceViewModel.kt  # ThemeMode StateFlow + setter
└── data/AppearanceRepo.kt             # ThemeMode persistence on the Settings DataStore
```

The persisted preference lives under `Settings.appearance.theme_mode` (proto
field 7); default is `SYSTEM` so a fresh install behaves like the rest of
the OS.

## Visual reference

See [`design-work/`](./design-work/) for side-by-side renders of every redesign
screen under both themes (light + dark for Cadence).
