package io.github.rootscripts.riff;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import com.yausername.youtubedl_android.YoutubeDL;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONObject;

public final class DownloadService extends Service {
  public static final String EVENT = "io.github.rootscripts.riff.DOWNLOAD";
  public static final String CANCEL = "cancel";
  private final ExecutorService worker = Executors.newSingleThreadExecutor();
  private final AtomicInteger pending = new AtomicInteger();
  private final Handler main = new Handler(Looper.getMainLooper());
  private volatile String activeId;
  private volatile long notified;

  @Override
  public void onCreate() {
    super.onCreate();
    getSystemService(NotificationManager.class)
        .createNotificationChannel(
            new NotificationChannel(
                "downloads", "Music downloads", NotificationManager.IMPORTANCE_LOW));
  }

  private Notification notification(String title, String text, int progress, boolean cancel) {
    PendingIntent open =
        PendingIntent.getActivity(
            this,
            0,
            new Intent(this, MainActivity.class),
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    Notification.Builder b =
        new Notification.Builder(this, "downloads")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setOngoing(true);
    if (progress < 100) b.setProgress(100, Math.max(0, progress), progress < 0);
    if (cancel)
      b.addAction(
          new Notification.Action.Builder(
                  null,
                  "Cancel",
                  PendingIntent.getService(
                      this,
                      1,
                      new Intent(this, DownloadService.class).setAction(CANCEL),
                      PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT))
              .build());
    return b.build();
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    if (intent == null) {
      stopSelf();
      return START_NOT_STICKY;
    }
    if (CANCEL.equals(intent.getAction())) {
      if (activeId != null) YoutubeDL.getInstance().destroyProcessById(activeId);
      return START_NOT_STICKY;
    }
    Notification n = notification("Riff", "Preparing download…", -1, true);
    if (Build.VERSION.SDK_INT >= 29)
      startForeground(2, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
    else startForeground(2, n);
    pending.incrementAndGet();
    String json = intent.getStringExtra("track");
    String requestedFormat = intent.getStringExtra("format"),
        requestedQuality = intent.getStringExtra("quality");
    final String format =
        java.util.List.of("mp3", "m4a", "flac", "opus", "wav")
                .contains(requestedFormat == null ? "" : requestedFormat)
            ? requestedFormat
            : "mp3";
    final String quality =
        java.util.List.of("0", "128K", "192K", "320K")
                .contains(requestedQuality == null ? "" : requestedQuality)
            ? requestedQuality
            : "0";
    worker.execute(
        () -> {
          File output = null;
          try {
            Track t = Track.from(new JSONObject(json));
            Track existing = Library.get(this).find(t.id);
            if (existing != null && existing.offline()) {
              event("Already available offline", false, true);
              return;
            }
            File folder = new File(getFilesDir(), "music");
            if (!folder.exists() && !folder.mkdirs())
              throw new IllegalStateException("Could not create the download folder");
            output = new File(folder, t.id.replaceAll("[^a-zA-Z0-9_-]", "_") + "." + format);
            activeId = "download-" + t.id;
            Online.download(
                this,
                t,
                output,
                format,
                quality,
                activeId,
                (progress, eta, line) -> {
                  if (System.currentTimeMillis() - notified > 700) {
                    notified = System.currentTimeMillis();
                    getSystemService(NotificationManager.class)
                        .notify(
                            2,
                            notification(
                                t.title,
                                "Downloading • " + progress.intValue() + "%",
                                progress.intValue(),
                                true));
                    event(t.title + " • " + progress.intValue() + "%", false, false);
                  }
                  return kotlin.Unit.INSTANCE;
                });
            t.uri = Uri.fromFile(output).toString();
            Artwork.keepOffline(this, t);
            Library.get(this).put(t);
            event("Saved offline: " + t.title, false, true);
          } catch (Exception e) {
            if (output != null) cleanup(output);
            event(
                e instanceof YoutubeDL.CanceledException
                    ? "Download cancelled"
                    : "Download failed: " + friendly(e),
                true,
                true);
          } finally {
            activeId = null;
            if (pending.decrementAndGet() == 0)
              main.post(
                  () -> {
                    if (pending.get() == 0) {
                      stopForeground(STOP_FOREGROUND_REMOVE);
                      stopSelf();
                    }
                  });
          }
        });
    return START_NOT_STICKY;
  }

  private void cleanup(File output) {
    File[] files = output.getParentFile().listFiles();
    if (files != null)
      for (File f : files)
        if (f.getName()
            .startsWith(output.getName().substring(0, output.getName().lastIndexOf('.') + 1)))
          f.delete();
  }

  public static String friendly(Exception e) {
    String m = e.getMessage();
    if (m == null || m.isBlank()) return "Please try again.";
    return m.length() > 350 ? m.substring(m.length() - 350) : m;
  }

  private void event(String message, boolean error, boolean done) {
    sendBroadcast(
        new Intent(EVENT)
            .setPackage(getPackageName())
            .putExtra("message", message)
            .putExtra("error", error)
            .putExtra("done", done));
  }

  @Override
  public void onTimeout(int startId, int fgsType) {
    if (activeId != null) YoutubeDL.getInstance().destroyProcessById(activeId);
    stopForeground(STOP_FOREGROUND_REMOVE);
    stopSelf();
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  @Override
  public void onDestroy() {
    if (activeId != null) YoutubeDL.getInstance().destroyProcessById(activeId);
    worker.shutdownNow();
    super.onDestroy();
  }
}
