package io.github.rootscripts.riff;

import android.content.Context;
import android.net.Uri;
import com.yausername.ffmpeg.FFmpeg;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

public final class Online {
  public static synchronized void init(Context c) throws Exception {
    YoutubeDL.getInstance().init(c.getApplicationContext());
    FFmpeg.getInstance().init(c.getApplicationContext());
  }

  private static YoutubeDLRequest request(String url) {
    return new YoutubeDLRequest(url)
        .addOption("--no-playlist")
        .addOption("--socket-timeout", 15)
        .addOption("--retries", 1)
        .addOption("--extractor-retries", 1)
        .addOption("--no-warnings");
  }

  public static List<Track> search(Context c, String query, boolean soundcloud, String processId)
      throws Exception {
    init(c);
    String target =
        validSource(query) ? query : (soundcloud ? "scsearch15:" : "ytsearch15:") + query;
    YoutubeDLRequest r =
        request(target)
            .addOption("--flat-playlist")
            .addOption("--dump-single-json")
            .addOption("--skip-download");
    JSONObject result = new JSONObject(YoutubeDL.getInstance().execute(r, processId).getOut());
    List<Track> tracks = new ArrayList<>();
    JSONArray entries = result.optJSONArray("entries");
    if (entries == null) tracks.add(parse(result));
    else
      for (int i = 0; i < entries.length(); i++)
        if (!entries.isNull(i)) tracks.add(parse(entries.getJSONObject(i)));
    return tracks;
  }

  public static boolean validSource(String value) {
    try {
      Uri u = Uri.parse(value);
      String host = u.getHost();
      return "https".equals(u.getScheme())
          && host != null
          && (host.equals("youtu.be")
              || host.equals("youtube.com")
              || host.endsWith(".youtube.com")
              || host.equals("soundcloud.com")
              || host.endsWith(".soundcloud.com"));
    } catch (Exception e) {
      return false;
    }
  }

  private static Track parse(JSONObject o) {
    String source = o.optString("webpage_url", o.optString("url"));
    String id = o.optString("id");
    if (!source.startsWith("https://")) source = "https://www.youtube.com/watch?v=" + id;
    String platform = source.contains("soundcloud.com") ? "sc" : "yt";
    Track t =
        new Track(
            platform + "_" + id,
            o.optString("track", o.optString("title", "Untitled")),
            o.optString(
                "artist", o.optString("uploader", o.optString("channel", "Unknown artist"))),
            source);
    t.source = source;
    t.duration = (long) (o.optDouble("duration", 0) * 1000);
    t.album = o.optString("album");
    t.artwork = o.optString("thumbnail");
    JSONArray thumbs = o.optJSONArray("thumbnails");
    if (t.artwork.isEmpty() && thumbs != null && thumbs.length() > 0)
      t.artwork = thumbs.optJSONObject(thumbs.length() - 1).optString("url");
    if (t.artwork.isEmpty() && platform.equals("yt"))
      t.artwork = "https://i.ytimg.com/vi/" + id + "/hqdefault.jpg";
    return t;
  }

  public static final class Stream {
    public String url;
    public boolean hls;
    public Map<String, String> headers = new HashMap<>();
    public long time = System.currentTimeMillis();
  }

  private static final Map<String, Stream> cache = new HashMap<>();

  public static synchronized Stream resolve(Context c, String source) throws Exception {
    Stream cached = cache.get(source);
    if (cached != null && System.currentTimeMillis() - cached.time < 20 * 60 * 1000) return cached;
    init(c);
    YoutubeDLRequest r =
        request(source)
            .addOption("--dump-single-json")
            .addOption("--skip-download")
            .addOption(
                "-f",
                "bestaudio[protocol=https][ext=m4a]/bestaudio[protocol=https]/bestaudio/best");
    JSONObject j = new JSONObject(YoutubeDL.getInstance().execute(r).getOut());
    Stream s = new Stream();
    s.url = j.optString("url");
    s.hls = j.optString("protocol").contains("m3u8") || s.url.contains(".m3u8");
    if (!s.url.startsWith("https://"))
      throw new IllegalStateException(
          "This source did not return a secure audio stream. Try downloading it instead.");
    JSONObject h = j.optJSONObject("http_headers");
    if (h != null) {
      java.util.Iterator<String> it = h.keys();
      while (it.hasNext()) {
        String key = it.next();
        s.headers.put(key, h.optString(key));
      }
    }
    cache.put(source, s);
    return s;
  }

  public static void download(
      Context c,
      Track t,
      File destination,
      String format,
      String quality,
      String processId,
      kotlin.jvm.functions.Function3<Float, Long, String, kotlin.Unit> callback)
      throws Exception {
    init(c);
    String source = t.source.isEmpty() ? t.uri : t.source;
    if (!validSource(source))
      throw new IllegalArgumentException("Use a YouTube or SoundCloud link.");
    YoutubeDLRequest r =
        request(source)
            .addOption("-f", "bestaudio/best")
            .addOption("-x")
            .addOption("--audio-format", format)
            .addOption("--audio-quality", quality)
            .addOption("--no-mtime")
            .addOption("--newline")
            .addOption("--add-metadata")
            .addOption(
                "-o",
                destination
                        .getAbsolutePath()
                        .substring(0, destination.getAbsolutePath().lastIndexOf('.'))
                    + ".%(ext)s");
    YoutubeDL.getInstance().execute(r, processId, callback);
    if (!destination.isFile() || destination.length() == 0)
      throw new IllegalStateException("The audio download was incomplete.");
  }
}
