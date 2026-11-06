# Cadence's Livery catalog

The catalog the app ships inside its APK (the `ee.schimke.composeai.livery.catalog` Gradle plugin
builds it into the assets on every build), and publishes for the app to download.

Every change here is a pull request (Livery's [repository mode]):

- **Review**: `.github/workflows/livery-catalog.yml` builds the tree, compares it with `main` and
  comments on the pull request with what changed (templates, designs, seasons). It fails on a change
  that would break installed apps.
- **Publish**: merging to `main` signs the catalog with the `LIVERY_SIGNING_KEY` secret (key id
  `cadence-1`) and publishes it to the rolling [`catalog` release]. A weekly run publishes it again
  so a downloaded catalog never expires. The app downloads it about once a day
  (`CatalogRefreshWorker`) and trusts only that key (`WidgetLivery`); until then, and whenever a
  download is missing or invalid, it draws the catalog in the APK.
- **Roll back**: revert the pull request; the revert publishes like any other change.

[repository mode]: https://github.com/yschimke/livery/blob/main/site/pages/repository-mode.md
[`catalog` release]: https://github.com/yschimke/cadence/releases/tag/catalog

- `contracts/headphone-controls.json`: what the headphone launcher widget's templates read and emit.
- `designs/headphone-default/`: one template per size tier (`launcher-w<min width>-h<min height>`),
  captured from `HeadphoneControlsTemplateContent` (shared module) with every value named and the
  widget's states as conditional layouts.

The templates are generated: after changing the widget's drawing code, run

    ./gradlew :composeApp:testDebugUnitTest --tests '*HeadphoneTemplatesTest*' -Pcadence.livery.regenerate=true

Without the property the same test fails when a committed template is stale.

## Seasons

A season swaps the widget's design for everyone between two dates, without an app release. Each
seasonal design has `"install": "Always"` and `"automatic": false`, so apps ship it ahead of time but
draw it only in its season, and a file in `seasons/` sets the dates:

| Season | Design | Dates (UTC, end exclusive) |
|---|---|---|
| `winter_2026` | `headphone-winter`: night sky, snow | 1 Dec to 20 Dec 2026 |
| `christmas_2026` | `headphone-christmas`: pine and red, twinkling lights | 20 Dec to 28 Dec 2026 |
| `new_year_2027` | `headphone-new-year`: midnight and gold, fireworks | 28 Dec 2026 to 4 Jan 2027 |
| `winter_2027` | `headphone-winter` | 4 Jan to 1 Feb 2027 |
| `halloween_2027` | `headphone-halloween`: purple night, moon and embers | 24 Oct to 2 Nov 2027 |

Two seasons can't overlap on one contract (`livery build` refuses it), so winter is split around
Christmas and New Year. The looks are `HeadphoneLook` in the shared module, and their backdrops
move only while audio plays. Merge a season well ahead of its start: apps download its design with
the catalog, so the switch itself needs no network.

To add a look, add it to `HeadphoneLook`, write its `designs/<id>/design.json`, capture its
templates with the command above, and add a season file. While the new design has no templates
yet, add `-x :composeApp:buildLiveryCatalog` to that command, because the catalog build refuses a
design with no templates.
