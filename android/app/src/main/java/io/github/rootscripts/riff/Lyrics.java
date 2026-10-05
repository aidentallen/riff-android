package io.github.rootscripts.riff;

import android.content.Context;
import java.io.File;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONObject;

public final class Lyrics {
  public static final class Line {
    public final long time;
    public final String text;

    Line(long time, String text) {
      this.time = time;
      this.text = text;
    }
  }

  private static final Pattern TIMESTAMP =
      Pattern.compile("\\[(\\d+):(\\d{1,2})(?:[.:](\\d{1,3}))?]");

  public static List<Line> parse(String lrc) {
    List<Line> lines = new ArrayList<>();
    long offset = 0;
    Matcher adjustment =
        Pattern.compile("\\[offset:([+-]?\\d+)]", Pattern.CASE_INSENSITIVE).matcher(lrc);
    if (adjustment.find())
      try {
        offset = Long.parseLong(adjustment.group(1));
      } catch (NumberFormatException ignored) {
      }
    for (String line : lrc.split("\\r?\\n")) {
      Matcher m = TIMESTAMP.matcher(line);
      List<Long> times = new ArrayList<>();
      int end = 0;
      while (m.find()) {
        String fraction = m.group(3);
        long millis = fraction == null ? 0 : Integer.parseInt((fraction + "000").substring(0, 3));
        times.add(
            Long.parseLong(m.group(1)) * 60000
                + Long.parseLong(m.group(2)) * 1000
                + millis
                + offset);
        end = m.end();
      }
      String text = line.substring(end).trim();
      if (times.isEmpty() && !line.matches("\\[[a-zA-Z]+:.*]")) lines.add(new Line(-1, text));
      else for (Long time : times) lines.add(new Line(Math.max(0, time), text));
    }
    boolean synced = lines.stream().anyMatch(l -> l.time >= 0);
    if (synced) {
      lines.removeIf(l -> l.time < 0);
      lines.sort(Comparator.comparingLong(l -> l.time));
    }
    return lines;
  }

  public static int active(List<Line> lines, long position) {
    int result = -1;
    for (int i = 0; i < lines.size(); i++)
      if (lines.get(i).time >= 0 && lines.get(i).time <= position) result = i;
    return result;
  }

  private static File file(Context c, Track t) {
    File folder = new File(c.getFilesDir(), "lyrics");
    folder.mkdirs();
    return new File(folder, t.id.replaceAll("[^a-zA-Z0-9_-]", "_") + ".lrc");
  }

  public static void save(Context c, Track t, String text) throws Exception {
    Files.write(file(c, t).toPath(), text.getBytes(StandardCharsets.UTF_8));
  }

  public static String get(Context c, Track t, boolean online) throws Exception {
    File cache = file(c, t);
    if (cache.isFile())
      return new String(Files.readAllBytes(cache.toPath()), StandardCharsets.UTF_8);
    if (!online) return "";
    String url =
        "https://lrclib.net/api/get?track_name="
            + URLEncoder.encode(t.title, "UTF-8")
            + "&artist_name="
            + URLEncoder.encode(t.artist, "UTF-8");
    if (t.duration > 0) url += "&duration=" + (t.duration / 1000);
    HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
    conn.setConnectTimeout(15000);
    conn.setReadTimeout(15000);
    conn.setRequestProperty("User-Agent", "Riff Android/1.0 (https://github.com/rootscripts/riff)");
    try {
      if (conn.getResponseCode() == 404) return "";
      if (conn.getResponseCode() != 200)
        throw new IllegalStateException(
            "Lyrics service is unavailable. You can import an LRC file.");
      JSONObject j =
          new JSONObject(new String(Io.read(conn.getInputStream()), StandardCharsets.UTF_8));
      String text = j.optString("syncedLyrics");
      if (text.equals("null") || text.isBlank()) text = j.optString("plainLyrics");
      if (text.equals("null")) text = "";
      if (!text.isBlank()) save(c, t, text);
      return text;
    } finally {
      conn.disconnect();
    }
  }
}
