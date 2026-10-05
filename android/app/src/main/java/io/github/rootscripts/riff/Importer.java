package io.github.rootscripts.riff;

import android.content.Context;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class Importer {
  public static Track importAudio(Context c, Uri uri) throws Exception {
    String id = "local_" + UUID.nameUUIDFromBytes(uri.toString().getBytes(StandardCharsets.UTF_8));
    Track existing = Library.get(c).find(id);
    if (existing != null && existing.offline()) return existing;
    String name = "Music";
    try (Cursor cursor =
        c.getContentResolver()
            .query(uri, new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) name = cursor.getString(0);
    }
    String ext =
        name.contains(".")
            ? name.substring(name.lastIndexOf('.')).toLowerCase(java.util.Locale.ROOT)
            : ".audio";
    if (!ext.matches("\\.[a-z0-9]{1,8}")) ext = ".audio";
    File folder = new File(c.getFilesDir(), "music");
    if (!folder.exists() && !folder.mkdirs())
      throw new IllegalStateException("Could not open your music folder");
    File output = new File(folder, id + ext);
    File partial = new File(folder, id + ".importing");
    try (InputStream in = c.getContentResolver().openInputStream(uri);
        FileOutputStream out = new FileOutputStream(partial)) {
      if (in == null) throw new IllegalStateException("Could not read " + name);
      Io.copy(in, out);
    } catch (Exception e) {
      partial.delete();
      throw e;
    }
    if (!partial.renameTo(output)) {
      partial.delete();
      throw new IllegalStateException("Could not save " + name);
    }
    Track t =
        new Track(
            id,
            name.replaceFirst("\\.[^.]+$", ""),
            "Unknown artist",
            Uri.fromFile(output).toString());
    MediaMetadataRetriever meta = new MediaMetadataRetriever();
    try {
      meta.setDataSource(output.getAbsolutePath());
      String value = meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
      if (value != null && !value.isBlank()) t.title = value;
      value = meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
      if (value != null && !value.isBlank()) t.artist = value;
      value = meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
      if (value != null) t.album = value;
      value = meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
      if (value != null) t.duration = Long.parseLong(value);
      byte[] art = meta.getEmbeddedPicture();
      if (art != null) {
        File arts = new File(c.getFilesDir(), "artwork");
        arts.mkdirs();
        File cover = new File(arts, id + ".jpg");
        try (FileOutputStream out = new FileOutputStream(cover)) {
          out.write(art);
        }
        t.artwork = Uri.fromFile(cover).toString();
      }
    } catch (Exception e) {
      android.util.Log.w("Riff", "No readable audio tags", e);
    } finally {
      meta.release();
    }
    Library.get(c).put(t);
    return t;
  }
}
