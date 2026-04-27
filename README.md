Cadence is a Compose Multiplatform app targeting Android.

* `/composeApp` — the Android application module (Compose Multiplatform-based).
  - `commonMain` for shared code, `androidMain` for Android-only code.
* `/shared` — pure-Kotlin library code consumed by `:composeApp`.

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html).

## Code formatting

This project uses [ktfmt](https://github.com/facebook/ktfmt) (Google style, 2-space indent).

```sh
./gradlew ktfmtFormat   # apply formatting
./gradlew ktfmtCheck    # verify, fails the build on any unformatted file
./gradlew installGitHooks  # one-off: register .githooks/pre-commit
```

The pre-commit hook runs `ktfmtCheck` and aborts the commit if anything is unformatted.
CI runs the same check via `.github/workflows/ktfmt.yml`.