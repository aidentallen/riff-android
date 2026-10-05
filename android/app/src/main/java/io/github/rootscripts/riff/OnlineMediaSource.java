package io.github.rootscripts.riff;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.Timeline;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.TransferListener;
import androidx.media3.exoplayer.source.BaseMediaSource;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.source.MediaPeriod;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.upstream.Allocator;
import java.io.IOException;

/** Resolves each online item asynchronously before choosing progressive or HLS playback. */
@androidx.media3.common.util.UnstableApi
final class OnlineMediaSource extends BaseMediaSource implements MediaSource.MediaSourceCaller {
  private final Context context;
  private final MediaItem original;
  private MediaSource delegate;
  private IOException error;
  private int generation;

  OnlineMediaSource(Context c, MediaItem item) {
    context = c.getApplicationContext();
    original = item;
  }

  @Override
  public MediaItem getMediaItem() {
    return original;
  }

  @Override
  protected void prepareSourceInternal(TransferListener listener) {
    Handler playback = new Handler(Looper.myLooper());
    int request = ++generation;
    RiffApplication.IO.execute(
        () -> {
          try {
            Track track = Library.get(context).find(original.mediaId);
            MediaItem resolved;
            DefaultHttpDataSource.Factory http =
                new DefaultHttpDataSource.Factory()
                    .setUserAgent("RiffAndroid/1.0")
                    .setConnectTimeoutMs(20000)
                    .setReadTimeoutMs(30000);
            if (track != null && track.offline()) resolved = track.mediaItem();
            else {
              Online.Stream stream =
                  Online.resolve(context, original.localConfiguration.uri.getQueryParameter("url"));
              http.setDefaultRequestProperties(stream.headers);
              MediaItem.Builder builder = original.buildUpon().setUri(Uri.parse(stream.url));
              if (stream.hls) builder.setMimeType(MimeTypes.APPLICATION_M3U8);
              resolved = builder.build();
            }
            MediaItem item = resolved;
            playback.post(
                () -> {
                  if (request != generation) return;
                  delegate =
                      new DefaultMediaSourceFactory(new DefaultDataSource.Factory(context, http))
                          .createMediaSource(item);
                  delegate.prepareSource(this, listener, getPlayerId());
                });
          } catch (Exception e) {
            playback.post(
                () -> {
                  if (request == generation) {
                    error = new IOException("Could not resolve this audio: " + e.getMessage(), e);
                  }
                });
          }
        });
  }

  @Override
  public void maybeThrowSourceInfoRefreshError() throws IOException {
    if (error != null) throw error;
    if (delegate != null) delegate.maybeThrowSourceInfoRefreshError();
  }

  @Override
  public void onSourceInfoRefreshed(MediaSource source, Timeline timeline) {
    refreshSourceInfo(timeline);
  }

  @Override
  public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator, long positionUs) {
    return delegate.createPeriod(id, allocator, positionUs);
  }

  @Override
  public void releasePeriod(MediaPeriod period) {
    if (delegate != null) delegate.releasePeriod(period);
  }

  @Override
  protected void releaseSourceInternal() {
    generation++;
    if (delegate != null) delegate.releaseSource(this);
    delegate = null;
    error = null;
  }
}
