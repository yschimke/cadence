# Cadence's Livery catalog

The catalog the app ships inside its APK (the `ee.schimke.composeai.livery.catalog` Gradle plugin
builds it into the assets on every build), and later publishes for the app to download.

- `contracts/headphone-controls.json`: what the headphone launcher widget's templates read and emit.
- `designs/headphone-default/`: one template per size tier (`launcher-w<min width>-h<min height>`),
  captured from `HeadphoneControlsTemplateContent` (shared module) with every value named and the
  widget's states as conditional layouts.

The templates are generated: after changing the widget's drawing code, run

    ./gradlew :composeApp:testDebugUnitTest --tests '*HeadphoneTemplatesTest*' -Pcadence.livery.regenerate=true

Without the property the same test fails when a committed template is stale.
