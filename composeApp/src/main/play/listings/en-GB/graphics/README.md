# Play Store graphics

Drop image files directly into the subdirectories below. Gradle Play
Publisher (GPP) auto-uploads them on `publishBundle`. All files in a
given directory are treated as the set of images for that asset, in
filename order.

| Directory | Required? | Format | Dimensions | Notes |
|---|---|---|---|---|
| `icon/` | yes | PNG, 32-bit, no transparency | exactly **512 × 512** | Hi-res app icon shown on the Play listing. Different from the launcher icon. |
| `feature-graphic/` | yes | PNG / JPEG | exactly **1024 × 500** | Banner at the top of the listing. No transparency. |
| `phone-screenshots/` | yes (≥ 2, ≤ 8) | PNG / JPEG | between 320 px and 3840 px on either side; aspect ratio 16:9 or 9:16 | The screenshots Google shows on phone listings. Currently 1078×1918 (Pixel 8a viewport at 9:16). |
| `seven-inch-screenshots/` | recommended (≥ 2, ≤ 8) | PNG / JPEG | between 320 px and 3840 px on either side | Shown on 7″-class tablets. Currently 1200×1920. |
| `ten-inch-screenshots/` | recommended (≥ 2, ≤ 8) | PNG / JPEG | between 320 px and 3840 px on either side | Shown on 10″-class tablets. Currently 1600×2560. |
| `tv-banner/` | leave empty | — | — | TV app variant only — phone publishing skips this. |

The current PNGs are rendered from `@Preview` composables in
`composeApp/src/main/kotlin/ee/schimke/cadence/preview/playstore/PlayStorePreviews.kt`
via the `compose-preview` CLI:

```sh
ANDROID_HOME=/path/to/sdk compose-preview render --filter PlayStore
```

Then copy the matching PNGs from `composeApp/build/compose-previews/renders/`
into the directories above. Re-run after any UI change that should be
reflected on the listing.

Filenames don't matter; sort order does. Use `01-home.png`,
`02-detail.png`, etc. if you want to control ordering.
