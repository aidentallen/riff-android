# Contributing to Riff for Android

This repository contains a native Android community port and the original
Electron desktop source. For Android bugs and changes, use this repository's
[issues](https://github.com/aidentallen/riff-android/issues) and pull requests.
For the upstream desktop project, see [rootscripts/riff](https://github.com/rootscripts/riff).

## Report a problem

Include the app version, Android version, device model/architecture, steps to
reproduce, expected behavior and the error shown in the app. Screenshots help
with layout issues. Remove personal information from logs before attaching them.

For YouTube or SoundCloud failures, try **Settings → Download engine** first.
Include a public example link when possible and say whether search, streaming
or downloading failed.

## Android development

Open `android/` in Android Studio, or install JDK 17 and Android SDK 35, then run:

```sh
cd android
./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest
```

With a connected device or emulator, run:

```sh
./gradlew :app:connectedDebugAndroidTest
```

Use `gradlew.bat` on Windows. Device tests create their own short audio fixture.
See [the Android guide](android/README.md) and [validation notes](android/VALIDATION.md)
for architecture, implementation details and current coverage.

Keep changes focused and describe the behavior they change. Include the checks
you ran, and update documentation when installation or behavior changes. Add a
test when it meaningfully verifies new parsing, storage or playback behavior.
The Android CI workflow checks builds, lint and unit tests on pushes and pull
requests.

## Release notes

Android releases use `android-v…` tags. Desktop `v…` release automation is scoped
to the original upstream repository. Production Android releases need a private
signing key; the preview APKs use a debug certificate. Never commit keystores,
signing passwords, `.env` files or `local.properties`.

To publish the current preview, open **Actions → Android → Run workflow**, choose
`main` and check **Publish the current Android preview APKs and source archive**.
After the build, lint and unit tests pass, the release job uploads the phone APK,
universal APK, full project source archive and SHA-256 checksums, then publishes
`android-v1.0.0-preview`. It refreshes that preview's assets; use a new version/tag
for a distinct release. Update [release notes](android/RELEASE_NOTES.md) first.

Keep generated APKs and build directories out of git. Attach installable builds
and checksums to GitHub Releases instead. Android changes are GPL-3.0-or-later;
the upstream desktop source retains its MIT license and attribution.
