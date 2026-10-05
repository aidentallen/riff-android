package io.github.rootscripts.riff;

import android.content.Context;
import android.net.Uri;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public final class Editor {
  public static Track trim(Context c, Track t, double start, double end) throws Exception {
    if (!t.offline()) throw new IllegalArgumentException("Download the track before editing it.");
    if (start < 0 || end <= start || (t.duration > 0 && end > t.duration / 1000.0 + 0.5))
      throw new IllegalArgumentException("Choose a valid start and end time.");
    Online.init(c);
    File input = new File(Uri.parse(t.uri).getPath());
    if (!input.isFile()) throw new IllegalArgumentException("Import the track into Riff first.");
    String id = "edit_" + UUID.randomUUID();
    File output = new File(new File(c.getFilesDir(), "music"), id + ".mp3");
    File executable = new File(c.getApplicationInfo().nativeLibraryDir, "libffmpeg.so");
    List<String> args =
        new ArrayList<>(
            List.of(
                executable.getAbsolutePath(),
                "-nostdin",
                "-y",
                "-ss",
                String.format(Locale.US, "%.3f", start),
                "-i",
                input.getAbsolutePath(),
                "-t",
                String.format(Locale.US, "%.3f", end - start),
                "-vn",
                "-codec:a",
                "libmp3lame",
                "-q:a",
                "2",
                "-metadata",
                "title=" + t.title + " (edit)",
                "-metadata",
                "artist=" + t.artist,
                output.getAbsolutePath()));
    ProcessBuilder builder = new ProcessBuilder(args).redirectErrorStream(true);
    File ffmpegDir = new File(c.getNoBackupFilesDir(), "youtubedl-android/packages/ffmpeg/usr/lib");
    File pythonDir = new File(c.getNoBackupFilesDir(), "youtubedl-android/packages/python/usr/lib");
    builder
        .environment()
        .put(
            "LD_LIBRARY_PATH",
            c.getApplicationInfo().nativeLibraryDir
                + ":"
                + pythonDir.getAbsolutePath()
                + ":"
                + ffmpegDir.getAbsolutePath());
    builder.environment().put("TMPDIR", c.getCacheDir().getAbsolutePath());
    Process process = builder.start();
    Thread killer =
        new Thread(
            () -> {
              try {
                if (!process.waitFor(3, TimeUnit.MINUTES)) process.destroy();
              } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
              }
            });
    killer.start();
    String log;
    try (InputStream in = process.getInputStream()) {
      log = new String(Io.read(in), StandardCharsets.UTF_8);
    }
    int code = process.waitFor();
    killer.interrupt();
    if (code != 0 || !output.isFile() || output.length() == 0) {
      output.delete();
      throw new IllegalStateException(
          "Could not export this selection: " + log.substring(Math.max(0, log.length() - 200)));
    }
    Track edited = new Track(id, t.title + " (edit)", t.artist, Uri.fromFile(output).toString());
    edited.album = t.album;
    edited.artwork = t.artwork;
    edited.duration = (long) ((end - start) * 1000);
    Library.get(c).put(edited);
    return edited;
  }
}
