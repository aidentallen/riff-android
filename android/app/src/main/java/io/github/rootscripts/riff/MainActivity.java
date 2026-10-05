package io.github.rootscripts.riff;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionCommand;
import androidx.media3.session.SessionToken;
import com.google.common.util.concurrent.ListenableFuture;
import com.yausername.youtubedl_android.YoutubeDL;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class MainActivity extends Activity {
  private static final int BG = 0xff121214,
      SURFACE = 0xff1e1f22,
      HIGH = 0xff2a292f,
      TEXT = 0xffeeedf1,
      MUTED = 0xffcac4d0;
  private int accent = 0xffd0bcff;
  private final Handler handler = new Handler(Looper.getMainLooper());
  private Library library;
  private SharedPreferences settings;
  MediaController controller;
  private ListenableFuture<MediaController> controllerFuture;
  private LinearLayout root, mini, nav;
  private FrameLayout body;
  private TextView miniTitle, miniArtist, downloadStatus;
  private Icon miniPlay;
  private ImageView miniArt;
  private int tab = 0, homeFilter = 0, searchPlatform = 0;
  private String query = "", searchMessage = "Search for a song, artist, or paste a link.";
  private List<Track> results = new ArrayList<>(), visibleTracks = new ArrayList<>();
  private final AtomicInteger searchGeneration = new AtomicInteger();
  private volatile String searchProcess;
  private boolean searching, receiverRegistered, visible;
  private Library.Playlist selectedPlaylist;
  private String selectedAlbum;
  private Track exportTrack, lrcTrack;
  private Dialog playerDialog, lyricsDialog;
  private SeekBar playerSeek;
  private TextView playerTitle, playerArtist, playerPosition, playerDuration, repeatLabel;
  private ImageView playerArt;
  private Icon playerPlay, playerLike, playerShuffle, playerRepeat;
  private String renderedTrack = "";
  private List<Lyrics.Line> lyricLines = new ArrayList<>();
  private List<TextView> lyricViews = new ArrayList<>();
  private ScrollView lyricsScroll;
  private int lastLyric = -1;
  private boolean seeking;
  private final BroadcastReceiver downloads =
      new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
          String message = intent.getStringExtra("message");
          if (downloadStatus != null) downloadStatus.setText(message);
          if (intent.getBooleanExtra("done", false)) {
            if (intent.getBooleanExtra("error", false)) error("Download", message);
            else toast(message);
            if (tab == 0 || tab == 2) renderPage();
          }
        }
      };
  private final Runnable ticker =
      new Runnable() {
        @Override
        public void run() {
          if (visible) {
            updatePlayback();
            handler.postDelayed(this, 500);
          }
        }
      };

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    library = Library.get(this);
    settings = getSharedPreferences("settings", MODE_PRIVATE);
    accent = settings.getInt("accent", accent);
    if (state != null) {
      tab = state.getInt("tab");
      homeFilter = state.getInt("filter");
      query = state.getString("query", "");
      searchPlatform = state.getInt("platform");
    }
    setup();
    SessionToken token = new SessionToken(this, new ComponentName(this, PlaybackService.class));
    controllerFuture = new MediaController.Builder(this, token).buildAsync();
    controllerFuture.addListener(
        () -> {
          try {
            controller = controllerFuture.get();
            controller.addListener(
                new Player.Listener() {
                  @Override
                  public void onEvents(Player player, Player.Events events) {
                    updatePlayback();
                  }

                  @Override
                  public void onPlayerError(PlaybackException e) {
                    error(
                        "Couldn't play this track",
                        "Check your connection or download the track for offline listening.\n\n"
                            + e.getErrorCodeName());
                  }
                });
            updatePlayback();
            handleIntent(getIntent());
          } catch (Exception e) {
            error("Player unavailable", DownloadService.friendly(e));
          }
        },
        this::runOnUiThread);
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
    if (controller != null) handleIntent(intent);
  }

  @Override
  protected void onSaveInstanceState(Bundle state) {
    state.putInt("tab", tab);
    state.putInt("filter", homeFilter);
    state.putInt("platform", searchPlatform);
    state.putString("query", query);
    super.onSaveInstanceState(state);
  }

  @Override
  protected void onStart() {
    super.onStart();
    visible = true;
    IntentFilter filter = new IntentFilter(DownloadService.EVENT);
    androidx.core.content.ContextCompat.registerReceiver(
        this, downloads, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED);
    receiverRegistered = true;
    handler.post(ticker);
  }

  @Override
  protected void onStop() {
    visible = false;
    handler.removeCallbacks(ticker);
    if (receiverRegistered) {
      unregisterReceiver(downloads);
      receiverRegistered = false;
    }
    super.onStop();
  }

  @Override
  protected void onDestroy() {
    if (playerDialog != null) playerDialog.dismiss();
    if (lyricsDialog != null) lyricsDialog.dismiss();
    if (searchProcess != null) YoutubeDL.getInstance().destroyProcessById(searchProcess);
    MediaController.releaseFuture(controllerFuture);
    super.onDestroy();
  }

  private void handleIntent(Intent intent) {
    if (Intent.ACTION_SEND.equals(intent.getAction())) {
      String value = intent.getStringExtra(Intent.EXTRA_TEXT);
      if (value != null) {
        java.util.regex.Matcher m =
            java.util.regex.Pattern.compile("https://[^\\s]+").matcher(value);
        if (m.find()) {
          query = m.group();
          tab = 1;
          renderPage();
          runSearch();
        }
      }
    } else if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null)
      importUris(List.of(intent.getData()));
  }

  private int dp(float v) {
    return Math.round(v * getResources().getDisplayMetrics().density);
  }

  private LinearLayout column() {
    LinearLayout v = new LinearLayout(this);
    v.setOrientation(LinearLayout.VERTICAL);
    return v;
  }

  private LinearLayout row() {
    LinearLayout v = new LinearLayout(this);
    v.setGravity(Gravity.CENTER_VERTICAL);
    return v;
  }

  private LinearLayout.LayoutParams size(int w, int h) {
    return new LinearLayout.LayoutParams(w < 0 ? w : dp(w), h < 0 ? h : dp(h));
  }

  private TextView text(String value, int sp, int color, boolean bold) {
    TextView t = new TextView(this);
    t.setText(value);
    t.setTextSize(sp);
    t.setTextColor(color);
    t.setFontFeatureSettings("kern");
    if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
    return t;
  }

  private void addSpace(LinearLayout parent, int height) {
    parent.addView(new View(this), size(1, height));
  }

  private GradientDrawable shape(int color, int radius) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(radius));
    return d;
  }

  private void rounded(View v, int color, int radius) {
    v.setBackground(shape(color, radius));
  }

  private void clickable(View v, int color, int radius, Runnable action) {
    v.setBackground(
        new RippleDrawable(
            ColorStateList.valueOf(0x33ffffff), shape(color, radius), shape(Color.WHITE, radius)));
    v.setOnClickListener(w -> action.run());
    v.setFocusable(true);
  }

  private TextView button(String label, boolean primary, Runnable action) {
    TextView b = text(label, 14, primary ? 0xff271b43 : accent, true);
    b.setGravity(Gravity.CENTER);
    b.setPadding(dp(20), dp(14), dp(20), dp(14));
    b.setMinHeight(dp(48));
    clickable(b, primary ? accent : HIGH, 26, action);
    return b;
  }

  private FrameLayout iconButton(
      String icon, String label, int color, int background, int diameter, Runnable action) {
    FrameLayout box = new FrameLayout(this);
    Icon image = new Icon(this, icon, color);
    box.addView(image, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
    box.setContentDescription(label);
    clickable(box, background, diameter / 2, action);
    box.setLayoutParams(size(diameter, diameter));
    return box;
  }

  private void outline(ImageView image, int radius) {
    image.setClipToOutline(true);
    image.setOutlineProvider(
        new ViewOutlineProvider() {
          @Override
          public void getOutline(View v, Outline out) {
            out.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(radius));
          }
        });
  }

  private EditText input(String hint, String value) {
    EditText e = new EditText(this);
    e.setTextColor(TEXT);
    e.setHintTextColor(MUTED);
    e.setTextSize(15);
    e.setSingleLine(true);
    e.setHint(hint);
    e.setText(value);
    e.setPadding(dp(18), dp(12), dp(18), dp(12));
    e.setBackground(shape(HIGH, 18));
    return e;
  }

  private LinearLayout paddedColumn() {
    LinearLayout v = column();
    v.setPadding(dp(22), dp(22), dp(22), dp(24));
    return v;
  }

  private void setup() {
    getWindow().setStatusBarColor(BG);
    getWindow().setNavigationBarColor(BG);
    if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
    root = column();
    root.setBackgroundColor(BG);
    setContentView(root);
    root.setOnApplyWindowInsetsListener(
        (v, insets) -> {
          if (Build.VERSION.SDK_INT >= 30) {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars()),
                ime = insets.getInsets(WindowInsets.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
          }
          return insets;
        });
    root.requestApplyInsets();
    body = new FrameLayout(this);
    root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
    mini = row();
    mini.setPadding(dp(10), dp(8), dp(6), dp(8));
    clickable(mini, 0xff34303f, 20, this::showPlayer);
    LinearLayout.LayoutParams mp = size(-1, 72);
    mp.setMargins(dp(12), dp(5), dp(12), dp(5));
    root.addView(mini, mp);
    miniArt = new ImageView(this);
    outline(miniArt, 12);
    mini.addView(miniArt, size(48, 48));
    LinearLayout labels = column();
    labels.setPadding(dp(12), 0, dp(8), 0);
    miniTitle = text("Nothing playing yet", 14, TEXT, true);
    miniTitle.setSingleLine(true);
    miniTitle.setEllipsize(TextUtils.TruncateAt.END);
    miniArtist = text("Pick a track to start", 12, MUTED, false);
    miniArtist.setSingleLine(true);
    labels.addView(miniTitle);
    addSpace(labels, 4);
    labels.addView(miniArtist);
    mini.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
    FrameLayout play =
        iconButton("play", "Play or pause", accent, Color.TRANSPARENT, 48, this::togglePlayback);
    miniPlay = (Icon) play.getChildAt(0);
    mini.addView(play);
    mini.addView(
        iconButton(
            "next",
            "Next track",
            TEXT,
            Color.TRANSPARENT,
            44,
            () -> {
              if (controller != null) controller.seekToNextMediaItem();
            }));
    mini.setVisibility(View.GONE);
    nav = row();
    nav.setPadding(dp(6), dp(5), dp(6), dp(4));
    root.addView(nav, size(-1, 72));
    renderNav();
    renderPage();
  }

  private void renderNav() {
    nav.removeAllViews();
    String[] names = {"Library", "Search", "Playlists", "Settings"};
    String[] icons = {"library", "search", "playlist", "settings"};
    for (int i = 0; i < names.length; i++) {
      int target = i;
      LinearLayout item = column();
      item.setGravity(Gravity.CENTER);
      FrameLayout pill = new FrameLayout(this);
      rounded(pill, i == tab ? 0xff4a3b63 : Color.TRANSPARENT, 18);
      pill.addView(
          new Icon(this, icons[i], i == tab ? accent : MUTED),
          new FrameLayout.LayoutParams(dp(23), dp(23), Gravity.CENTER));
      item.addView(pill, size(60, 32));
      TextView label = text(names[i], 11, i == tab ? TEXT : MUTED, i == tab);
      label.setGravity(Gravity.CENTER);
      item.addView(label, size(-1, 22));
      clickable(
          item,
          Color.TRANSPARENT,
          18,
          () -> {
            tab = target;
            selectedPlaylist = null;
            selectedAlbum = null;
            renderNav();
            renderPage();
          });
      nav.addView(item, new LinearLayout.LayoutParams(0, -1, 1));
    }
  }

  private void renderPage() {
    if (isFinishing()) return;
    body.removeAllViews();
    downloadStatus = null;
    renderNav();
    if (tab == 0) renderHome();
    else if (tab == 1) renderSearch();
    else if (tab == 2) renderPlaylists();
    else renderSettings();
  }

  private LinearLayout heading(String eyebrow, String title) {
    LinearLayout h = column();
    TextView small = text(eyebrow.toUpperCase(Locale.ROOT), 11, accent, true);
    small.setLetterSpacing(0.14f);
    h.addView(small);
    addSpace(h, 7);
    h.addView(text(title, 32, TEXT, true));
    return h;
  }

  private void chips(
      LinearLayout parent, String[] labels, int selected, java.util.function.IntConsumer action) {
    LinearLayout r = row();
    for (int i = 0; i < labels.length; i++) {
      int value = i;
      TextView chip = text(labels[i], 12, i == selected ? 0xff271b43 : MUTED, true);
      chip.setGravity(Gravity.CENTER);
      chip.setPadding(dp(12), dp(11), dp(12), dp(11));
      clickable(chip, i == selected ? accent : HIGH, 20, () -> action.accept(value));
      LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(42), 1);
      if (i > 0) p.leftMargin = dp(8);
      r.addView(chip, p);
    }
    parent.addView(r, size(-1, 42));
  }

  private void renderHome() {
    LinearLayout header = paddedColumn();
    LinearLayout top = row();
    top.addView(
        heading("Made for humans", selectedAlbum == null ? "Your library" : selectedAlbum),
        new LinearLayout.LayoutParams(0, -2, 1));
    top.addView(
        iconButton(
            selectedAlbum == null ? "plus" : "back",
            selectedAlbum == null ? "Import music" : "Back to albums",
            accent,
            HIGH,
            48,
            () -> {
              if (selectedAlbum != null) {
                selectedAlbum = null;
                renderHomeFresh();
              } else importMenu();
            }));
    header.addView(top);
    addSpace(header, 24);
    List<Track> all = library.all();
    if (selectedAlbum == null) {
      LinearLayout hero = row();
      hero.setPadding(dp(22), dp(22), dp(16), dp(22));
      GradientDrawable gradient =
          new GradientDrawable(
              GradientDrawable.Orientation.TL_BR, new int[] {0xff4f378b, 0xff312548});
      gradient.setCornerRadius(dp(26));
      hero.setBackground(gradient);
      LinearLayout copy = column();
      copy.addView(text("Your music.\nYour way.", 26, 0xfff2e9ff, true));
      addSpace(copy, 10);
      copy.addView(
          text(library.offline().size() + " tracks • always with you", 12, 0xffddcee9, false));
      addSpace(copy, 16);
      copy.addView(
          button(
              all.isEmpty() ? "＋  Import music" : "Shuffle library",
              true,
              () -> {
                if (all.isEmpty()) importMenu();
                else play(all, 0, true);
              }),
          size(-2, 48));
      hero.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
      ImageView logo = new ImageView(this);
      logo.setImageResource(R.mipmap.ic_launcher);
      logo.setAlpha(.8f);
      hero.addView(logo, size(82, 100));
      header.addView(hero);
      addSpace(header, 22);
      chips(
          header,
          new String[] {"All tracks", "Liked", "Albums"},
          homeFilter,
          i -> {
            homeFilter = i;
            selectedAlbum = null;
            renderHomeFresh();
          });
      addSpace(header, 22);
    }
    if (homeFilter == 2 && selectedAlbum == null) {
      Map<String, List<Track>> albums = new LinkedHashMap<>();
      for (Track t : all)
        if (t.offline())
          albums
              .computeIfAbsent(
                  t.album.isBlank() ? "Unknown album" : t.album, k -> new ArrayList<>())
              .add(t);
      for (Map.Entry<String, List<Track>> entry : albums.entrySet()) {
        LinearLayout card = row();
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        ImageView art = new ImageView(this);
        outline(art, 12);
        Artwork.load(art, entry.getValue().get(0));
        card.addView(art, size(56, 56));
        LinearLayout copy = column();
        copy.setPadding(dp(14), 0, 0, 0);
        copy.addView(text(entry.getKey(), 16, TEXT, true));
        copy.addView(text(entry.getValue().size() + " tracks", 12, MUTED, false));
        card.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        clickable(
            card,
            SURFACE,
            18,
            () -> {
              selectedAlbum = entry.getKey();
              renderHomeFresh();
            });
        header.addView(card, size(-1, 84));
        addSpace(header, 8);
      }
      if (albums.isEmpty())
        empty(header, "Albums live here", "Import music with album tags to see your albums.");
      body.addView(scroll(header));
      return;
    }
    List<Track> tracks = homeFilter == 1 ? library.favorites() : all;
    if (selectedAlbum != null) {
      tracks = new ArrayList<>();
      for (Track t : all)
        if ((t.album.isBlank() ? "Unknown album" : t.album).equals(selectedAlbum) && t.offline())
          tracks.add(t);
    }
    tracks.sort(Comparator.comparing(t -> t.title.toLowerCase(Locale.ROOT)));
    header.addView(
        text(
            (homeFilter == 1 ? "Liked songs" : "Tracks") + "  ·  " + tracks.size(),
            16,
            TEXT,
            true));
    addSpace(header, 8);
    if (tracks.isEmpty())
      empty(
          header,
          homeFilter == 1 ? "Keep your favorites close" : "Bring your music along",
          homeFilter == 1
              ? "Tap the heart on any track to save it here."
              : "Import audio from your phone, or find your next favorite in Search.");
    showTracks(header, tracks);
  }

  private void renderHomeFresh() {
    body.removeAllViews();
    renderHome();
  }

  private void empty(LinearLayout target, String title, String subtitle) {
    addSpace(target, 25);
    Icon icon = new Icon(this, "music", accent);
    LinearLayout center = column();
    center.setGravity(Gravity.CENTER);
    center.addView(icon, size(42, 42));
    addSpace(center, 18);
    TextView a = text(title, 20, TEXT, true);
    a.setGravity(Gravity.CENTER);
    center.addView(a);
    addSpace(center, 9);
    TextView b = text(subtitle, 14, MUTED, false);
    b.setGravity(Gravity.CENTER);
    b.setLineSpacing(dp(5), 1);
    center.addView(b);
    addSpace(center, 24);
    target.addView(center, size(-1, -2));
  }

  private ScrollView scroll(View child) {
    ScrollView scroll = new ScrollView(this);
    scroll.setFillViewport(true);
    scroll.setClipToPadding(false);
    scroll.addView(child);
    return scroll;
  }

  private void showTracks(LinearLayout header, List<Track> tracks) {
    visibleTracks = new ArrayList<>(tracks);
    ListView list = new ListView(this);
    list.setDivider(null);
    list.setSelector(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
    list.setClipToPadding(false);
    list.setPadding(0, 0, 0, dp(12));
    list.addHeaderView(header, null, false);
    list.setAdapter(
        new BaseAdapter() {
          @Override
          public int getCount() {
            return tracks.size();
          }

          @Override
          public Track getItem(int i) {
            return tracks.get(i);
          }

          @Override
          public long getItemId(int i) {
            return getItem(i).id.hashCode();
          }

          @Override
          public View getView(int i, View old, ViewGroup parent) {
            return trackRow(getItem(i), tracks, i);
          }
        });
    body.addView(list);
  }

  private View trackRow(Track t, List<Track> queue, int index) {
    LinearLayout row = row();
    row.setPadding(dp(22), dp(7), dp(12), dp(7));
    ImageView art = new ImageView(this);
    outline(art, 14);
    Artwork.load(art, t);
    row.addView(art, size(54, 54));
    LinearLayout labels = column();
    labels.setPadding(dp(14), 0, dp(4), 0);
    TextView title = text(t.title, 15, TEXT, true);
    title.setSingleLine(true);
    title.setEllipsize(TextUtils.TruncateAt.END);
    labels.addView(title);
    addSpace(labels, 5);
    String info =
        t.artist
            + "  ·  "
            + (t.offline() ? "Offline" : t.id.startsWith("sc_") ? "SoundCloud" : "YouTube");
    TextView artist = text(info, 12, MUTED, false);
    artist.setSingleLine(true);
    artist.setEllipsize(TextUtils.TruncateAt.END);
    labels.addView(artist);
    row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
    if (t.duration > 0) {
      TextView time = text(time(t.duration), 11, MUTED, false);
      row.addView(time, size(38, -2));
    }
    row.addView(
        iconButton(
            "more", "Options for " + t.title, MUTED, Color.TRANSPARENT, 48, () -> trackMenu(t)));
    clickable(row, Color.TRANSPARENT, 12, () -> play(queue, index, false));
    return row;
  }

  private void renderSearch() {
    LinearLayout head = paddedColumn();
    head.addView(heading("Find your next favorite", "Search"));
    addSpace(head, 22);
    chips(
        head,
        new String[] {"YouTube", "SoundCloud", "My library"},
        searchPlatform,
        i -> {
          searchPlatform = i;
          searchGeneration.incrementAndGet();
          searching = false;
          if (searchProcess != null) YoutubeDL.getInstance().destroyProcessById(searchProcess);
          results = new ArrayList<>();
          searchMessage = "Search for a song, artist, or paste a link.";
          renderPage();
        });
    addSpace(head, 16);
    LinearLayout search = row();
    EditText field = input("Song, artist, or https:// link", query);
    field.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
    field.addTextChangedListener(
        new TextWatcher() {
          @Override
          public void beforeTextChanged(CharSequence s, int st, int count, int after) {}

          @Override
          public void onTextChanged(CharSequence s, int st, int before, int count) {
            query = s.toString();
          }

          @Override
          public void afterTextChanged(Editable e) {}
        });
    field.setOnEditorActionListener(
        (v, action, event) -> {
          if (action == EditorInfo.IME_ACTION_SEARCH) {
            hideKeyboard();
            runSearch();
            return true;
          }
          return false;
        });
    search.addView(field, new LinearLayout.LayoutParams(0, dp(54), 1));
    LinearLayout.LayoutParams p = size(50, 50);
    p.leftMargin = dp(10);
    search.addView(
        iconButton(
            "search",
            "Search tracks",
            0xff271b43,
            accent,
            50,
            () -> {
              hideKeyboard();
              runSearch();
            }),
        p);
    head.addView(search);
    addSpace(head, 20);
    TextView status = text(searching ? "Finding your music…" : searchMessage, 13, MUTED, false);
    status.setLineSpacing(dp(4), 1);
    head.addView(status);
    if (searching) {
      ProgressBar progress = new ProgressBar(this);
      head.addView(progress, size(36, 36));
    }
    addSpace(head, 12);
    if (results.isEmpty() && !searching)
      empty(
          head,
          "A whole world of music",
          "Search YouTube or SoundCloud, or browse the music you've saved.");
    showTracks(head, results);
  }

  private void hideKeyboard() {
    View focused = getCurrentFocus();
    if (focused != null)
      getSystemService(InputMethodManager.class)
          .hideSoftInputFromWindow(focused.getWindowToken(), 0);
    body.clearFocus();
  }

  private void runSearch() {
    String value = query.trim();
    if (value.isEmpty()) return;
    if (value.startsWith("http") && !Online.validSource(value)) {
      error("Unsupported link", "Paste a secure YouTube or SoundCloud link.");
      return;
    }
    if (searchProcess != null) YoutubeDL.getInstance().destroyProcessById(searchProcess);
    int generation = searchGeneration.incrementAndGet();
    int platform = searchPlatform;
    if (platform == 2) {
      results = new ArrayList<>();
      for (Track t : library.all())
        if ((t.title + " " + t.artist + " " + t.album)
            .toLowerCase(Locale.ROOT)
            .contains(value.toLowerCase(Locale.ROOT))) results.add(t);
      searching = false;
      searchMessage = results.size() + " tracks found in your library";
      renderPage();
      return;
    }
    searching = true;
    results = new ArrayList<>();
    String process = "search-" + generation;
    searchProcess = process;
    renderPage();
    RiffApplication.IO.execute(
        () -> {
          try {
            List<Track> tracks = Online.search(this, value, platform == 1, process);
            runOnUiThread(
                () -> {
                  if (generation != searchGeneration.get() || isDestroyed()) return;
                  results = tracks;
                  searching = false;
                  searchMessage = tracks.size() + " results · tap to play, or ⋮ to download";
                  if (tab == 1) renderPage();
                });
          } catch (Exception e) {
            runOnUiThread(
                () -> {
                  if (generation != searchGeneration.get() || isDestroyed()) return;
                  searching = false;
                  searchMessage =
                      "Couldn't search. Check your connection or update the download engine in"
                          + " Settings.";
                  if (tab == 1) renderPage();
                  error("Search unavailable", DownloadService.friendly(e));
                });
          }
        });
  }

  private void renderPlaylists() {
    if (selectedPlaylist != null) {
      Library.Playlist selected = selectedPlaylist;
      LinearLayout head = paddedColumn();
      LinearLayout top = row();
      top.addView(
          iconButton(
              "back",
              "All playlists",
              accent,
              HIGH,
              48,
              () -> {
                selectedPlaylist = null;
                renderPage();
              }));
      LinearLayout copy = heading("Your collection", selected.name);
      copy.setPadding(dp(14), 0, 0, 0);
      top.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
      top.addView(
          iconButton(
              "more",
              "Playlist options",
              MUTED,
              Color.TRANSPARENT,
              48,
              () -> playlistMenu(selected)));
      head.addView(top);
      addSpace(head, 22);
      List<Track> tracks = library.contents(selected);
      LinearLayout actions = row();
      actions.addView(
          button("Play all", true, () -> play(tracks, 0, false)),
          new LinearLayout.LayoutParams(0, dp(48), 1));
      LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(48), 1);
      p.leftMargin = dp(10);
      actions.addView(button("Add songs", false, () -> pickSongs(selected)), p);
      head.addView(actions);
      addSpace(head, 20);
      head.addView(text(tracks.size() + " tracks", 13, MUTED, false));
      if (tracks.isEmpty()) empty(head, "Make it yours", "Add a few songs to start your playlist.");
      showTracks(head, tracks);
      return;
    }
    LinearLayout head = paddedColumn();
    LinearLayout top = row();
    top.addView(
        heading("Every mood, one place", "Playlists"), new LinearLayout.LayoutParams(0, -2, 1));
    top.addView(
        iconButton("plus", "Create playlist", accent, HIGH, 48, () -> createPlaylist(null)));
    head.addView(top);
    addSpace(head, 26);
    for (Library.Playlist playlist : library.playlists()) {
      LinearLayout card = row();
      card.setPadding(dp(16), dp(16), dp(6), dp(16));
      rounded(card, SURFACE, 22);
      FrameLayout symbol = new FrameLayout(this);
      rounded(symbol, 0xff4f378b, 16);
      symbol.addView(
          new Icon(this, "playlist", accent),
          new FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER));
      card.addView(symbol, size(60, 60));
      LinearLayout copy = column();
      copy.setPadding(dp(16), 0, dp(8), 0);
      TextView title = text(playlist.name, 18, TEXT, true);
      title.setSingleLine(true);
      title.setEllipsize(TextUtils.TruncateAt.END);
      copy.addView(title);
      addSpace(copy, 5);
      copy.addView(text(playlist.ids.size() + " tracks", 13, MUTED, false));
      card.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
      card.addView(
          iconButton(
              "more",
              "Options for " + playlist.name,
              MUTED,
              Color.TRANSPARENT,
              48,
              () -> playlistMenu(playlist)));
      clickable(
          card,
          SURFACE,
          22,
          () -> {
            selectedPlaylist = playlist;
            renderPage();
          });
      head.addView(card, size(-1, 96));
      addSpace(head, 12);
    }
    if (library.playlists().isEmpty()) {
      empty(
          head,
          "Soundtrack your day",
          "Create a playlist for late nights, long walks, or whatever you're feeling.");
      head.addView(button("Create a playlist", true, () -> createPlaylist(null)));
    }
    body.addView(scroll(head));
  }

  private void createPlaylist(Track add) {
    EditText field = input("Playlist name", "");
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle("New playlist")
            .setView(dialogContent(field))
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Create", null)
            .create();
    dialog.setOnShowListener(
        w ->
            dialog
                .getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(
                    v -> {
                      String name = field.getText().toString().trim();
                      if (name.isEmpty()) {
                        field.setError("Give your playlist a name");
                        return;
                      }
                      Library.Playlist p = library.createPlaylist(name);
                      if (add != null) library.add(p, add);
                      dialog.dismiss();
                      toast("Playlist created");
                      if (tab == 2) renderPage();
                    }));
    dialog.show();
  }

  private void pickSongs(Library.Playlist p) {
    List<Track> tracks = library.all();
    if (tracks.isEmpty()) {
      toast("Import or save a song first");
      return;
    }
    String[] titles = new String[tracks.size()];
    boolean[] selected = new boolean[tracks.size()];
    for (int i = 0; i < tracks.size(); i++) {
      titles[i] = tracks.get(i).title + " — " + tracks.get(i).artist;
      selected[i] = p.ids.contains(tracks.get(i).id);
    }
    new AlertDialog.Builder(this)
        .setTitle("Songs in " + p.name)
        .setMultiChoiceItems(titles, selected, (d, i, checked) -> selected[i] = checked)
        .setNegativeButton("Cancel", null)
        .setPositiveButton(
            "Save",
            (d, w) -> {
              for (int i = 0; i < tracks.size(); i++)
                if (selected[i]) library.add(p, tracks.get(i));
                else library.remove(p, tracks.get(i).id);
              renderPage();
            })
        .show();
  }

  private void addToPlaylist(Track t) {
    List<Library.Playlist> playlists = library.playlists();
    String[] names = new String[playlists.size() + 1];
    for (int i = 0; i < playlists.size(); i++) names[i] = playlists.get(i).name;
    names[names.length - 1] = "＋ New playlist";
    new AlertDialog.Builder(this)
        .setTitle("Add to playlist")
        .setItems(
            names,
            (d, i) -> {
              if (i == playlists.size()) createPlaylist(t);
              else {
                library.add(playlists.get(i), t);
                toast("Added to " + playlists.get(i).name);
              }
            })
        .show();
  }

  private void playlistMenu(Library.Playlist p) {
    new AlertDialog.Builder(this)
        .setTitle(p.name)
        .setItems(
            new String[] {"Rename", "Delete playlist"},
            (d, i) -> {
              if (i == 0) {
                EditText field = input("Playlist name", p.name);
                new AlertDialog.Builder(this)
                    .setTitle("Rename playlist")
                    .setView(dialogContent(field))
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton(
                        "Save",
                        (a, w) -> {
                          String name = field.getText().toString().trim();
                          if (!name.isEmpty()) {
                            library.rename(p, name);
                            renderPage();
                          }
                        })
                    .show();
              } else
                new AlertDialog.Builder(this)
                    .setTitle("Delete " + p.name + "?")
                    .setMessage("Your music files stay in your library.")
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton(
                        "Delete",
                        (a, w) -> {
                          library.delete(p);
                          selectedPlaylist = null;
                          renderPage();
                        })
                    .show();
            })
        .show();
  }

  private void trackMenu(Track t) {
    List<String> names =
        new ArrayList<>(
            List.of(
                library.liked(t.id) ? "Unlike" : "Like",
                "Add to playlist",
                "Play next",
                "Lyrics",
                "Edit track details"));
    if (t.offline()) {
      names.add("Trim audio");
      names.add("Export audio");
      names.add("Share audio");
    } else names.add("Download for offline listening");
    if (selectedPlaylist != null) names.add("Remove from this playlist");
    if (library.find(t.id) != null) names.add("Remove from library");
    new AlertDialog.Builder(this)
        .setTitle(t.title)
        .setItems(
            names.toArray(new String[0]),
            (d, i) -> {
              switch (names.get(i)) {
                case "Like":
                case "Unlike":
                  library.toggleLike(t);
                  updatePlayback();
                  if (tab == 0) renderPage();
                  break;
                case "Add to playlist":
                  addToPlaylist(t);
                  break;
                case "Play next":
                  if (controller != null) {
                    library.put(t);
                    controller.addMediaItem(
                        controller.getMediaItemCount() == 0
                            ? 0
                            : controller.getCurrentMediaItemIndex() + 1,
                        preferred(t).mediaItem());
                    toast("Added to queue");
                  }
                  break;
                case "Lyrics":
                  showLyrics(t);
                  break;
                case "Edit track details":
                  editDetails(t);
                  break;
                case "Download for offline listening":
                  download(t);
                  break;
                case "Trim audio":
                  trimDialog(t);
                  break;
                case "Export audio":
                  export(t);
                  break;
                case "Share audio":
                  {
                    Uri uri =
                        androidx.core.content.FileProvider.getUriForFile(
                            this,
                            getPackageName() + ".files",
                            new File(Uri.parse(t.uri).getPath()));
                    startActivity(
                        Intent.createChooser(
                            new Intent(Intent.ACTION_SEND)
                                .setType("audio/*")
                                .putExtra(Intent.EXTRA_STREAM, uri)
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                            "Share audio"));
                    break;
                  }
                case "Remove from this playlist":
                  library.remove(selectedPlaylist, t.id);
                  renderPage();
                  break;
                case "Remove from library":
                  new AlertDialog.Builder(this)
                      .setTitle("Remove this track?")
                      .setMessage(
                          "This removes it from Riff and your playlists. Export a copy first if you"
                              + " want to keep it outside the app.")
                      .setNegativeButton("Cancel", null)
                      .setPositiveButton(
                          "Remove",
                          (a, w) -> {
                            library.forget(t.id);
                            if (t.uri.startsWith("file:")) {
                              File file = new File(Uri.parse(t.uri).getPath());
                              if (file.getAbsolutePath()
                                  .startsWith(
                                      new File(getFilesDir(), "music").getAbsolutePath()
                                          + File.separator)) file.delete();
                            }
                            renderPage();
                          })
                      .show();
                  break;
              }
            })
        .show();
  }

  private void editDetails(Track t) {
    LinearLayout content = paddedColumn();
    EditText title = input("Title", t.title),
        artist = input("Artist", t.artist),
        album = input("Album", t.album);
    content.addView(title);
    addSpace(content, 12);
    content.addView(artist);
    addSpace(content, 12);
    content.addView(album);
    new AlertDialog.Builder(this)
        .setTitle("Track details")
        .setView(content)
        .setNegativeButton("Cancel", null)
        .setPositiveButton(
            "Save",
            (d, w) -> {
              if (!title.getText().toString().trim().isEmpty())
                t.title = title.getText().toString().trim();
              t.artist = artist.getText().toString().trim();
              t.album = album.getText().toString().trim();
              library.put(t);
              renderPage();
            })
        .show();
  }

  private void play(List<Track> queue, int start, boolean shuffle) {
    if (controller == null) {
      toast("Player is connecting…");
      return;
    }
    if (queue.isEmpty()) {
      toast("Add some music first");
      return;
    }
    List<MediaItem> items = new ArrayList<>();
    for (Track track : queue) {
      Track t = preferred(track);
      if (library.find(t.id) == null) library.put(t);
      items.add(t.mediaItem());
    }
    controller.setShuffleModeEnabled(shuffle);
    controller.setMediaItems(items, Math.min(start, items.size() - 1), 0);
    controller.prepare();
    controller.play();
    updatePlayback();
  }

  private Track preferred(Track track) {
    Track stored = library.find(track.id);
    return stored != null && stored.offline() ? stored : track;
  }

  private Track current() {
    if (controller == null || controller.getCurrentMediaItem() == null) return null;
    return library.find(controller.getCurrentMediaItem().mediaId);
  }

  private void togglePlayback() {
    if (controller == null || controller.getMediaItemCount() == 0) return;
    if (controller.getPlayWhenReady()) controller.pause();
    else {
      if (controller.getPlaybackState() == Player.STATE_IDLE
          || controller.getPlaybackState() == Player.STATE_ENDED) controller.prepare();
      controller.play();
    }
    updatePlayback();
  }

  private static String time(long millis) {
    long seconds = Math.max(0, millis) / 1000;
    return String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60);
  }

  private void updatePlayback() {
    if (controller == null) return;
    Track track = current();
    mini.setVisibility(track == null ? View.GONE : View.VISIBLE);
    if (track == null) return;
    miniTitle.setText(track.title);
    miniArtist.setText(
        controller.getPlaybackState() == Player.STATE_BUFFERING ? "Loading audio…" : track.artist);
    miniPlay.kind(controller.getPlayWhenReady() ? "pause" : "play");
    if (!track.id.equals(miniArt.getContentDescription())) {
      Artwork.load(miniArt, track);
      miniArt.setContentDescription(track.id);
    }
    if (playerDialog != null && playerDialog.isShowing()) {
      if (!track.id.equals(renderedTrack)) {
        renderedTrack = track.id;
        playerTitle.setText(track.title);
        playerArtist.setText(track.artist);
        Artwork.load(playerArt, track);
      }
      playerPlay.kind(controller.getPlayWhenReady() ? "pause" : "play");
      playerLike.kind(library.liked(track.id) ? "heartfill" : "heart");
      playerShuffle.color(controller.getShuffleModeEnabled() ? accent : MUTED);
      playerRepeat.color(controller.getRepeatMode() != Player.REPEAT_MODE_OFF ? accent : MUTED);
      repeatLabel.setText(controller.getRepeatMode() == Player.REPEAT_MODE_ONE ? "1" : "");
      long duration = controller.getDuration();
      if (duration == C.TIME_UNSET || duration <= 0) duration = track.duration;
      long position = controller.getCurrentPosition();
      playerSeek.setMax((int) (Math.max(0, duration) / 1000));
      if (!seeking) playerSeek.setProgress((int) (position / 1000));
      playerPosition.setText(time(position));
      playerDuration.setText(time(duration));
    }
    if (lyricsDialog != null
        && lyricsDialog.isShowing()
        && lrcTrack != null
        && lrcTrack.id.equals(track.id)) {
      int active = Lyrics.active(lyricLines, controller.getCurrentPosition());
      if (active != lastLyric) {
        lastLyric = active;
        for (int i = 0; i < lyricViews.size(); i++)
          lyricViews.get(i).setTextColor(i == active ? accent : MUTED);
        if (active >= 0 && active < lyricViews.size())
          lyricsScroll.smoothScrollTo(0, Math.max(0, lyricViews.get(active).getTop() - dp(140)));
      }
    }
  }

  private void showPlayer() {
    if (current() == null) return;
    if (playerDialog != null) playerDialog.dismiss();
    playerDialog = new Dialog(this);
    LinearLayout content = paddedColumn();
    content.setBackgroundColor(BG);
    LinearLayout top = row();
    top.addView(iconButton("down", "Close player", TEXT, HIGH, 48, () -> playerDialog.dismiss()));
    TextView label = text("NOW PLAYING", 12, MUTED, true);
    label.setLetterSpacing(.15f);
    label.setGravity(Gravity.CENTER);
    label.setContentDescription("Open playback queue");
    label.setOnClickListener(v -> showQueue());
    top.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
    top.addView(
        iconButton(
            "more",
            "Track options",
            TEXT,
            Color.TRANSPARENT,
            48,
            () -> {
              Track t = current();
              if (t != null) trackMenu(t);
            }));
    content.addView(top);
    addSpace(content, 22);
    playerArt = new ImageView(this);
    outline(playerArt, 30);
    int width =
        Math.min(
            400,
            (int)
                    (getResources().getDisplayMetrics().widthPixels
                        / getResources().getDisplayMetrics().density)
                - 44);
    int screenHeightDp =
        (int)
            (getResources().getDisplayMetrics().heightPixels
                / getResources().getDisplayMetrics().density);
    int artworkHeight = Math.max(120, Math.min(width, screenHeightDp - 420));
    content.addView(playerArt, size(-1, artworkHeight));
    addSpace(content, 24);
    LinearLayout info = row();
    LinearLayout copy = column();
    playerTitle = text("", 25, TEXT, true);
    playerTitle.setMaxLines(2);
    playerTitle.setEllipsize(TextUtils.TruncateAt.END);
    playerArtist = text("", 15, MUTED, false);
    copy.addView(playerTitle);
    addSpace(copy, 6);
    copy.addView(playerArtist);
    info.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
    FrameLayout like =
        iconButton(
            "heart",
            "Like this track",
            accent,
            HIGH,
            48,
            () -> {
              Track t = current();
              if (t != null) {
                library.toggleLike(t);
                updatePlayback();
              }
            });
    playerLike = (Icon) like.getChildAt(0);
    info.addView(like);
    content.addView(info);
    addSpace(content, 18);
    playerSeek = new SeekBar(this);
    playerSeek.setProgressTintList(ColorStateList.valueOf(accent));
    playerSeek.setThumbTintList(ColorStateList.valueOf(accent));
    playerSeek.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          @Override
          public void onStartTrackingTouch(SeekBar v) {
            seeking = true;
          }

          @Override
          public void onStopTrackingTouch(SeekBar v) {
            if (controller != null) controller.seekTo(v.getProgress() * 1000L);
            seeking = false;
          }

          @Override
          public void onProgressChanged(SeekBar v, int progress, boolean user) {
            if (user) playerPosition.setText(time(progress * 1000L));
          }
        });
    content.addView(playerSeek, size(-1, 36));
    LinearLayout times = row();
    playerPosition = text("0:00", 12, MUTED, false);
    playerDuration = text("0:00", 12, MUTED, false);
    playerDuration.setGravity(Gravity.END);
    times.addView(playerPosition, new LinearLayout.LayoutParams(0, -2, 1));
    times.addView(playerDuration);
    content.addView(times);
    addSpace(content, 22);
    LinearLayout controls = row();
    controls.setGravity(Gravity.CENTER);
    FrameLayout shuffle =
        iconButton(
            "shuffle",
            "Toggle shuffle",
            MUTED,
            Color.TRANSPARENT,
            48,
            () -> {
              controller.setShuffleModeEnabled(!controller.getShuffleModeEnabled());
              updatePlayback();
            });
    playerShuffle = (Icon) shuffle.getChildAt(0);
    controls.addView(shuffle);
    controls.addView(
        iconButton(
            "prev",
            "Previous track",
            TEXT,
            Color.TRANSPARENT,
            52,
            () -> controller.seekToPrevious()));
    FrameLayout play =
        iconButton("play", "Play or pause", 0xff271b43, accent, 80, this::togglePlayback);
    playerPlay = (Icon) play.getChildAt(0);
    playerPlay.setLayoutParams(new FrameLayout.LayoutParams(dp(36), dp(36), Gravity.CENTER));
    LinearLayout.LayoutParams pp = size(80, 80);
    pp.setMargins(dp(12), 0, dp(12), 0);
    controls.addView(play, pp);
    controls.addView(
        iconButton(
            "next",
            "Next track",
            TEXT,
            Color.TRANSPARENT,
            52,
            () -> controller.seekToNextMediaItem()));
    FrameLayout repeat =
        iconButton(
            "repeat",
            "Change repeat mode",
            MUTED,
            Color.TRANSPARENT,
            48,
            () -> {
              controller.setRepeatMode((controller.getRepeatMode() + 1) % 3);
              updatePlayback();
            });
    playerRepeat = (Icon) repeat.getChildAt(0);
    repeatLabel = text("", 9, accent, true);
    FrameLayout.LayoutParams rp = new FrameLayout.LayoutParams(dp(12), dp(12), Gravity.CENTER);
    repeat.addView(repeatLabel, rp);
    controls.addView(repeat);
    content.addView(controls);
    addSpace(content, 22);
    LinearLayout tools = row();
    tools.addView(
        button(
            "Lyrics",
            false,
            () -> {
              Track t = current();
              if (t != null) showLyrics(t);
            }),
        new LinearLayout.LayoutParams(0, dp(48), 1));
    LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(0, dp(48), 1);
    ep.leftMargin = dp(12);
    tools.addView(button("Sound effects", false, this::effectsDialog), ep);
    content.addView(tools);
    playerDialog.setContentView(scroll(content));
    playerDialog.show();
    playerDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
    playerDialog.getWindow().setLayout(-1, -1);
    renderedTrack = "";
    updatePlayback();
  }

  private LinearLayout dialogContent(View view) {
    LinearLayout content = paddedColumn();
    content.addView(view);
    return content;
  }

  private void showQueue() {
    if (controller == null || controller.getMediaItemCount() == 0) return;
    String[] titles = new String[controller.getMediaItemCount()];
    for (int i = 0; i < titles.length; i++)
      titles[i] =
          (i == controller.getCurrentMediaItemIndex() ? "▶  " : "")
              + controller.getMediaItemAt(i).mediaMetadata.title;
    new AlertDialog.Builder(this)
        .setTitle("Playback queue")
        .setItems(
            titles,
            (d, i) -> {
              controller.seekToDefaultPosition(i);
              controller.prepare();
              controller.play();
            })
        .setNegativeButton("Close", null)
        .setNeutralButton(
            "Remove a track",
            (d, w) -> {
              new AlertDialog.Builder(this)
                  .setTitle("Remove from queue")
                  .setItems(
                      titles,
                      (a, i) -> {
                        controller.removeMediaItem(i);
                        updatePlayback();
                      })
                  .setNegativeButton("Cancel", null)
                  .show();
            })
        .show();
  }

  private void importMenu() {
    new AlertDialog.Builder(this)
        .setTitle("Add music")
        .setItems(
            new String[] {"Choose audio files", "Import a folder"},
            (d, i) -> {
              if (i == 0) chooseAudio();
              else
                startActivityForResult(
                    new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                    11);
            })
        .show();
  }

  private void chooseAudio() {
    Intent intent =
        new Intent(Intent.ACTION_OPEN_DOCUMENT)
            .setType("audio/*")
            .addCategory(Intent.CATEGORY_OPENABLE)
            .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    startActivityForResult(intent, 10);
  }

  private void importUris(List<Uri> uris) {
    toast("Importing " + uris.size() + " track" + (uris.size() == 1 ? "" : "s") + "…");
    RiffApplication.IO.execute(
        () -> {
          int count = 0;
          String failure = "";
          for (Uri uri : uris) {
            try {
              Importer.importAudio(this, uri);
              count++;
            } catch (Exception e) {
              failure = DownloadService.friendly(e);
            }
          }
          int done = count;
          String message = failure;
          runOnUiThread(
              () -> {
                if (isDestroyed()) return;
                tab = 0;
                renderPage();
                toast("Imported " + done + " track" + (done == 1 ? "" : "s"));
                if (!message.isEmpty()) error("Some tracks couldn't be imported", message);
              });
        });
  }

  private void importFolder(Uri tree) {
    toast("Finding audio files…");
    RiffApplication.IO.execute(
        () -> {
          try {
            List<Uri> files = new ArrayList<>();
            collectAudio(tree, DocumentsContract.getTreeDocumentId(tree), files, 0);
            runOnUiThread(
                () -> {
                  if (files.isEmpty()) toast("No audio files found");
                  else importUris(files);
                });
          } catch (Exception e) {
            runOnUiThread(() -> error("Couldn't read this folder", DownloadService.friendly(e)));
          }
        });
  }

  private void collectAudio(Uri tree, String id, List<Uri> files, int depth) throws Exception {
    if (depth > 12 || files.size() > 5000) return;
    Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, id);
    try (android.database.Cursor cursor =
        getContentResolver()
            .query(
                children,
                new String[] {
                  DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                  DocumentsContract.Document.COLUMN_MIME_TYPE,
                  DocumentsContract.Document.COLUMN_DISPLAY_NAME
                },
                null,
                null,
                null)) {
      if (cursor == null) return;
      while (cursor.moveToNext()) {
        String child = cursor.getString(0), mime = cursor.getString(1), name = cursor.getString(2);
        if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime))
          collectAudio(tree, child, files, depth + 1);
        else if ((mime != null && mime.startsWith("audio/"))
            || (name != null
                && name.toLowerCase(Locale.ROOT)
                    .matches(".*\\.(mp3|m4a|aac|flac|wav|ogg|opus|webm)$")))
          files.add(DocumentsContract.buildDocumentUriUsingTree(tree, child));
      }
    }
  }

  private void notifications() {
    if (Build.VERSION.SDK_INT >= 33
        && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED)
      requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, 20);
  }

  private void download(Track t) {
    String[] formats = {"mp3", "m4a", "flac", "opus", "wav"};
    new AlertDialog.Builder(this)
        .setTitle("Download format")
        .setItems(
            new String[] {"MP3", "M4A (AAC)", "FLAC", "Opus", "WAV"},
            (d, i) -> {
              String format = formats[i];
              if (format.equals("flac") || format.equals("wav")) queueDownload(t, format, "0");
              else
                new AlertDialog.Builder(this)
                    .setTitle("Audio quality")
                    .setItems(
                        new String[] {"Best available", "128 kbps", "192 kbps", "320 kbps"},
                        (a, j) ->
                            queueDownload(t, format, new String[] {"0", "128K", "192K", "320K"}[j]))
                    .show();
            })
        .show();
  }

  private void queueDownload(Track t, String format, String quality) {
    notifications();
    startForegroundService(
        new Intent(this, DownloadService.class)
            .putExtra("track", t.json().toString())
            .putExtra("format", format)
            .putExtra("quality", quality));
    toast("Download queued. Progress appears in notifications.");
  }

  private void export(Track t) {
    exportTrack = t;
    String ext = t.uri.substring(t.uri.lastIndexOf('.'));
    if (!ext.matches("\\.[a-zA-Z0-9]{1,8}")) ext = ".mp3";
    startActivityForResult(
        new Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("audio/*")
            .putExtra(Intent.EXTRA_TITLE, t.title.replaceAll("[/:*?<>|]", "_") + ext),
        12);
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data) {
    super.onActivityResult(request, result, data);
    if (result != RESULT_OK || data == null) return;
    if (request == 10) {
      List<Uri> files = new ArrayList<>();
      if (data.getClipData() != null)
        for (int i = 0; i < data.getClipData().getItemCount(); i++)
          files.add(data.getClipData().getItemAt(i).getUri());
      else if (data.getData() != null) files.add(data.getData());
      importUris(files);
    } else if (request == 11 && data.getData() != null) importFolder(data.getData());
    else if (request == 12 && data.getData() != null && exportTrack != null) {
      Track t = exportTrack;
      Uri uri = data.getData();
      RiffApplication.IO.execute(
          () -> {
            try (InputStream in = getContentResolver().openInputStream(Uri.parse(t.uri));
                OutputStream out = getContentResolver().openOutputStream(uri)) {
              if (in == null || out == null)
                throw new IllegalStateException("Could not open this file");
              Io.copy(in, out);
              runOnUiThread(() -> toast("Audio exported"));
            } catch (Exception e) {
              runOnUiThread(() -> error("Couldn't export", DownloadService.friendly(e)));
            }
          });
    } else if (request == 13 && data.getData() != null && lrcTrack != null) {
      Track t = lrcTrack;
      Uri uri = data.getData();
      RiffApplication.IO.execute(
          () -> {
            try (InputStream in = getContentResolver().openInputStream(uri)) {
              if (in == null) throw new IllegalStateException("Could not read the lyrics");
              String lrc = new String(Io.read(in), StandardCharsets.UTF_8);
              Lyrics.save(this, t, lrc);
              runOnUiThread(() -> showLyrics(t));
            } catch (Exception e) {
              runOnUiThread(() -> error("Couldn't import lyrics", DownloadService.friendly(e)));
            }
          });
    }
  }

  private void trimDialog(Track t) {
    LinearLayout content = paddedColumn();
    content.addView(
        text(
            "Save a selection as a new MP3. Your original stays in your library.",
            14,
            MUTED,
            false));
    addSpace(content, 18);
    EditText start = input("Start time in seconds", "0"),
        end = input("End time in seconds", String.format(Locale.US, "%.1f", t.duration / 1000.0));
    start.setInputType(8194);
    end.setInputType(8194);
    content.addView(text("Start (seconds)", 12, MUTED, false));
    content.addView(start);
    addSpace(content, 12);
    content.addView(text("End (seconds)", 12, MUTED, false));
    content.addView(end);
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle("Trim audio")
            .setView(content)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save selection", null)
            .create();
    dialog.setOnShowListener(
        v ->
            dialog
                .getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(
                    w -> {
                      double from, to;
                      try {
                        from = Double.parseDouble(start.getText().toString());
                        to = Double.parseDouble(end.getText().toString());
                        if (!Double.isFinite(from)
                            || !Double.isFinite(to)
                            || from < 0
                            || to <= from
                            || (t.duration > 0 && to > t.duration / 1000.0 + .5))
                          throw new IllegalArgumentException();
                      } catch (Exception e) {
                        end.setError("Choose a valid start and end time");
                        return;
                      }
                      dialog.dismiss();
                      toast("Saving your selection…");
                      RiffApplication.IO.execute(
                          () -> {
                            try {
                              Track edited = Editor.trim(this, t, from, to);
                              runOnUiThread(
                                  () -> {
                                    toast("Saved " + edited.title);
                                    if (tab == 0) renderPage();
                                  });
                            } catch (Exception e) {
                              runOnUiThread(
                                  () -> error("Couldn't trim audio", DownloadService.friendly(e)));
                            }
                          });
                    }));
    dialog.show();
  }

  private void showLyrics(Track t) {
    lrcTrack = t;
    if (lyricsDialog != null) lyricsDialog.dismiss();
    lyricsDialog = new Dialog(this);
    LinearLayout content = paddedColumn();
    content.setBackgroundColor(BG);
    LinearLayout top = row();
    top.addView(heading("Lyrics", t.title), new LinearLayout.LayoutParams(0, -2, 1));
    top.addView(iconButton("close", "Close lyrics", TEXT, HIGH, 48, () -> lyricsDialog.dismiss()));
    content.addView(top);
    addSpace(content, 18);
    LinearLayout actions = row();
    actions.addView(
        button(
            "Import LRC",
            false,
            () ->
                startActivityForResult(
                    new Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .setType("*/*")
                        .addCategory(Intent.CATEGORY_OPENABLE),
                    13)),
        new LinearLayout.LayoutParams(0, dp(48), 1));
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(48), 1);
    p.leftMargin = dp(10);
    actions.addView(button("Edit lyrics", false, () -> editLyrics(t)), p);
    content.addView(actions);
    addSpace(content, 10);
    content.addView(button("Time lyrics to this track", false, () -> syncLyrics(t)));
    addSpace(content, 20);
    lyricsScroll = new ScrollView(this);
    LinearLayout lines = column();
    TextView loading = text("Finding lyrics…", 16, MUTED, false);
    lines.addView(loading);
    lyricsScroll.addView(lines);
    content.addView(lyricsScroll, new LinearLayout.LayoutParams(-1, 0, 1));
    lyricLines = new ArrayList<>();
    lyricViews = new ArrayList<>();
    lastLyric = -1;
    lyricsDialog.setContentView(content);
    lyricsDialog.show();
    lyricsDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
    lyricsDialog.getWindow().setLayout(-1, -1);
    Dialog target = lyricsDialog;
    RiffApplication.IO.execute(
        () -> {
          try {
            String value = Lyrics.get(this, t, settings.getBoolean("onlineLyrics", true));
            List<Lyrics.Line> parsed = Lyrics.parse(value);
            runOnUiThread(
                () -> {
                  if (!target.isShowing() || target != lyricsDialog) return;
                  lines.removeAllViews();
                  lyricLines = parsed;
                  lyricViews = new ArrayList<>();
                  if (value.isBlank()) {
                    lines.addView(
                        text(
                            "No lyrics found. Import an LRC file or add your own.",
                            17,
                            MUTED,
                            false));
                    return;
                  }
                  for (Lyrics.Line line : parsed) {
                    TextView label = text(line.text.isEmpty() ? "♪" : line.text, 22, MUTED, true);
                    label.setPadding(0, dp(12), 0, dp(12));
                    if (line.time >= 0)
                      label.setOnClickListener(
                          w -> {
                            Track playing = current();
                            if (playing != null && playing.id.equals(t.id))
                              controller.seekTo(line.time);
                          });
                    lines.addView(label);
                    lyricViews.add(label);
                  }
                  updatePlayback();
                });
          } catch (Exception e) {
            runOnUiThread(
                () -> {
                  if (target.isShowing()) {
                    lines.removeAllViews();
                    lines.addView(
                        text(
                            "Lyrics unavailable. You can still import or edit them.\n\n"
                                + DownloadService.friendly(e),
                            15,
                            MUTED,
                            false));
                  }
                });
          }
        });
  }

  private void editLyrics(Track t) {
    EditText field = input("Plain text or [00:00.00] synced lyrics", "");
    field.setSingleLine(false);
    field.setMinLines(8);
    field.setGravity(Gravity.TOP);
    field.setInputType(131073);
    RiffApplication.IO.execute(
        () -> {
          try {
            String cached = Lyrics.get(this, t, false);
            runOnUiThread(() -> field.setText(cached));
          } catch (Exception ignored) {
          }
        });
    new AlertDialog.Builder(this)
        .setTitle("Your lyrics")
        .setView(dialogContent(field))
        .setNegativeButton("Cancel", null)
        .setPositiveButton(
            "Save",
            (d, w) -> {
              try {
                Lyrics.save(this, t, field.getText().toString());
                showLyrics(t);
              } catch (Exception e) {
                error("Couldn't save lyrics", DownloadService.friendly(e));
              }
            })
        .show();
  }

  private void syncLyrics(Track track) {
    if (current() == null || !current().id.equals(track.id)) {
      toast("Play this track before timing its lyrics");
      return;
    }
    List<Lyrics.Line> lines = new ArrayList<>(lyricLines);
    if (lines.isEmpty()) {
      toast("Add or import your lyrics first");
      return;
    }
    Dialog sync = new Dialog(this);
    LinearLayout content = paddedColumn();
    content.addView(text("Time your lyrics", 24, TEXT, true));
    addSpace(content, 10);
    content.addView(
        text(
            "Play the song, then tap each line as you hear it. Tap again to adjust its time.",
            14,
            MUTED,
            false));
    addSpace(content, 14);
    content.addView(button("Play / pause", false, this::togglePlayback));
    addSpace(content, 14);
    long[] times = new long[lines.size()];
    for (int i = 0; i < lines.size(); i++) {
      int index = i;
      Lyrics.Line line = lines.get(i);
      times[i] = line.time;
      TextView label =
          text((line.time < 0 ? "—:—" : time(line.time)) + "  " + line.text, 16, TEXT, false);
      label.setPadding(dp(10), dp(15), dp(10), dp(15));
      clickable(
          label,
          HIGH,
          12,
          () -> {
            Track playing = current();
            if (playing == null || !playing.id.equals(track.id)) {
              toast("Return to the track you're timing");
              return;
            }
            times[index] = controller.getCurrentPosition();
            label.setText(time(times[index]) + "  " + line.text);
            label.setTextColor(accent);
          });
      content.addView(label);
      addSpace(content, 5);
    }
    addSpace(content, 16);
    content.addView(
        button(
            "Save timing",
            true,
            () -> {
              StringBuilder lrc = new StringBuilder();
              for (int i = 0; i < lines.size(); i++) {
                if (times[i] < 0) {
                  toast("Tap each line before saving");
                  return;
                }
                long value = times[i];
                lrc.append(
                    String.format(
                        Locale.US,
                        "[%02d:%02d.%03d]%s\n",
                        value / 60000,
                        (value / 1000) % 60,
                        value % 1000,
                        lines.get(i).text));
              }
              try {
                Lyrics.save(this, track, lrc.toString());
                sync.dismiss();
                showLyrics(track);
              } catch (Exception e) {
                error("Couldn't save timing", DownloadService.friendly(e));
              }
            }));
    addSpace(content, 10);
    content.addView(button("Cancel", false, sync::dismiss));
    sync.setContentView(scroll(content));
    sync.show();
    sync.getWindow().setBackgroundDrawable(shape(SURFACE, 24));
    sync.getWindow().setLayout(-1, (int) (getResources().getDisplayMetrics().heightPixels * .85));
  }

  private void effectsDialog() {
    LinearLayout content = paddedColumn();
    content.addView(text("Make it sound like you", 22, TEXT, true));
    addSpace(content, 8);
    content.addView(
        text(
            "Effects apply during playback. Available effects depend on your device.",
            13,
            MUTED,
            false));
    addSpace(content, 20);
    slider(content, "Speed", "speed", .5f, 2f, settings.getFloat("speed", 1f), 100, false);
    slider(content, "Pitch", "pitch", .5f, 2f, settings.getFloat("pitch", 1f), 100, false);
    Switch equalizer = new Switch(this);
    equalizer.setText("Equalizer");
    equalizer.setTextColor(TEXT);
    equalizer.setChecked(settings.getBoolean("eq", false));
    equalizer.setOnCheckedChangeListener(
        (v, checked) -> {
          settings.edit().putBoolean("eq", checked).apply();
          effects();
        });
    content.addView(equalizer, size(-1, 50));
    String[] bands = {"60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz"};
    for (int i = 0; i < 5; i++)
      slider(
          content, bands[i], "band" + i, -1500, 1500, settings.getInt("band" + i, 0), 3000, true);
    slider(content, "Bass boost", "bass", 0, 1000, settings.getInt("bass", 0), 1000, true);
    slider(content, "Echo", "echo", 0, 1000, settings.getInt("echo", 0), 1000, true);
    slider(
        content, "Distortion", "distortion", 0, 1000, settings.getInt("distortion", 0), 1000, true);
    TextView reverb = text("Reverb", 15, TEXT, true);
    content.addView(reverb);
    addSpace(content, 10);
    chips(
        content,
        new String[] {"Off", "Room", "Hall"},
        settings.getInt("reverb", 0) == 0 ? 0 : settings.getInt("reverb", 0) == 1 ? 1 : 2,
        i -> {
          settings.edit().putInt("reverb", i == 0 ? 0 : i == 1 ? 1 : 5).apply();
          effects();
        });
    addSpace(content, 20);
    Dialog dialog = new Dialog(this);
    content.addView(
        button(
            "Reset effects",
            false,
            () -> {
              SharedPreferences.Editor edit =
                  settings
                      .edit()
                      .putFloat("speed", 1f)
                      .putFloat("pitch", 1f)
                      .putBoolean("eq", false)
                      .putInt("bass", 0)
                      .putInt("echo", 0)
                      .putInt("distortion", 0)
                      .putInt("reverb", 0);
              for (int i = 0; i < 10; i++) edit.putInt("band" + i, 0);
              edit.apply();
              effects();
              dialog.dismiss();
              effectsDialog();
            }));
    addSpace(content, 10);
    content.addView(button("Done", true, dialog::dismiss));
    dialog.setContentView(scroll(content));
    dialog.show();
    dialog.getWindow().setBackgroundDrawable(shape(SURFACE, 26));
    dialog.getWindow().setLayout(-1, (int) (getResources().getDisplayMetrics().heightPixels * .86));
  }

  private void slider(
      LinearLayout content,
      String title,
      String key,
      float min,
      float max,
      float initial,
      int steps,
      boolean integer) {
    TextView label = text(title + "  ·  " + effectValue(key, initial), 14, TEXT, false);
    content.addView(label);
    SeekBar seek = new SeekBar(this);
    seek.setMax(steps);
    seek.setProgress(Math.round((initial - min) / (max - min) * steps));
    seek.setProgressTintList(ColorStateList.valueOf(accent));
    seek.setThumbTintList(ColorStateList.valueOf(accent));
    seek.setOnSeekBarChangeListener(
        new SeekBar.OnSeekBarChangeListener() {
          @Override
          public void onStartTrackingTouch(SeekBar v) {}

          @Override
          public void onStopTrackingTouch(SeekBar v) {
            effects();
          }

          @Override
          public void onProgressChanged(SeekBar v, int progress, boolean user) {
            if (!user) return;
            float value = min + (max - min) * progress / steps;
            label.setText(title + "  ·  " + effectValue(key, value));
            if (integer) settings.edit().putInt(key, Math.round(value)).apply();
            else settings.edit().putFloat(key, value).apply();
          }
        });
    content.addView(seek, size(-1, 42));
    addSpace(content, 8);
  }

  private String effectValue(String key, float value) {
    if (key.startsWith("band")) return String.format(Locale.US, "%+.1f dB", value / 100);
    if (key.equals("bass") || key.equals("echo") || key.equals("distortion"))
      return Math.round(value / 10) + "%";
    return String.format(Locale.US, "%.2f×", value);
  }

  private void effects() {
    if (controller != null)
      controller.sendCustomCommand(
          new SessionCommand(PlaybackService.EFFECTS, Bundle.EMPTY), Bundle.EMPTY);
  }

  private void renderSettings() {
    LinearLayout page = paddedColumn();
    page.addView(heading("Just the way you like it", "Settings"));
    addSpace(page, 26);
    settingsCard(
        page, "Sound effects", "Speed, pitch, equalizer, bass and reverb", this::effectsDialog);
    addSpace(page, 12);
    LinearLayout lyrics = column();
    lyrics.setPadding(dp(18), dp(12), dp(18), dp(12));
    rounded(lyrics, SURFACE, 22);
    Switch toggle = new Switch(this);
    toggle.setText("Online lyrics");
    toggle.setTextColor(TEXT);
    toggle.setTextSize(16);
    toggle.setChecked(settings.getBoolean("onlineLyrics", true));
    toggle.setOnCheckedChangeListener(
        (v, value) -> settings.edit().putBoolean("onlineLyrics", value).apply());
    lyrics.addView(toggle, size(-1, 48));
    lyrics.addView(text("Find and cache synced lyrics from LRCLIB.", 12, MUTED, false));
    page.addView(lyrics);
    addSpace(page, 22);
    page.addView(text("Make it your color", 17, TEXT, true));
    addSpace(page, 12);
    LinearLayout swatches = row();
    int[] colors = {0xffd0bcff, 0xffa8dab5, 0xffffb59e, 0xffb5d4ff};
    for (int color : colors) {
      FrameLayout swatch = new FrameLayout(this);
      rounded(swatch, color, 26);
      if (color == accent)
        swatch.addView(
            new Icon(this, "check", 0xff271b43),
            new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
      swatch.setContentDescription("Accent color " + Integer.toHexString(color));
      clickable(
          swatch,
          color,
          26,
          () -> {
            accent = color;
            settings.edit().putInt("accent", color).apply();
            setup();
            updatePlayback();
          });
      LinearLayout.LayoutParams p = size(52, 52);
      p.rightMargin = dp(14);
      swatches.addView(swatch, p);
    }
    page.addView(swatches);
    addSpace(page, 24);
    settingsCard(
        page,
        "Download engine",
        "Update yt-dlp when online sources change",
        () -> {
          toast("Updating download engine…");
          RiffApplication.IO.execute(
              () -> {
                try {
                  Online.init(this);
                  YoutubeDL.getInstance().updateYoutubeDL(this, YoutubeDL.UpdateChannel._STABLE);
                  runOnUiThread(() -> toast("Download engine is up to date"));
                } catch (Exception e) {
                  runOnUiThread(
                      () -> error("Couldn't update the engine", DownloadService.friendly(e)));
                }
              });
        });
    addSpace(page, 12);
    settingsCard(
        page,
        "Sleep timer",
        "Pause your music after a little while",
        () ->
            new AlertDialog.Builder(this)
                .setTitle("Sleep timer")
                .setItems(
                    new String[] {"Off", "15 minutes", "30 minutes", "60 minutes"},
                    (d, i) -> {
                      settings
                          .edit()
                          .putLong(
                              "sleepUntil",
                              i == 0
                                  ? 0
                                  : System.currentTimeMillis()
                                      + new int[] {0, 15, 30, 60}[i] * 60000L)
                          .apply();
                      effects();
                      toast(i == 0 ? "Sleep timer off" : "Sleep timer set");
                    })
                .show());
    addSpace(page, 12);
    settingsCard(
        page,
        "Storage",
        library.offline().size() + " offline tracks · private app storage",
        () ->
            new AlertDialog.Builder(this)
                .setTitle("Your music stays with you")
                .setMessage(
                    "Imported and downloaded audio lives in Riff's private storage. Use a track's"
                        + " Export audio action to save a copy outside the app. Uninstalling Riff"
                        + " removes its private library, playlists, and lyrics.")
                .setPositiveButton("Got it", null)
                .show());
    addSpace(page, 12);
    settingsCard(
        page,
        "About Riff",
        "1.0.0 Android · based on rootscripts/riff",
        () ->
            new AlertDialog.Builder(this)
                .setTitle("Riff for Android")
                .setMessage(
                    "Made for humans. A native Android port of rootscripts/riff.\n\n"
                        + "Playback: AndroidX Media3\n"
                        + "Search/downloads: yt-dlp and FFmpeg via youtubedl-android\n"
                        + "Lyrics: LRCLIB\n\n"
                        + "Riff desktop: MIT © rootscripts\n"
                        + "Android port: GPL-3.0-or-later\n"
                        + "Media3: Apache-2.0\n"
                        + "youtubedl-android and FFmpeg distribution: GPL-3.0\n\n"
                        + "Desktop Discord RPC and desktop updating are unavailable on Android.")
                .setNeutralButton(
                    "Project",
                    (d, w) ->
                        startActivity(
                            new Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://github.com/rootscripts/riff"))))
                .setNegativeButton("Licenses", (d, w) -> showLicenses())
                .setPositiveButton("Done", null)
                .show());
    downloadStatus = text("", 12, accent, false);
    page.addView(downloadStatus);
    addSpace(page, 30);
    TextView footer = text("RIFF\nYour music, wherever you go.", 12, MUTED, false);
    footer.setGravity(Gravity.CENTER);
    footer.setLineSpacing(dp(6), 1);
    page.addView(footer);
    body.addView(scroll(page));
  }

  private void settingsCard(LinearLayout target, String title, String subtitle, Runnable action) {
    LinearLayout card = column();
    card.setPadding(dp(18), dp(18), dp(18), dp(18));
    card.addView(text(title, 16, TEXT, true));
    addSpace(card, 5);
    card.addView(text(subtitle, 12, MUTED, false));
    clickable(card, SURFACE, 22, action);
    target.addView(card, size(-1, -2));
  }

  private void toast(String message) {
    if (!isDestroyed()) Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
  }

  private void showLicenses() {
    try {
      StringBuilder value = new StringBuilder();
      for (String name : new String[] {"NOTICES.txt", "Riff-MIT.txt", "GPL-3.0.txt"}) {
        try (InputStream in = getAssets().open("licenses/" + name)) {
          value.append(new String(Io.read(in), StandardCharsets.UTF_8)).append("\n\n");
        }
      }
      TextView notice = text(value.toString(), 12, MUTED, false);
      notice.setTextIsSelectable(true);
      notice.setPadding(dp(20), dp(16), dp(20), dp(16));
      new AlertDialog.Builder(this)
          .setTitle("Open-source licenses")
          .setView(scroll(notice))
          .setPositiveButton("Done", null)
          .show();
    } catch (Exception e) {
      error("Licenses unavailable", DownloadService.friendly(e));
    }
  }

  private void error(String title, String message) {
    if (!isDestroyed() && !isFinishing())
      new AlertDialog.Builder(this)
          .setTitle(title)
          .setMessage(message)
          .setPositiveButton("OK", null)
          .show();
  }

  @Override
  public void onBackPressed() {
    if (selectedPlaylist != null || selectedAlbum != null) {
      selectedPlaylist = null;
      selectedAlbum = null;
      renderPage();
    } else if (tab != 0) {
      tab = 0;
      renderPage();
    } else super.onBackPressed();
  }
}
