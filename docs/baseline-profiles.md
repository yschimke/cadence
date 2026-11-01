# Android baseline profiles

The phone and Wear apps each have their own AndroidX Baseline Profile generator.
The startup journey contributes to both the Startup and Baseline Profiles; a
separate browsing gesture contributes only to the Baseline Profile. The generated
rules are saved under each app's `src/release/generated/baselineProfiles/` and
are consumed by release builds. The Baseline Profiles workflow generates both
profiles on every pull request, on main, and on demand, and uploads them as
artifacts. The release workflow generates them for the release tag and downloads
them before building the APK and bundles. Normal local builds do not run the
generators or require a device.

To generate or refresh a profile, start a dedicated emulator or connect an API
33+ device and select exactly that device with `ANDROID_SERIAL`:

```sh
ANDROID_SERIAL=<phone-serial> ./scripts/generate-baseline-profile.sh composeApp
ANDROID_SERIAL=<watch-serial> ./scripts/generate-baseline-profile.sh wearApp
```

Check that both `baseline-prof.txt` and `startup-prof.txt` are populated in the
corresponding generated source directory. Some Android test runner failures can
leave Gradle reporting success without producing rules, so also inspect the
instrumentation output if either file is missing.

Run the commands separately. Both apps intentionally use the same application
ID, so generating on the wrong device can replace its existing Cadence app.
Generation installs a non-debuggable test build, repeatedly launches it, and
interacts with its UI. Prefer a dedicated test emulator/device; a differently
signed installed app must be removed before the test build can be installed.
The release APK must also be installable for the generator: configure the normal
release signing credentials if your build setup requires them.

Regenerate after changing startup or the journeys. Profile generation needs
representative state to capture paths beyond an empty app; the current journeys
cover startup and scrolling the phone home screen or swiping the Wear pager.
Library profiles are merged as well. To verify release packaging, inspect the
APK for `assets/dexopt/baseline.prof`. Measure performance on a physical device
before claiming an improvement.
