package io.github.rootscripts.riff;

import static org.junit.Assert.*;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.SystemClock;
import androidx.core.content.FileProvider;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AppIntegrationTest {
  private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();

  private static void main(Runnable code) {
    InstrumentationRegistry.getInstrumentation().runOnMainSync(code);
  }

  private Track fixture() throws Exception {
    File folder = new File(context.getCacheDir(), "imports");
    folder.mkdirs();
    File file = new File(folder, "First Light.wav");
    int rate = 16000, frames = rate * 60, bytes = frames * 2;
    ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
    header
        .put("RIFF".getBytes())
        .putInt(36 + bytes)
        .put("WAVEfmt ".getBytes())
        .putInt(16)
        .putShort((short) 1)
        .putShort((short) 1)
        .putInt(rate)
        .putInt(rate * 2)
        .putShort((short) 2)
        .putShort((short) 16)
        .put("data".getBytes())
        .putInt(bytes);
    try (FileOutputStream out = new FileOutputStream(file)) {
      out.write(header.array());
      ByteBuffer pcm = ByteBuffer.allocate(bytes).order(ByteOrder.LITTLE_ENDIAN);
      for (int i = 0; i < frames; i++)
        pcm.putShort((short) (Math.sin(i * 2 * Math.PI * 220 / rate) * 1600));
      out.write(pcm.array());
    }
    Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".files", file);
    Track track = Importer.importAudio(context, uri);
    track.title = "First Light";
    track.artist = "Riff test tone";
    track.album = "Offline sessions";
    Library.get(context).put(track);
    return track;
  }

  @Test
  public void importsAudioAndPersistsPlaylistAndLyrics() throws Exception {
    Track track = fixture();
    Library library = Library.get(context);
    assertTrue(track.offline());
    assertTrue(track.duration >= 59000);
    assertEquals(
        track.id,
        Importer.importAudio(
                context,
                FileProvider.getUriForFile(
                    context,
                    context.getPackageName() + ".files",
                    new File(context.getCacheDir(), "imports/First Light.wav")))
            .id);
    Library.Playlist playlist = library.createPlaylist("Offline sessions");
    library.add(playlist, track);
    library.add(playlist, track);
    assertEquals(1, library.contents(playlist).size());
    if (!library.liked(track.id)) library.toggleLike(track);
    assertTrue(library.favorites().stream().anyMatch(t -> t.id.equals(track.id)));
    Lyrics.save(context, track, "[00:01]One\n[00:03]Two");
    assertEquals("[00:01]One\n[00:03]Two", Lyrics.get(context, track, false));
    assertTrue(new File(context.getFilesDir(), "library.json").length() > 0);
    library.remove(playlist, track.id);
    assertTrue(library.contents(playlist).isEmpty());
    library.delete(playlist);
    playlist = library.createPlaylist("Offline sessions");
    library.add(playlist, track);
  }

  @Test
  public void nativeEnginesExecuteAndTrimOfflineAudio() throws Exception {
    Track track = fixture();
    Online.init(context);
    String version =
        YoutubeDL.getInstance()
            .execute(new YoutubeDLRequest(List.<String>of()).addOption("--version"))
            .getOut();
    assertFalse(version.trim().isEmpty());
    Track edited = Editor.trim(context, track, 1, 3);
    assertTrue(edited.offline());
    assertEquals(2000, edited.duration);
    assertTrue(new File(Uri.parse(edited.uri).getPath()).length() > 1000);
    assertNotNull(Library.get(context).find(edited.id));
  }

  @Test
  public void playsSeeksAndContinuesOutsideActivity() throws Exception {
    Track track = fixture();
    AtomicReference<com.google.common.util.concurrent.ListenableFuture<MediaController>> future =
        new AtomicReference<>();
    main(
        () ->
            future.set(
                new MediaController.Builder(
                        context,
                        new SessionToken(
                            context, new ComponentName(context, PlaybackService.class)))
                    .buildAsync()));
    MediaController controller = future.get().get(60, TimeUnit.SECONDS);
    AtomicReference<PlaybackException> error = new AtomicReference<>();
    try {
      main(
          () -> {
            controller.addListener(
                new Player.Listener() {
                  @Override
                  public void onPlayerError(PlaybackException e) {
                    error.set(e);
                  }
                });
            controller.setMediaItems(List.of(track.mediaItem(), track.mediaItem()));
            controller.prepare();
            controller.play();
          });
      boolean started = false;
      for (int i = 0; i < 60; i++) {
        AtomicReference<Boolean> playing = new AtomicReference<>();
        main(() -> playing.set(controller.isPlaying()));
        if (playing.get()) {
          started = true;
          break;
        }
        if (error.get() != null) break;
        SystemClock.sleep(500);
      }
      assertNull(error.get());
      assertTrue("Native player did not start", started);
      main(() -> controller.seekTo(10000));
      SystemClock.sleep(1500);
      AtomicReference<Long> position = new AtomicReference<>();
      main(() -> position.set(controller.getCurrentPosition()));
      assertTrue(position.get() >= 10000);
      main(
          () ->
              context.startActivity(
                  new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
      SystemClock.sleep(2000);
      InstrumentationRegistry.getInstrumentation()
          .getUiAutomation()
          .performGlobalAction(
              android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME);
      SystemClock.sleep(2000);
      AtomicReference<Boolean> background = new AtomicReference<>();
      main(() -> background.set(controller.isPlaying()));
      assertTrue("Playback stopped in background", background.get());
      main(
          () -> {
            controller.pause();
            controller.seekTo(0);
          });
    } finally {
      main(controller::release);
    }
  }
}
