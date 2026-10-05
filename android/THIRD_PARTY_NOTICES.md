# Licenses and source

The new Android port is licensed under **GPL-3.0-or-later** because the bundled
youtubedl-android and FFmpeg distribution is GPL-licensed. The complete GPL text
is in `LICENSE`. The original Riff code and icon retain their MIT license and
rootscripts attribution, preserved in `UPSTREAM-LICENSE`. The parent desktop
project's MIT license is unchanged.

| Component | Version / source | License |
| --- | --- | --- |
| Riff name/icon and desktop reference | [rootscripts/riff](https://github.com/rootscripts/riff/tree/b2a0834) | MIT |
| AndroidX Media3 | 1.6.1, [source](https://github.com/androidx/media/tree/1.6.1) | Apache-2.0 |
| AndroidX Core | 1.15.0, [source](https://android.googlesource.com/platform/frameworks/support/) | Apache-2.0 |
| youtubedl-android library/common/ffmpeg | 0.18.1, [source and native build instructions](https://github.com/JunkFood02/youtubedl-android) | GPL-3.0 |
| yt-dlp | Packaged by youtubedl-android; [source](https://github.com/yt-dlp/yt-dlp) | Unlicense, with separately licensed bundled components |
| FFmpeg | Packaged by youtubedl-android; [native build instructions](https://github.com/JunkFood02/youtubedl-android/blob/master/BUILD_FFMPEG.md), [source](https://ffmpeg.org/download.html) | GPL/LGPL according to enabled components; this distribution is GPL |
| Python runtime | Packaged by youtubedl-android; [native build instructions](https://github.com/JunkFood02/youtubedl-android/blob/master/BUILD_PYTHON.md) | PSF and bundled component licenses |
| Jackson | Transitive via youtubedl-android | Apache-2.0 |
| Kotlin standard library | Transitive via youtubedl-android | Apache-2.0 |
| Apache Commons IO | Transitive via youtubedl-android | Apache-2.0 |
| desugar_jdk_libs | 2.1.5, [source](https://github.com/google/desugar_jdk_libs) | GPL-2.0 with Classpath Exception and other component licenses |

Lyrics are supplied by [LRCLIB](https://lrclib.net/). Service content is not part
of this source distribution. All source for the new app and its build scripts
is supplied alongside the APK. Rebuilding native dependency binaries uses the
upstream repositories and instructions above.
