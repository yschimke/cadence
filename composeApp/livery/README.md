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

A season swaps the widget's design for everyone between two dates, without an app release: add a
design with `"install": "Always"` and `"automatic": false` (so it draws only in its season), and a
file in `seasons/`:

```json
{ "id": "winter_2026", "start": "2026-12-01", "end": "2027-01-07",
  "designs": { "cadence/headphone-controls@1": "headphone-winter" } }
```

`start` is inclusive and `end` exclusive (midnight UTC). Merge it well ahead of `start`: apps
download the design with the catalog, so on the day the switch needs no network.
