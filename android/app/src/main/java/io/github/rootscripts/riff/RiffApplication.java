package io.github.rootscripts.riff;

import android.app.Application;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class RiffApplication extends Application {
  public static final ExecutorService IO = Executors.newFixedThreadPool(3);

  @Override
  public void onCreate() {
    super.onCreate();
    Library.get(this);
  }
}
