package io.github.rootscripts.riff;

import android.content.Context;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

public final class Library {
  private static Library instance;
  private final AtomicFile file;
  private final Map<String, Track> tracks = new LinkedHashMap<>();
  private final Set<String> liked = new LinkedHashSet<>();
  private final Map<String, Playlist> playlists = new LinkedHashMap<>();

  public static final class Playlist {
    public String id, name;
    public final List<String> ids = new ArrayList<>();
  }

  public static synchronized Library get(Context context) {
    if (instance == null) instance = new Library(context.getApplicationContext());
    return instance;
  }

  private Library(Context context) {
    file = new AtomicFile(new File(context.getFilesDir(), "library.json"));
    if (!file.getBaseFile().exists()) return;
    try {
      JSONObject j = new JSONObject(new String(file.readFully(), StandardCharsets.UTF_8));
      JSONArray a = j.optJSONArray("tracks");
      if (a != null)
        for (int i = 0; i < a.length(); i++) {
          Track t = Track.from(a.getJSONObject(i));
          tracks.put(t.id, t);
        }
      a = j.optJSONArray("liked");
      if (a != null) for (int i = 0; i < a.length(); i++) liked.add(a.getString(i));
      a = j.optJSONArray("playlists");
      if (a != null)
        for (int i = 0; i < a.length(); i++) {
          JSONObject o = a.getJSONObject(i);
          Playlist p = new Playlist();
          p.id = o.getString("id");
          p.name = o.getString("name");
          JSONArray ids = o.getJSONArray("ids");
          for (int k = 0; k < ids.length(); k++)
            if (tracks.containsKey(ids.getString(k))) p.ids.add(ids.getString(k));
          playlists.put(p.id, p);
        }
    } catch (Exception e) {
      android.util.Log.e("Riff", "Could not read library", e);
    }
  }

  private void save() {
    FileOutputStream out = null;
    try {
      JSONObject j = new JSONObject();
      JSONArray a = new JSONArray();
      for (Track t : tracks.values()) a.put(t.json());
      j.put("tracks", a).put("liked", new JSONArray(liked));
      a = new JSONArray();
      for (Playlist p : playlists.values())
        a.put(
            new JSONObject().put("id", p.id).put("name", p.name).put("ids", new JSONArray(p.ids)));
      j.put("playlists", a);
      out = file.startWrite();
      out.write(j.toString().getBytes(StandardCharsets.UTF_8));
      file.finishWrite(out);
    } catch (Exception e) {
      if (out != null) file.failWrite(out);
      throw new IllegalStateException("Could not save your library", e);
    }
  }

  public synchronized void put(Track track) {
    tracks.put(track.id, track);
    save();
  }

  public synchronized Track find(String id) {
    return tracks.get(id);
  }

  public synchronized List<Track> all() {
    return new ArrayList<>(tracks.values());
  }

  public synchronized List<Track> offline() {
    List<Track> result = new ArrayList<>();
    for (Track t : tracks.values()) if (t.offline()) result.add(t);
    return result;
  }

  public synchronized boolean liked(String id) {
    return liked.contains(id);
  }

  public synchronized boolean toggleLike(Track t) {
    tracks.putIfAbsent(t.id, t);
    boolean add = !liked.remove(t.id);
    if (add) liked.add(t.id);
    save();
    return add;
  }

  public synchronized List<Track> favorites() {
    List<Track> result = new ArrayList<>();
    for (String id : liked) if (tracks.containsKey(id)) result.add(tracks.get(id));
    return result;
  }

  public synchronized Playlist createPlaylist(String name) {
    Playlist p = new Playlist();
    p.id = UUID.randomUUID().toString();
    p.name = name;
    playlists.put(p.id, p);
    save();
    return p;
  }

  public synchronized List<Playlist> playlists() {
    return new ArrayList<>(playlists.values());
  }

  public synchronized void add(Playlist p, Track t) {
    tracks.putIfAbsent(t.id, t);
    if (!p.ids.contains(t.id)) p.ids.add(t.id);
    save();
  }

  public synchronized void remove(Playlist p, String id) {
    p.ids.remove(id);
    save();
  }

  public synchronized void rename(Playlist p, String name) {
    p.name = name;
    save();
  }

  public synchronized void delete(Playlist p) {
    playlists.remove(p.id);
    save();
  }

  public synchronized List<Track> contents(Playlist p) {
    List<Track> result = new ArrayList<>();
    for (String id : p.ids) if (tracks.containsKey(id)) result.add(tracks.get(id));
    return result;
  }

  public synchronized void forget(String id) {
    tracks.remove(id);
    liked.remove(id);
    for (Playlist p : playlists.values()) p.ids.remove(id);
    save();
  }
}
