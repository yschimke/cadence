# Cadence UI Builder designs

These `.uid` files are editable UI Builder studies of Cadence's mobile screens, major components, Wear screens and widgets. The repository paths are their canonical home. `index.json` maps each design to its implementing source file.

The starting designs are hand-built from Material 3, Wear Material 3 and Remote Compose catalog primitives. They are not generated from the app, are not pixel-identical to it, and do not yet drive production UI. In particular, the swimmer, app-specific icons, dynamic data, navigation and actions must be recreated or connected when a design is implemented. Screens currently contain independent component studies: editing a component file does not automatically update the corresponding screen file.

Open a `.uid` file in a UI Builder file-enabled host. Use the catalog pin in the document; the phone catalog is `m3-catalog`, Wear screens use `wear-m3`, and widgets use `remote-m3`. Designs use representative fixture data and contain no account credentials.

Edit the repository file as the source of truth. Validate and render it after each change, then separately adapt and verify the application's Compose code. See each `source` entry in `index.json` for the implementation to compare. The app's existing Compose previews remain the source for actual rendered app behavior.

## Current tooling limitations

The hosted UI Builder catalog validated these documents, including their repository homes. The local `compose-preview 2.57.0` server available during creation did not accept the newer `home` field or the hosted Wear/Remote Compose catalog revisions. It could render temporary mobile copies after removing `home`, but opening these canonical files in that older local editor has not been verified. Use a compatible file-enabled editor/catalog version; do not remove `home` from the canonical files to work around the version mismatch.
