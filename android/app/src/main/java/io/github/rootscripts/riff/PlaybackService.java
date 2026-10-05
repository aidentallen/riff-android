package io.github.rootscripts.riff;

import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.audiofx.BassBoost;
import android.media.audiofx.Equalizer;
import android.media.audiofx.PresetReverb;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;
import androidx.media3.session.SessionCommand;
import androidx.media3.session.SessionResult;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public final class PlaybackService extends MediaSessionService {
  public static final String EFFECTS = "riff.effects";
  private ExoPlayer player;
  private MediaSession session;
  private Equalizer eq;
  private BassBoost bass;
  private PresetReverb reverb;
  private final RiffAudioProcessor processing = new RiffAudioProcessor();
  private final android.os.Handler timer =
      new android.os.Handler(android.os.Looper.getMainLooper());
  private final Runnable checkpoint =
      new Runnable() {
        @Override
        public void run() {
          SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
          long sleep = prefs.getLong("sleepUntil", 0);
          if (sleep > 0 && System.currentTimeMillis() >= sleep) {
            player.pause();
            prefs.edit().putLong("sleepUntil", 0).apply();
          }
          if (player.getCurrentMediaItem() != null)
            prefs
                .edit()
                .putString("lastTrack", player.getCurrentMediaItem().mediaId)
                .putLong("lastPosition", player.getCurrentPosition())
                .apply();
          timer.postDelayed(this, 3000);
        }
      };

  @Override
  public void onCreate() {
    super.onCreate();
    DefaultDataSource.Factory upstream =
        new DefaultDataSource.Factory(
            this,
            new DefaultHttpDataSource.Factory()
                .setUserAgent("RiffAndroid/1.0")
                .setConnectTimeoutMs(20000)
                .setReadTimeoutMs(30000));
    player =
        new ExoPlayer.Builder(
                this,
                new androidx.media3.exoplayer.DefaultRenderersFactory(this) {
                  @Override
                  protected androidx.media3.exoplayer.audio.AudioSink buildAudioSink(
                      android.content.Context context,
                      boolean floatOutput,
                      boolean playbackParams) {
                    return new androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                        .setEnableFloatOutput(false)
                        .setEnableAudioTrackPlaybackParams(false)
                        .setAudioProcessors(
                            new androidx.media3.common.audio.AudioProcessor[] {processing})
                        .build();
                  }
                })
            .setMediaSourceFactory(
                new androidx.media3.exoplayer.source.MediaSource.Factory() {
                  private final DefaultMediaSourceFactory local =
                      new DefaultMediaSourceFactory(upstream);

                  @Override
                  public int[] getSupportedTypes() {
                    return local.getSupportedTypes();
                  }

                  @Override
                  public androidx.media3.exoplayer.source.MediaSource.Factory
                      setDrmSessionManagerProvider(
                          androidx.media3.exoplayer.drm.DrmSessionManagerProvider provider) {
                    local.setDrmSessionManagerProvider(provider);
                    return this;
                  }

                  @Override
                  public androidx.media3.exoplayer.source.MediaSource.Factory
                      setLoadErrorHandlingPolicy(
                          androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy policy) {
                    local.setLoadErrorHandlingPolicy(policy);
                    return this;
                  }

                  @Override
                  public androidx.media3.exoplayer.source.MediaSource createMediaSource(
                      androidx.media3.common.MediaItem item) {
                    if (item.localConfiguration != null
                        && "riff".equals(item.localConfiguration.uri.getScheme()))
                      return new OnlineMediaSource(PlaybackService.this, item);
                    return local.createMediaSource(item);
                  }
                })
            .build();
    player.setAudioAttributes(
        new AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build(),
        true);
    player.setHandleAudioBecomingNoisy(true);
    player.setWakeMode(C.WAKE_MODE_LOCAL);
    player.addListener(
        new Player.Listener() {
          @Override
          public void onAudioSessionIdChanged(int id) {
            releaseEffects();
            attachEffects(id);
          }

          @Override
          public void onPlayerError(PlaybackException error) {
            android.util.Log.e("Riff", "Playback error", error);
          }
        });
    PendingIntent activity =
        PendingIntent.getActivity(
            this,
            0,
            new Intent(this, MainActivity.class),
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    session =
        new MediaSession.Builder(this, player)
            .setSessionActivity(activity)
            .setCallback(
                new MediaSession.Callback() {
                  @Override
                  public MediaSession.ConnectionResult onConnect(
                      MediaSession s, MediaSession.ControllerInfo controller) {
                    MediaSession.ConnectionResult base =
                        MediaSession.Callback.super.onConnect(s, controller);
                    return new MediaSession.ConnectionResult.AcceptedResultBuilder(s)
                        .setAvailableSessionCommands(
                            base.availableSessionCommands
                                .buildUpon()
                                .add(new SessionCommand(EFFECTS, Bundle.EMPTY))
                                .build())
                        .build();
                  }

                  @Override
                  public ListenableFuture<SessionResult> onCustomCommand(
                      MediaSession s,
                      MediaSession.ControllerInfo controller,
                      SessionCommand command,
                      Bundle args) {
                    if (EFFECTS.equals(command.customAction)) {
                      applyEffects();
                      return Futures.immediateFuture(
                          new SessionResult(SessionResult.RESULT_SUCCESS));
                    }
                    return MediaSession.Callback.super.onCustomCommand(
                        s, controller, command, args);
                  }
                })
            .build();
    applyEffects();
    restoreQueue();
    timer.post(checkpoint);
  }

  private void restoreQueue() {
    SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
    try {
      org.json.JSONArray ids = new org.json.JSONArray(prefs.getString("queue", "[]"));
      java.util.List<androidx.media3.common.MediaItem> items = new java.util.ArrayList<>();
      int index = 0;
      for (int i = 0; i < ids.length(); i++) {
        Track track = Library.get(this).find(ids.getString(i));
        if (track == null) continue;
        if (track.id.equals(prefs.getString("lastTrack", ""))) index = items.size();
        items.add(track.mediaItem());
      }
      if (!items.isEmpty()) player.setMediaItems(items, index, prefs.getLong("lastPosition", 0));
    } catch (Exception e) {
      android.util.Log.w("Riff", "Could not restore queue", e);
    }
    player.addListener(
        new Player.Listener() {
          @Override
          public void onTimelineChanged(androidx.media3.common.Timeline timeline, int reason) {
            if (reason != Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) return;
            org.json.JSONArray ids = new org.json.JSONArray();
            for (int i = 0; i < player.getMediaItemCount(); i++)
              ids.put(player.getMediaItemAt(i).mediaId);
            prefs.edit().putString("queue", ids.toString()).apply();
          }
        });
  }

  private void attachEffects(int id) {
    if (id == C.AUDIO_SESSION_ID_UNSET) return;
    try {
      eq = new Equalizer(0, id);
    } catch (Exception ignored) {
    }
    try {
      bass = new BassBoost(0, id);
    } catch (Exception ignored) {
    }
    try {
      reverb = new PresetReverb(0, 0);
      player.setAuxEffectInfo(new androidx.media3.common.AuxEffectInfo(reverb.getId(), 0.4f));
    } catch (Exception ignored) {
    }
    applyEffects();
  }

  private void applyEffects() {
    SharedPreferences p = getSharedPreferences("settings", MODE_PRIVATE);
    processing.echo = p.getInt("echo", 0) / 1000f;
    processing.distortion = p.getInt("distortion", 0) / 1000f;
    player.setPlaybackParameters(
        new PlaybackParameters(p.getFloat("speed", 1f), p.getFloat("pitch", 1f)));
    try {
      if (eq != null) {
        boolean enabled = p.getBoolean("eq", false);
        eq.setEnabled(enabled);
        short[] range = eq.getBandLevelRange();
        for (short i = 0; i < eq.getNumberOfBands(); i++)
          eq.setBandLevel(
              i, (short) Math.max(range[0], Math.min(range[1], p.getInt("band" + i, 0))));
      }
      if (bass != null) {
        int value = p.getInt("bass", 0);
        bass.setStrength((short) value);
        bass.setEnabled(value > 0);
      }
      if (reverb != null) {
        short preset = (short) p.getInt("reverb", 0);
        reverb.setPreset(preset);
        reverb.setEnabled(preset != 0);
      }
    } catch (Exception e) {
      android.util.Log.w("Riff", "Audio effect is unavailable", e);
    }
  }

  private void releaseEffects() {
    if (eq != null) eq.release();
    if (bass != null) bass.release();
    if (reverb != null) reverb.release();
    eq = null;
    bass = null;
    reverb = null;
  }

  @Override
  public MediaSession onGetSession(@NonNull MediaSession.ControllerInfo controllerInfo) {
    return session;
  }

  @Override
  public void onTaskRemoved(Intent rootIntent) {
    if (!player.getPlayWhenReady()
        || player.getMediaItemCount() == 0
        || player.getPlaybackState() == Player.STATE_ENDED) stopSelf();
  }

  @Override
  public void onDestroy() {
    timer.removeCallbacksAndMessages(null);
    releaseEffects();
    if (session != null) session.release();
    if (player != null) player.release();
    super.onDestroy();
  }
}
