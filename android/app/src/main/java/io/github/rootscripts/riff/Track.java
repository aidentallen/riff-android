package io.github.rootscripts.riff;

import android.net.Uri;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import org.json.JSONObject;

public final class Track {
  public String id, title, artist, album, uri, source, artwork;
  public long duration;

  public Track(String id, String title, String artist, String uri) {
    this.id = id;
    this.title = title;
    this.artist = artist;
    this.uri = uri;
    album = "";
    source = "";
    artwork = "";
  }

  public boolean offline() {
    return uri.startsWith("content:") || uri.startsWith("file:");
  }

  public MediaItem mediaItem() {
    Uri playable =
        offline()
            ? Uri.parse(uri)
            : new Uri.Builder()
                .scheme("riff")
                .authority("stream")
                .appendQueryParameter("url", source.isEmpty() ? uri : source)
                .build();
    MediaMetadata.Builder meta =
        new MediaMetadata.Builder().setTitle(title).setArtist(artist).setAlbumTitle(album);
    if (!artwork.isEmpty()) meta.setArtworkUri(Uri.parse(artwork));
    return new MediaItem.Builder()
        .setMediaId(id)
        .setUri(playable)
        .setMediaMetadata(meta.build())
        .build();
  }

  public JSONObject json() {
    JSONObject j = new JSONObject();
    try {
      j.put("id", id)
          .put("title", title)
          .put("artist", artist)
          .put("album", album)
          .put("uri", uri)
          .put("source", source)
          .put("artwork", artwork)
          .put("duration", duration);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
    return j;
  }

  public static Track from(JSONObject j) {
    Track t =
        new Track(
            j.optString("id"),
            j.optString("title", "Untitled"),
            j.optString("artist", "Unknown artist"),
            j.optString("uri"));
    t.album = j.optString("album");
    t.source = j.optString("source");
    t.artwork = j.optString("artwork");
    t.duration = j.optLong("duration");
    return t;
  }
}
