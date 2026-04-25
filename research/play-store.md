# Automated Play Store releases on git tag

Captured 2026-04-25. Goal: pushing a git tag (e.g. `v0.1.0`) builds a signed
AAB from `composeApp` and uploads it to a Play Console track. No manual steps
in Play Console for routine releases; manual promotion only when crossing
internal → production.

## The three real options

All three drive the same Google Play Developer Publishing API v3. The
service-account JSON flow is still the only supported auth — no workload
identity federation, no first-party "release on GitHub tag" button in Play
Console as of Apr 2026.

### 1. Gradle Play Publisher (`com.github.triplet.play`)

- Latest: **4.0.0** (2025-01-25). Actively maintained. 3.x line is
  parked at **3.13.0** (2024-12) for projects on older AGP.
- 4.x **requires AGP 9+**. This repo is on AGP 9.2.0 already
  (see `gradle/libs.versions.toml`), so use **4.0.0**.
- What it does: full Gradle integration. Tasks `publishBundle`,
  `promoteArtifact`, `publishListing`, `publishReleaseNotes`. Handles
  staged rollouts, track promotion, listings, screenshots, release notes
  — i.e. everything the Publishing API exposes, not just the upload.
- Auth: `serviceAccountCredentials` DSL or `ANDROID_PUBLISHER_CREDENTIALS`
  env var.
- Fits this repo: Gradle plugin slots in next to the existing KMP/AGP
  plugins. No extra runtime (no Ruby, no Node action wrapper).

### 2. fastlane `supply`

- fastlane 2.233+ line, actively maintained.
- Same API surface as GPP. Adds Ruby toolchain to the runner; that's the
  main cost. Worth it if we'd also use fastlane for iOS (`pilot`, `match`)
  — and we will, eventually, since this is KMP and iOS is on the roadmap.
- Auth: same service-account JSON.
- Fits this repo: overkill **today** (Android-only releases), arguably
  the right call once iOS ships, because one tool covers both stores.

### 3. `r0adkll/upload-google-play` GitHub Action

- Latest: **v1.1.5** (2024-04-21). Maintained but low velocity; long
  issue queue.
- Thin Node wrapper around `googleapis`. Uploads one AAB/APK to one
  track with optional mapping/debug-symbols, release notes, user
  fraction. **Doesn't** do listings, screenshots, or promote-existing-
  release.
- We'd still build and sign the AAB ourselves in a separate step.
- Fits this repo: simplest path if all we want is "upload the AAB I just
  built." No Gradle plugin to learn, no Ruby. Trade-off: anything beyond
  upload (listing changes, promotion, rollout halt) is back to the Play
  Console UI.

### Not viable

- No official Google-published Action exists. Forks of `r0adkll` exist
  but none have meaningful traction.
- No KMP-aware publisher — Play Store only sees the Android AAB anyway,
  so KMP-awareness is unnecessary.

## Recommendation

**Start with Gradle Play Publisher 4.0.0 + GitHub Actions on tag push.**
Reasoning:

- The Play release config (track, rollout %, release notes source)
  belongs in the build, not the CI YAML. Keeps the workflow file
  small and the policy reviewable in Gradle.
- One tool covers upload + promotion + listing sync, so we don't end
  up with three actions stitched together when we want to halt a
  rollout or promote internal → production from CI.
- No extra runtime on the runner — JDK is already there for the build.

Revisit fastlane the day we publish iOS. At that point `supply` (Android)
+ `pilot`/`deliver` (iOS) under one Fastfile beats two separate stacks.

## Signing

Two viable routes; both work with GPP.

1. **Play App Signing (recommended).** Generate one upload keystore
   locally, register the upload certificate with Play once, then Play
   re-signs with the app signing key it holds. Compromised upload key
   is recoverable via Play support; compromised app signing key is not.
   This is the default for new apps since 2021 anyway.
2. **Self-managed app signing key.** Only if there's a reason to keep
   the signing key off Google's servers. Not worth the operational
   risk for a side project.

Either way, the upload keystore lives as a base64-encoded GitHub
Actions secret (`UPLOAD_KEYSTORE_BASE64`), decoded into a tmpfile at
build time, with `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` as
plain secrets. Wire into `composeApp/build.gradle.kts` via a
`signingConfigs.create("release")` block that reads env vars and
silently skips when they're absent (so local debug builds still
work).

## Versioning from the tag

Drive both `versionName` and `versionCode` from the tag — having them
diverge from git is the usual source of "which build is in production
again?" confusion.

- Tag format: `v<major>.<minor>.<patch>` (e.g. `v0.1.0`).
- `versionName` = tag minus the leading `v`.
- `versionCode` = monotonic integer. Two reasonable sources:
  - `git rev-list --count HEAD` — deterministic, survives rebuilds,
    but requires full clone in CI (`fetch-depth: 0`).
  - `${{ github.run_number }}` — simpler, but resets if you ever
    move the repo or recreate the workflow. Avoid.
  Prefer the rev-list count.
- Read both in `composeApp/build.gradle.kts` from env vars set by the
  workflow; fall back to `1` / `0.0.0-dev` for local builds.

## Track strategy

Don't push tags straight to production. Two-step:

1. Tag `v<x.y.z>` → CI builds, signs, uploads to **internal** track at
   100% rollout. This is the Play "internal testing" track — instant
   review, up to 100 testers, available within minutes.
2. Promote internal → production manually (Play Console UI) **or** via
   a separate workflow triggered by a `release-v<x.y.z>` tag that runs
   GPP's `promoteArtifact` task.

Rationale: internal track gates on a real "does the upload work, does
it install, does the signing match" check before anything user-visible.
Production rollouts almost always want a staged % anyway, which is
easier to drive from the Play Console for a one-person release.

## Service-account setup (one-off)

Per Google's current docs (Apr 2026):

1. Create a GCP project; enable the Google Play Android Developer API.
2. Create a service account in GCP; generate a JSON key.
3. In Play Console → **Users and permissions** → **Invite new users**,
   invite the service account's email and grant: **Release manager**
   on the app, plus **View app information** at the account level.
   Strictly scope to this app — service-account compromise should not
   blast-radius into other apps.
4. **Wait up to 36 hours** before the first API call works. Google
   documents this propagation delay; first-time setups that "fail
   immediately" are usually this.
5. Store the JSON as `PLAY_SERVICE_ACCOUNT_JSON` GitHub secret.

## Workflow sketch

`.github/workflows/release.yml`:

```yaml
on:
  push:
    tags: ['v*.*.*']

jobs:
  release:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with: { fetch-depth: 0 }
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: 17 }
      - name: Decode keystore
        run: echo "$UPLOAD_KEYSTORE_BASE64" | base64 -d > "$RUNNER_TEMP/upload.jks"
        env:
          UPLOAD_KEYSTORE_BASE64: ${{ secrets.UPLOAD_KEYSTORE_BASE64 }}
      - name: Publish to internal track
        run: ./gradlew :composeApp:publishBundle
        env:
          VERSION_NAME: ${{ github.ref_name }}  # strip leading v in build.gradle.kts
          KEYSTORE_PATH: ${{ runner.temp }}/upload.jks
          KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
          KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
          KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
          ANDROID_PUBLISHER_CREDENTIALS: ${{ secrets.PLAY_SERVICE_ACCOUNT_JSON }}
```

GPP block in `composeApp/build.gradle.kts`:

```kotlin
play {
    track.set("internal")
    defaultToAppBundles.set(true)
    releaseStatus.set(ReleaseStatus.COMPLETED)
    // releaseNotes pulled from src/main/play/release-notes/en-US/internal.txt
}
```

Release notes live in-repo under `composeApp/src/main/play/`, not in
the workflow YAML — that's the directory layout GPP expects.

## Open questions before implementing

- Do we want the tag to push to production directly with a 1% staged
  rollout, or always land in internal first? Answer drives whether
  there's one workflow or two.
- App ID `ee.schimke.shokz` — has it been reserved in Play Console
  yet? Service-account propagation can't start until the app exists
  and a first manual upload has happened (Play won't accept an
  API-only first upload).
- Is there an existing keystore from an earlier Play upload, or do
  we generate a fresh one? If fresh, this is also the moment to
  decide on Play App Signing.

## Implemented (2026-04-25)

GPP 4.0.0 is now wired into `composeApp/build.gradle.kts` with a
`play { track = "internal" }` block that no-ops when
`ANDROID_PUBLISHER_CREDENTIALS` is unset. Trigger is `release-build.yml`
on a `release: published` event from release-please (not a raw tag push,
since release-please owns the tag lifecycle on this repo).

Differences from the original sketch above:

- **Trigger.** release-please creates the tag + GitHub Release on its own
  cadence; `release-build.yml` already listened on `release: published`,
  so the Play upload was bolted onto the existing job rather than a new
  workflow.
- **Versioning.** `versionName` is owned by release-please via
  `// x-release-please-version`. `versionCode` is derived deterministically
  from `versionName` (MAJOR\*10000 + MINOR\*100 + PATCH) — no git-history
  read, no env var. Capped at major < 22 which is plenty.
- **Signing.** `signingConfigs.release` reads `SHOKZ_KEYSTORE_PATH` /
  `_PASSWORD` / `SHOKZ_KEY_ALIAS` / `SHOKZ_KEY_PASSWORD` from env. Local
  builds without those env vars skip the signing config entirely (so
  `:assembleRelease` still works for local profiling, just unsigned).
- **Secrets.** `SIGNING_KEYSTORE` (base64 keystore),
  `SHOKZ_KEYSTORE_PASSWORD`, `SHOKZ_KEY_ALIAS`, `SHOKZ_KEY_PASSWORD`,
  `PLAY_SERVICE_ACCOUNT_JSON`. The Play upload step gates on both the
  keystore and the credentials being present, so a release without them
  configured still produces an unsigned APK on the GitHub release —
  it just skips Play.

What is **not** yet done — needs a human:

- App `ee.schimke.shokz` reserved in Play Console + first manual upload
  (Play won't accept an API-only first upload).
- Service account created, granted Release Manager on the app, JSON
  installed as `PLAY_SERVICE_ACCOUNT_JSON`. Wait 36h after granting
  before the first run.
- Upload keystore generated and registered for Play App Signing;
  base64 → `SIGNING_KEYSTORE` secret.
- Promotion internal → production stays manual in Play Console for now;
  if/when we want it automated, add a workflow that runs
  `:composeApp:promoteArtifact --from-track internal --to-track production`
  on a separate trigger.

## Sources

- [Triple-T/gradle-play-publisher releases](https://github.com/Triple-T/gradle-play-publisher/releases)
- [r0adkll/upload-google-play releases](https://github.com/r0adkll/upload-google-play/releases)
- [fastlane releases](https://github.com/fastlane/fastlane/releases)
- [Google Play Developer API — Getting Started](https://developers.google.com/android-publisher/getting_started)
- [Google Play Developer API — Authorization](https://developers.google.com/android-publisher/authorization)
