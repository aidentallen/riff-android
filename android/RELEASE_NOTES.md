# Riff for Android · 1.0 preview

A native Android port of [rootscripts/Riff](https://github.com/rootscripts/riff),
with the complete desktop project preserved alongside the new app.

## Downloads

| Asset | Use it for |
| --- | --- |
| **riff-android.apk** | Most modern Android phones (ARM64, about 59 MiB) |
| **riff-android-universal.apk** | ARM64, older ARMv7 phones and x86_64 emulators (about 151 MiB) |
| **riff-project-source.zip** | Complete Android and desktop source, build scripts, tests, documentation and screenshots |
| **SHA256SUMS.txt** | SHA-256 checksums for the APKs and source archive |

Requires **Android 8.0+**. These are debug-signed preview APKs for personal testing,
not a Play Store release. Open an APK to install it, then use **Library → +** to
import music. The app starts with an empty library; no music is bundled.

Imported and downloaded files live in private app storage. Export or share audio
you want to keep before uninstalling.

## Included

- Offline library, album browsing, favorites and playlists.
- Background playback, lock-screen/media notifications, headset controls and queue management.
- On-device YouTube/SoundCloud search, streaming and downloads with bundled yt-dlp/FFmpeg.
- Synced lyrics, LRC import, offline cache and manual lyric timing.
- Speed/pitch controls, EQ, bass, echo, distortion and reverb.
- FFmpeg trimming to a separate MP3, export and sharing.
- Accent colors, sleep timer and download-engine updates.

## Verification and current limits

Builds and APK signature checks passed. Android lint reported zero errors, all
6 unit tests passed, and 3 integration checks passed on an Android 9 emulator,
including background playback, native yt-dlp execution and FFmpeg trimming.

Live online extraction/lyrics, background downloading on a physical phone and
device-specific native effects have not yet been verified end to end. Use
**Settings → Download engine** if an online source needs a newer yt-dlp version.
See [validation notes](https://github.com/aidentallen/riff-android/blob/main/android/VALIDATION.md)
for the exact test coverage.

The new Android port is **GPL-3.0-or-later**. Original Riff desktop code and assets
retain their **MIT** license and rootscripts attribution. Dependency sources and
licenses are listed in the repository and in the app's **About → Licenses** screen.
