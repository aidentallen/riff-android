# Riff for Android

A native Android port of [rootscripts/riff](https://github.com/rootscripts/riff),
based on desktop revision `b2a0834`. The desktop source remains in the parent
directory. This project uses native Android views and AndroidX Media3; it does
not require a desktop computer, Electron, a hosted server, or an account.

## Install

Download [`riff-android.apk`](https://github.com/aidentallen/riff-android/releases/download/android-v1.0.0-preview/riff-android.apk)
from the [preview release](https://github.com/aidentallen/riff-android/releases/tag/android-v1.0.0-preview)
and install it on Android 8.0 or newer. Allow your browser
or file manager to install apps when Android asks. This is a debug-signed build
for personal testing, not a Play Store release. The smaller phone APK contains
ARM64; `riff-android-universal.apk` also supports ARMv7 and x86_64. No music or
example library is bundled.

Use Library → **+** to import files or a folder. Files are copied into private
app storage, so playback remains available without internet or broad storage
permission. Imports preserve title, artist, album, artwork, and duration when
those tags are readable. Music and app data are removed when the app is
uninstalled. Use a track's **Export audio** or **Share audio** action to keep a
copy outside Riff.

## Features

| Desktop feature | Android implementation |
| --- | --- |
| Local music | File and folder import, album browsing, local search, metadata and artwork |
| YouTube / SoundCloud | On-device search, streaming, HTTPS link lookup, Android share-to-Riff |
| Offline downloads | MP3, M4A, FLAC, Opus and WAV with quality choices; bundled yt-dlp/FFmpeg, foreground progress and cancel |
| Playlists | Create, rename, delete, add/remove tracks; liked songs |
| Playback | Native background service, lock-screen/media notification, headset controls, audio focus, unplug-to-pause |
| Queue | Play all, play next, shuffle, repeat one/all, seeking, queue viewer/removal and position restoration; tap Now Playing to open the queue |
| Lyrics | LRCLIB lookup, offline caching, LRC import, editable plain/synced lyrics, manual tap-to-time, highlighted line and tap-to-seek |
| Audio effects | Independent speed and pitch, equalizer, bass boost, echo, distortion and room/hall reverb |
| Audio editor | Trim a local track to a new MP3 without changing the original; export and share |
| Preferences | Four accent colors, online lyrics toggle, sleep timer, yt-dlp engine update |

Online sources can change extraction requirements or reject requests, and some
tracks require an account or are unavailable. The app reports extraction errors
and has a download-engine update action in Settings. Only YouTube and SoundCloud
HTTPS links are accepted. Audio effects depend on the device's effect support.

Desktop Discord RPC, Electron updates, desktop window controls, discovery/vibe
recommendations, waveform editing, word-level lyric timing, and the desktop HQ
audio graph are not ported. Android's notification and media-session controls
replace desktop media controls. Converting to FLAC or WAV preserves the available
source quality; it cannot recover fidelity lost by the source. Edited track details are saved in
Riff's library; trimming embeds title/artist in the new MP3.

## Build

Open this directory in Android Studio or use JDK 17 and an Android SDK:

```sh
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
# Set ANDROID_HOME, or put sdk.dir=/your/android/sdk in local.properties.
./gradlew :app:assembleDebug :app:lintDebug :app:testDebugUnitTest
```

The phone APK is `app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`; the
universal APK is `app/build/outputs/apk/debug/app-universal-debug.apk`. The checked-in Gradle
wrapper uses Gradle 8.11.1 and Android Gradle Plugin 8.9.2. Internet access is
required for the first dependency download. To make a distributable release,
configure your own signing key and build `:app:assembleRelease` or
`:app:bundleRelease`; no production signing key is supplied.

For device tests, connect an Android emulator/device and run:

```sh
./gradlew :app:connectedDebugAndroidTest
```

These tests generate their own short WAV fixture and verify import, deduplication,
playlist storage, lyric caching, native yt-dlp execution, FFmpeg trimming,
playback, seeking, and continued playback after leaving the activity. Unit tests
cover LRC fractions, repeated timestamps, offsets, plain lyrics, and line timing.

## Structure

- `MainActivity`: phone UI, navigation, file picker, playlists, player, lyrics and effects.
- `PlaybackService`: Media3 player/media session, background playback and effects.
- `DownloadService`: serial foreground downloads, progress and cancellation.
- `Online`: yt-dlp initialization, search and secure stream resolution.
- `Library`: atomic on-device track, favorite and playlist storage.
- `Importer`, `Lyrics`, `Editor`: metadata, lyric parsing/cache and FFmpeg trimming.

See [VALIDATION.md](VALIDATION.md) for tested behavior,
[the root README](../README.md) for screenshots and desktop setup, and
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for licensing.
