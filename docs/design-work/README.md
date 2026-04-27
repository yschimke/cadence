# Design-system reference renders

These PNGs are checked-in compose-preview output, captured against the
production composables (`HomeContent`, `FileSyncContent`, `ManageSyncContent`)
under both themes for side-by-side review. Regenerated via:

```sh
ANDROID_HOME=$ANDROID_SDK_ROOT compose-preview show --json --filter "Cadence|System|Manage"
```

then copied from `composeApp/build/compose-previews/renders/` here.

Companion document: [../STYLE_GUIDE.md](../STYLE_GUIDE.md).

## Curate (default home)

| | System (Roboto Flex + dynamic) | Cadence (Manrope/Inter + Coastal Blue) | Cadence dark |
| --- | --- | --- | --- |
| Curate home | ![](./01-curate-home-system.png) | ![](./02-curate-home-cadence.png) | ![](./03-curate-home-cadence-dark.png) |
| Curate + USB banner | ![](./04-curate-usb-banner-system.png) | ![](./05-curate-usb-banner-cadence.png) | — |

## Sync (USB-attached)

| | System | Cadence | Cadence dark |
| --- | --- | --- | --- |
| Sync ready | ![](./06-sync-ready-system.png) | ![](./07-sync-ready-cadence.png) | ![](./08-sync-ready-cadence-dark.png) |
| Syncing | ![](./09-sync-syncing-system.png) | ![](./10-sync-syncing-cadence.png) | — |
| Complete | ![](./11-sync-done-system.png) | ![](./12-sync-done-cadence.png) | — |

## Manage (deep config + Appearance toggle)

The `Manage` screen now hosts the Appearance toggle (`System` / `Cadence`) at
the top of the page.

![](./13-manage-populated.png)
