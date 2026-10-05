package io.github.rootscripts.riff;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.util.LruCache;
import android.widget.ImageView;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public final class Artwork {
  public static void keepOffline(android.content.Context context, Track track) {
    if (!track.artwork.startsWith("https://")) return;
    HttpURLConnection connection = null;
    try {
      connection = (HttpURLConnection) new URL(track.artwork).openConnection();
      connection.setConnectTimeout(8000);
      connection.setReadTimeout(8000);
      java.io.File folder = new java.io.File(context.getFilesDir(), "artwork");
      folder.mkdirs();
      java.io.File file =
          new java.io.File(folder, track.id.replaceAll("[^a-zA-Z0-9_-]", "_") + ".jpg");
      try (InputStream in = connection.getInputStream();
          java.io.FileOutputStream out = new java.io.FileOutputStream(file)) {
        Io.copy(in, out);
      }
      track.artwork = Uri.fromFile(file).toString();
    } catch (Exception ignored) {
    } finally {
      if (connection != null) connection.disconnect();
    }
  }

  private static final LruCache<String, Bitmap> cache =
      new LruCache<>(12 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap bitmap) {
          return bitmap.getByteCount();
        }
      };

  public static void load(ImageView view, Track track) {
    String key = track.artwork;
    view.setTag(key);
    view.setImageResource(R.mipmap.ic_launcher);
    view.setScaleType(ImageView.ScaleType.FIT_CENTER);
    view.setPadding(10, 10, 10, 10);
    view.setBackgroundColor(Color.rgb(65 + Math.abs(track.id.hashCode() % 40), 49, 92));
    if (key.isEmpty()) return;
    Bitmap bitmap = cache.get(key);
    if (bitmap != null) {
      set(view, bitmap);
      return;
    }
    RiffApplication.IO.execute(
        () -> {
          HttpURLConnection conn = null;
          try {
            InputStream in;
            if (key.startsWith("https://")) {
              conn = (HttpURLConnection) new URL(key).openConnection();
              conn.setConnectTimeout(8000);
              conn.setReadTimeout(8000);
              in = conn.getInputStream();
            } else if (key.startsWith("file:") || key.startsWith("content:"))
              in = view.getContext().getContentResolver().openInputStream(Uri.parse(key));
            else return;
            if (in == null) return;
            Bitmap image;
            try (InputStream stream = in) {
              BitmapFactory.Options opt = new BitmapFactory.Options();
              opt.inSampleSize = 2;
              image = BitmapFactory.decodeStream(stream, null, opt);
            }
            if (image != null) {
              cache.put(key, image);
              view.post(
                  () -> {
                    if (key.equals(view.getTag())) set(view, image);
                  });
            }
          } catch (Exception ignored) {
          } finally {
            if (conn != null) conn.disconnect();
          }
        });
  }

  private static void set(ImageView view, Bitmap image) {
    view.setPadding(0, 0, 0, 0);
    view.setScaleType(ImageView.ScaleType.CENTER_CROP);
    view.setImageBitmap(image);
  }
}
