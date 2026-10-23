package br.casa.sala;

import android.animation.LayoutTransition;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.location.Location;
import android.location.LocationManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    /** Tint of the gradients over the artwork, by period of the day (manhã, tarde, entardecer, noite). */
    private static final int[] TINT = {0x0F1B2E, 0x101A2C, 0x22152E, 0x05070B};
    private static final int[][] WEATHER_PALETTE = {
            {0xF05683B8, 0xF02E5B89, 0xF01C3C66, 0x667FC4FF},
            {0xF04A79B8, 0xF0244E84, 0xF0173561, 0x667FC4FF},
            {0xF0715D9C, 0xF04D3D79, 0xF02B2A55, 0x779E8CFF},
            {0xF03A5285, 0xF01F355F, 0xF011203F, 0x668AAEFF}
    };
    private static final int WHITE = 0xFFFFFFFF, SOFT = 0xCCFFFFFF, DIM = 0x99FFFFFF;

    private static final Set<String> HIDDEN_BY_DEFAULT = new HashSet<>(Arrays.asList(
            "com.tcl.guard", "com.tcl.ocean.instructions", "com.tcl.magiconnectfree", "com.tcl.messagebox",
            "com.amlogic.airplay", "com.tcl.exhibit", "com.tcl.esticker", "com.tcl.repairguide", "com.tcl.usercenter",
            "com.tcl.hotelmenu", "com.tcl.logkit", "com.tcl.miracast", "com.google.android.play.games",
            "com.tcl.waterfall.overseas", "com.google.android.apps.tv.launcherx"));

    private static final int HOME = 0, MOVIES = 1, SERIES = 2, APPS = 3, SEARCH = 4;
    /** How much of "Seus apps" hangs below the screen until you move down to it. */
    private static final int PEEK_HIDDEN = 88;
    /** Height of the title area; sized so "Para você" is whole and the next row peeks at the bottom edge. */
    private static final int HERO_H = 196;
    /** Side margin for text and the first card; rows themselves run to the screen edges. */
    private static final int SIDE = 40;
    /** Space inside the grid viewport for focused cards to grow without being clipped. */
    private static final int GRID_SAFE_INSET = 28;
    private boolean appsMode;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService bg = Executors.newSingleThreadExecutor();
    private SharedPreferences prefs;
    private float dp;
    private int period;
    private int page = HOME;

    private ImageView heroA, heroB;
    private boolean heroOnA = true;
    private Object heroKey;
    private View scrimLeft, scrimBottom, scrimFull;
    private LinearLayout homePage, heroInfo, dots;
    private TextView heroProvider, heroTitle, heroMeta, heroOverview;
    private ImageView heroLogo;
    private TextView forYouLabel, appsLabel, histLabel;
    private HorizontalScrollView forYouScroll, appsScroll, histScroll, continueScroll, watchlistScroll;
    private HorizontalScrollView cinemaScroll;
    private LinearLayout continueRow, frequentRow, continueSplit;
    private HorizontalScrollView frequentScroll;
    private TextView frequentLabel;
    private TextView continueLabel;
    private LinearLayout watchlistRow;
    private TextView watchlistLabel;
    private LinearLayout forYouRow, appsRow, histRow;
    private ScrollView gridPage;
    private LinearLayout grid;
    private final Map<Integer, LinearLayout> cachedGrids = new HashMap<>();
    private final Map<Integer, Integer> cachedGridScroll = new HashMap<>();
    private final Set<Integer> builtGridPages = new HashSet<>();
    private int activeGridPage = -1;
    private final LinkedHashMap<String, Bitmap> artworkCache = new LinkedHashMap<>(48, 0.75f, true);
    private int artworkCacheBytes;
    private static final int ARTWORK_CACHE_MAX_BYTES = 32 * 1024 * 1024;
    private TextView[] tabs;
    private TextClock clock;
    private TextView searchButton;
    private GradientDrawable topShade;
    private ImageView avatarView;
    private LinearLayout mainContent;
    private Profiles.Profile profile;
    private FrameLayout layer;
    private Magnifier magnifier;
    private LinearLayout searchPage, searchResults;
    private android.widget.EditText searchBox;
    private static final int VOICE_SEARCH_REQUEST = 305;
    private String lastQuery = "";
    private final Runnable searchLater = () -> runSearch(searchBox.getText().toString());
    private TextView weather;
    private TextView weatherLabel, cinemaLabel;
    private LinearLayout weatherRow, cinemaRow;
    private FrameLayout weatherCard;
    private View weatherDivider;
    private FrameLayout avatarFocusShell;
    private List<Cinema.Movie> cinemaMovies = new ArrayList<>();
    private TextView reminderLabel;
    private LinearLayout reminderRow;
    private HorizontalScrollView reminderScroll;
    private static final int LOCATION_REQUEST = 304;

    private List<App> apps = new ArrayList<>();
    private Map<String, App> byPkg = new HashMap<>();
    private List<Tmdb.Pick> picks = new ArrayList<>();
    private static final int TITLE_PAGE_SIZE = 16;
    private int movieVisibleCount = TITLE_PAGE_SIZE;
    private int seriesVisibleCount = TITLE_PAGE_SIZE;
    private boolean managingApps;
    private boolean enteringAppManager;
    private View appManagerBack, appManagerFirst, appManagerFirstRow;
    private FrameLayout rootView;
    private FrameLayout sheet;
    private View sheetReturn;
    private String moving;
    private TextView moveHint;





    /** Rebuild the grid without throwing the focus back to the top. */



    private List<Tmdb.Pick> heroPicks = new ArrayList<>();
    private int heroIndex;
    private boolean heroLocked;
    private String histPkg;
    private boolean firstRender = true;
    private View lastFocus;

    private boolean isTab(View v) {
        if (v == null || tabs == null) return false;
        if (v == searchButton) return true;
        for (TextView t : tabs) if (t == v) return true;
        return false;
    }

    static final class App {
        String pkg, label;
        Intent intent;
        Drawable banner, icon;
        boolean system;
        int launches;
        long last, pinned;
    }

    private static final class Oval extends ViewOutlineProvider {
        @Override
        public void getOutline(View v, Outline o) {
            o.setOval(0, 0, v.getWidth(), v.getHeight());
        }
    }

    private static final class Round extends ViewOutlineProvider {
        private final int r;
        Round(int r) { this.r = r; }
        @Override
        public void getOutline(View v, Outline o) {
            o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r);
        }
    }


    private final Runnable tick = this::onTick;
    private final Runnable rotate = this::onRotate;

    private void onTick() {
        int p = Sun.period(Calendar.getInstance());
        if (p != period) {
            period = p;
            applyTint();
            applyWeatherPalette();
        }
        long sleepAt = prefs.getLong("sleepAt", 0);
        if (sleepAt > 0 && sleepAt <= System.currentTimeMillis() && !prefs.getBoolean("sleepNotified", false)) {
            prefs.edit().putBoolean("sleepNotified", true).apply();
            showSheet("Hora de dormir", null, "O temporizador terminou. A TV pode ser colocada em espera pelo controle remoto ou pelas configurações do sistema.",
                    new String[]{"Fechar", "Configurações da TV"}, choice -> {
                        if (choice == 0) prefs.edit().remove("sleepAt").apply();
                        else open(new Intent(Settings.ACTION_SETTINGS));
                    });
        }
        checkCinemaReminders();
        ui.postDelayed(tick, 60_000);
    }

    private void onRotate() {
        if (!heroLocked && !heroPicks.isEmpty() && page == HOME && histPkg == null) {
            heroIndex = (heroIndex + 1) % heroPicks.size();
            showPick(heroPicks.get(heroIndex));
        }
        ui.postDelayed(rotate, 8000);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        profile = Profiles.current(this);
        prefs = getSharedPreferences("sala" + Profiles.suffix(this), MODE_PRIVATE);
        dp = getResources().getDisplayMetrics().density;
        period = Sun.period(Calendar.getInstance());
        setContentView(build());
        getWindow().getDecorView().getViewTreeObserver().addOnGlobalFocusChangeListener((o, n) -> {
            lastFocus = n;
            if (n == null) return;
            if (histPkg != null && !isInside(n, histRow) && !isInside(n, appsRow)) hideHistory(false);
            if (!isInside(n, forYouRow)) heroLocked = false;
            if (histPkg == null) setRowFocus(n);
        });
        applyTint();
        if (profile == null) ui.post(() -> editProfile(null, true));
        else ui.post(this::askLocationIfNeeded);
        applyProfile();
    }

    private int kidsAge() {
        return profile != null && profile.kids ? profile.age : 0;
    }

    /** Age limits apply only to Kids profiles. */
    private int recommendationAge() {
        return kidsAge();
    }

    /** Apply profile UI and avatar. */
    private void applyProfile() {
        int age = kidsAge();
        avatarView.setImageBitmap(Profiles.avatar(profile == null ? 0 : profile.avatar, px(88)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // HOME may bring this existing launcher task forward without creating it again.
        if (page != HOME || histPkg != null) returnToHome(true);
        ui.removeCallbacks(tick);
        ui.removeCallbacks(rotate);
        onTick();
        ui.postDelayed(rotate, 8000);
        reload();
        bg.execute(() -> {
            runOnUiThread(() -> {
                if (!isFinishing() && page == HOME && histPkg == null) renderContinueRow();
            });
        });
    }

    /** Installing or removing an app updates the screen (and its TMDB content) right away. */
    private static final class PackageWatch extends android.content.BroadcastReceiver {
        private final Runnable onChange;
        PackageWatch(Runnable onChange) { this.onChange = onChange; }
        @Override
        public void onReceive(android.content.Context c, Intent i) {
            onChange.run();
        }
    }

    private static final class MediaUpdateWatch extends android.content.BroadcastReceiver {
        private final MainActivity owner;
        MediaUpdateWatch(MainActivity owner) { this.owner = owner; }
        @Override public void onReceive(android.content.Context context, Intent intent) {
            if (owner.page == HOME && owner.histPkg == null && owner.layer == null && owner.sheet == null) {
                owner.renderContinueRow();
            }
        }
    }

    private final PackageWatch packages = new PackageWatch(this::reload);
    private final MediaUpdateWatch mediaUpdates = new MediaUpdateWatch(this);

    @Override
    protected void onStart() {
        super.onStart();
        android.content.IntentFilter f = new android.content.IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(packages, f);
        android.content.IntentFilter mediaFilter = new android.content.IntentFilter(History.ACTION_UPDATED);
        if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(mediaUpdates, mediaFilter, RECEIVER_NOT_EXPORTED);
        else registerReceiver(mediaUpdates, mediaFilter);
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(packages);
        unregisterReceiver(mediaUpdates);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(tick);
        ui.removeCallbacks(rotate);
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        returnToHome(true);
    }

    /** Return to the first launcher page and restore its initial scroll position. */
    private void returnToHome(boolean focus) {
        hideHistory(false);
        showPage(HOME);
        if (forYouScroll != null) forYouScroll.scrollTo(0, 0);
        if (homePage != null) {
            homePage.animate().cancel();
            homePage.setTranslationY(0);
        }
        if (heroInfo != null) heroInfo.setAlpha(1f);
        if (scrimFull != null) scrimFull.animate().alpha(0f).setDuration(180).start();
        if (focus) focusDefault();
    }

    @Override
    public void onBackPressed() {
        if (histPkg != null) hideHistory(true);
        else if (page == SEARCH) {
            showPage(HOME);
            tabs[HOME].requestFocus();
        } else if (page != HOME) {
            showPage(HOME);
            tabs[HOME].requestFocus();
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getDeviceId() >= 0
                && (e.getKeyCode() >= 400 || e.getScanCode() >= 400)) {
            android.util.Log.i("SalaRemote", "keyCode=" + e.getKeyCode() + " scanCode=" + e.getScanCode()
                    + " device=" + e.getDeviceId());
        }
        if (moving != null) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                int k = e.getKeyCode();
                if (k == KeyEvent.KEYCODE_DPAD_LEFT) moveBy(-1);
                else if (k == KeyEvent.KEYCODE_DPAD_RIGHT) moveBy(1);
                else if (k == KeyEvent.KEYCODE_DPAD_UP) moveBy(-appsPerLine());
                else if (k == KeyEvent.KEYCODE_DPAD_DOWN) moveBy(appsPerLine());
                else if (k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_BACK) endMove();
            }
            return true;
        }
        if (layer != null && sheet == null && e.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (e.getAction() == KeyEvent.ACTION_UP && profile != null) closeLayer();
            return true;
        }
        if (layer != null && sheet == null) return super.dispatchKeyEvent(e);
        if (sheet != null) {
            if (e.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                if (e.getAction() == KeyEvent.ACTION_UP) closeSheet(true);
                return true;
            }
            return super.dispatchKeyEvent(e);
        }
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            if (e.getKeyCode() == 204) {
                returnToHome(true);
                return true;
            }
            if (e.getKeyCode() == 456) {
                menu();
                return true;
            }
            // The TCL remote's physical Settings key arrives as a private keycode plus Linux MENU scancode.
            // Branded keys arrive as private Android keycodes (such as 4065) while the scan code
            // carries TCL's app mapping (such as 569 for YouTube). Keep older firmware mappings too.
            if (handleTclAppShortcut(e.getScanCode())) return true;
            if (e.getKeyCode() != e.getScanCode() && handleTclAppShortcut(e.getKeyCode())) return true;
        }
        if (page == APPS && managingApps && e.getAction() == KeyEvent.ACTION_DOWN) {
            View cur = getCurrentFocus();
            if (e.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN && cur == appManagerBack
                    && appManagerFirst != null) {
                appManagerFirst.requestFocus();
                return true;
            }
            if (e.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP && cur != null
                    && (cur == appManagerFirst || (appManagerFirstRow != null && isInside(cur, appManagerFirstRow)))) {
                appManagerBack.requestFocus();
                return true;
            }
        }
        if (e.getAction() == KeyEvent.ACTION_DOWN
                && (e.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP || e.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN)
                && page != HOME && page != SEARCH) {
            View cur = getCurrentFocus();
            if (cur != null && grid != null && isInside(cur, grid)) {
                int direction = e.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP
                        ? View.FOCUS_UP : View.FOCUS_DOWN;
                View next = android.view.FocusFinder.getInstance().findNextFocus(grid, cur, direction);
                if (next != null && next.requestFocus(direction)) return true;
                if (direction == View.FOCUS_UP) {
                    if (gridPage.getScrollY() > px(1)) {
                        gridPage.smoothScrollBy(0, -Math.max(px(120), gridPage.getHeight() / 2));
                        return true;
                    }
                    if (isAtPageStart(cur)) {
                        // The menu is reachable only from the first content row.
                        (page < tabs.length ? tabs[page] : searchButton).requestFocus();
                    }
                    return true;
                }
                // Do not let a DOWN press at the last card wrap to the tabs or another row.
                return true;
            }
        }
        if (page == HOME && e.getAction() == KeyEvent.ACTION_DOWN && homeVerticalNav(e.getKeyCode())) return true;
        return super.dispatchKeyEvent(e);
    }

    /** Explicit vertical moves between "Para você" and the apps/continue strip, so no press is swallowed. */
    private boolean homeVerticalNav(int code) {
        if (code != KeyEvent.KEYCODE_DPAD_DOWN && code != KeyEvent.KEYCODE_DPAD_UP) return false;
        View cur = getCurrentFocus();
        if (cur == null || continueSplit == null || continueSplit.getVisibility() != View.VISIBLE) return false;
        if (code == KeyEvent.KEYCODE_DPAD_DOWN && isInside(cur, forYouScroll)) {
            View target = firstFocusableContent(continueSplit);
            if (target != null && target.requestFocus()) return true;
            return focusFirstHomeSectionBelowContinue();
        }
        if (code == KeyEvent.KEYCODE_DPAD_UP && isInside(cur, continueSplit)) {
            View target = firstFocusableContent(forYouScroll);
            return target != null && target.requestFocus();
        }
        return false;
    }

    private boolean isAtPageStart(View current) {
        if (page == HOME) {
            if (homePage.getTranslationY() < -px(1) || !isInside(current, forYouScroll)) return false;
            View first = firstFocusableContent(forYouScroll);
            return first == null || (first.getParent() instanceof View
                    && isInside(current, (View) first.getParent()));
        }
        if (page == SEARCH) {
            View first = firstFocusableContent(searchPage);
            return first == null || current == first || (first.getParent() instanceof View
                    && isInside(current, (View) first.getParent()));
        }
        if (gridPage.getScrollY() > px(1)) return false;
        View first = firstFocusableContent(grid);
        return first == null || current == first || (first.getParent() instanceof View
                && isInside(current, (View) first.getParent()));
    }

    /** TCL firmware maps the remote's branded app keys to private keycodes from Generic.kl. */
    private boolean handleTclAppShortcut(int keyCode) {
        String[] labels;
        String[] packages = new String[0];
        switch (keyCode) {
            case 472: labels = new String[]{"netflix"}; packages = new String[]{"com.netflix.ninja"}; break;
            case 473: labels = new String[]{"prime video", "amazon prime", "prime"}; packages = new String[]{"com.amazon.amazonvideo.livingroom"}; break;
            case 569: labels = new String[]{"youtube"}; packages = new String[]{"com.google.android.youtube.tv"}; break;
            case 586: labels = new String[]{"globoplay", "globo play"}; packages = new String[]{"com.globo.globotv"}; break;
            case 762: labels = new String[]{"disney+", "disney plus"}; packages = new String[]{"com.disney.disneyplus"}; break;
            case 747: labels = new String[]{"wetv", "we tv"}; break;
            case 759: labels = new String[]{"iqiyi"}; packages = new String[]{"com.iqiyi.i18n.tv"}; break;
            case 652: labels = new String[]{"apple tv"}; packages = new String[]{"com.apple.atve.androidtv.appletv"}; break;
            case 649: labels = new String[]{"pluto tv", "pluto"}; break;
            case 751: labels = new String[]{"okko"}; break;
            case 755: labels = new String[]{"fpt play", "fptplayer"}; break;
            case 654: labels = new String[]{"stan"}; break;
            case 743: labels = new String[]{"vidio"}; break;
            case 646: labels = new String[]{"wink"}; break;
            case 640: labels = new String[]{"vix"}; break;
            case 641: labels = new String[]{"sonyliv", "sony liv"}; break;
            case 642: labels = new String[]{"vtvgo", "vtv go"}; break;
            case 753: labels = new String[]{"tot"}; break;
            case 746: labels = new String[]{"ivi"}; break;
            case 651: labels = new String[]{"browsehere", "browser"}; break;
            case 655: labels = new String[]{"galaview", "gala view"}; break;
            case 763: labels = new String[]{"freeview play", "freeview"}; break;
            default: return false;
        }

        for (App app : apps) {
            String pkg = app.pkg.toLowerCase(Locale.ROOT);
            String label = app.label.toLowerCase(Locale.ROOT);
            for (String target : packages) if (pkg.equals(target)) {
                android.util.Log.i("SalaRemote", "Abrindo atalho " + target + " pelo pacote instalado");
                launch(app);
                return true;
            }
            for (String target : labels) if (label.contains(target)) {
                android.util.Log.i("SalaRemote", "Abrindo atalho pelo app " + app.pkg);
                launch(app);
                return true;
            }
        }

        // Apps may still be loading when the user presses a shortcut after startup.
        for (String pkg : packages) {
            PackageManager pm = getPackageManager();
            Intent intent = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (intent == null) intent = pm.getLaunchIntentForPackage(pkg);
            if (intent != null) {
                android.util.Log.i("SalaRemote", "Abrindo atalho " + pkg + " pelo PackageManager");
                prefs.edit().putInt("n:" + pkg, prefs.getInt("n:" + pkg, 0) + 1)
                        .putLong("t:" + pkg, System.currentTimeMillis()).apply();
                open(intent);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_MENU) {
            menu();
            return true;
        }
        return super.onKeyDown(code, e);
    }

    private int px(float v) { return Math.round(v * dp); }

    private static boolean isInside(View v, View parent) {
        for (Object p = v; p instanceof View; p = ((View) p).getParent()) if (p == parent) return true;
        return false;
    }

    // ------------------------------------------------------------------ layout

    private View build() {
        FrameLayout root = new FrameLayout(this);
        rootView = root;
        root.setBackgroundColor(Color.BLACK);
        heroA = heroImage();
        heroB = heroImage();
        heroB.setAlpha(0f);
        root.addView(heroA, new FrameLayout.LayoutParams(-1, -1));
        root.addView(heroB, new FrameLayout.LayoutParams(-1, -1));
        scrimLeft = new View(this);
        root.addView(scrimLeft, new FrameLayout.LayoutParams(-1, -1));
        scrimBottom = new View(this);
        root.addView(scrimBottom, new FrameLayout.LayoutParams(-1, -1));
        scrimFull = new View(this);
        scrimFull.setAlpha(0f);
        root.addView(scrimFull, new FrameLayout.LayoutParams(-1, -1));

        // Safe area: TVs crop the edges (overscan), so nothing important goes in the outer 5%.
        LinearLayout content = new LinearLayout(this);
        mainContent = content;
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(px(SIDE), px(30), px(SIDE), px(40));
        content.setClipChildren(false);
        content.setClipToPadding(false);
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout nav = new LinearLayout(this);
        nav.setPadding(px(5), px(5), px(5), px(5));
        // Keep artwork and cards from visually bleeding through the navigation bar.
        nav.setBackground(pill(0xE51A2A47, 0x55FFFFFF, px(24)));
        String[] names = {"Início", "Filmes", "Séries", "Apps"};
        tabs = new TextView[names.length];
        for (int i = 0; i < names.length; i++) {
            tabs[i] = tab(names[i], i);
            if (!Tmdb.enabled() && (i == MOVIES || i == SERIES)) tabs[i].setVisibility(View.GONE);
            nav.addView(tabs[i], lp(0, 0, i < names.length - 1 ? px(2) : 0, 0));
        }
        searchButton = new TextView(this);
        searchButton.setFocusable(true);
        searchButton.setClickable(true);
        magnifier = new Magnifier(px(2));
        searchButton.setForeground(magnifier);
        searchButton.setOnClickListener(v -> showSearch());
        searchButton.setOnFocusChangeListener((v, has) -> styleTabs());
        // The magnifier lives inside the menu pill, before "Início".
        nav.addView(searchButton, 0, new LinearLayout.LayoutParams(px(46), px(36)));
        ((LinearLayout.LayoutParams) searchButton.getLayoutParams()).setMargins(0, 0, px(2), 0);
        nav.setGravity(Gravity.CENTER_VERTICAL);
        avatarFocusShell = new FrameLayout(this);
        avatarFocusShell.setFocusable(true);
        avatarFocusShell.setClickable(true);
        avatarFocusShell.setDefaultFocusHighlightEnabled(false);
        avatarFocusShell.setOutlineProvider(new Oval());
        avatarFocusShell.setClipToOutline(true);
        avatarView = new ImageView(this);
        avatarView.setFocusable(false);
        avatarView.setClickable(false);
        avatarView.setDefaultFocusHighlightEnabled(false);
        avatarView.setOutlineProvider(new Oval());
        avatarView.setClipToOutline(true);
        avatarFocusShell.setOnClickListener(v -> pickProfile());
        avatarFocusShell.setOnFocusChangeListener((v, has) -> {
            v.animate().scaleX(has ? 1.12f : 1f).scaleY(has ? 1.12f : 1f).setDuration(150).start();
            GradientDrawable ring = new GradientDrawable();
            ring.setShape(GradientDrawable.OVAL);
            ring.setStroke(px(3), WHITE);
            v.setForeground(has ? new android.graphics.drawable.InsetDrawable(ring, px(1)) : null);
        });
        avatarFocusShell.addView(avatarView, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout avatarSlot = new FrameLayout(this);
        avatarSlot.setClipChildren(false);
        avatarSlot.setClipToPadding(false);
        avatarSlot.addView(avatarFocusShell, new FrameLayout.LayoutParams(px(38), px(38), Gravity.CENTER));
        top.addView(avatarSlot, lp(0, 0, px(14), 0));
        avatarSlot.getLayoutParams().width = px(46);
        avatarSlot.getLayoutParams().height = px(46);
        top.addView(nav);
        View spacer = new View(this);
        top.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        clock = new TextClock(this);
        clock.setFormat12Hour("HH:mm");
        clock.setFormat24Hour("HH:mm");
        clock.setTextSize(20);
        clock.setTextColor(WHITE);
        clock.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        weather = text("", 18, true);
        weather.setTextColor(WHITE);
        weather.setFocusable(true);
        weather.setOnClickListener(v -> weatherMenu());
        top.addView(weather, lp(0, 0, px(14), 0));
        top.addView(clock, lp(0, 0, px(16), 0));
        TextView gear = text("⚙", 18, true);
        gear.setGravity(Gravity.CENTER);
        gear.setWidth(px(38));
        gear.setHeight(px(38));
        gear.setFocusable(true);
        gear.setTextColor(WHITE);
        gear.setBackground(pill(0x33FFFFFF, 0x22FFFFFF, px(19)));
        gear.setOnClickListener(v -> open(new Intent(Settings.ACTION_SETTINGS)));
        gear.setOnFocusChangeListener((v, has) -> {
            gear.setBackground(has ? pill(WHITE, WHITE, px(19)) : pill(0x33FFFFFF, 0x22FFFFFF, px(19)));
            gear.setTextColor(has ? Color.BLACK : WHITE);
        });
        top.addView(gear);
        // The bar stays above the rows when they slide up, over a shade that appears only then.
        topShade = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFA05070B, 0xF505070B, 0x0005070B});
        topShade.setAlpha(0);
        top.setBackground(topShade);
        top.setElevation(px(10));
        top.setPadding(px(SIDE), px(30), px(SIDE), px(34));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-1, -2);
        // Keep the page below the full toolbar bounds; the previous negative bottom margin
        // let the first content row slide underneath the elevated navigation bar.
        tlp.setMargins(-px(SIDE), -px(30), -px(SIDE), 0);
        content.addView(top, tlp);

        // Início
        homePage = new LinearLayout(this);
        homePage.setOrientation(LinearLayout.VERTICAL);
        homePage.setClipChildren(false);
        LayoutTransition lt = new LayoutTransition();
        lt.setDuration(200);
        homePage.setLayoutTransition(lt);

        heroInfo = new LinearLayout(this);
        heroInfo.setOrientation(LinearLayout.VERTICAL);
        heroInfo.setGravity(Gravity.BOTTOM);
        LinearLayout providerLine = new LinearLayout(this);
        providerLine.setGravity(Gravity.CENTER_VERTICAL);
        heroLogo = new ImageView(this);
        heroLogo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        heroLogo.setOutlineProvider(new Round(px(6)));
        heroLogo.setClipToOutline(true);
        providerLine.addView(heroLogo, new LinearLayout.LayoutParams(px(34), px(19)));
        heroProvider = text("", 13, true);
        heroProvider.setTextColor(WHITE);
        providerLine.addView(heroProvider, lp(px(8), 0, 0, 0));
        heroInfo.addView(providerLine, lp(0, 0, 0, px(8)));
        heroTitle = text("", 30, true);
        heroTitle.setTextColor(WHITE);
        heroTitle.setSingleLine(false);
        heroTitle.setMaxLines(2);
        heroTitle.setEllipsize(TextUtils.TruncateAt.END);
        heroTitle.setMaxWidth(px(560));
        heroTitle.setLineSpacing(0, 0.95f);
        heroTitle.setShadowLayer(px(8), 0, px(1), 0x66000000);
        heroInfo.addView(heroTitle);
        heroMeta = text("", 15, true);
        heroMeta.setTextColor(WHITE);
        heroInfo.addView(heroMeta, lp(0, px(8), 0, 0));
        heroOverview = text("", 14, false);
        heroOverview.setTextColor(SOFT);
        heroOverview.setSingleLine(false);
        heroOverview.setMaxLines(2);
        heroOverview.setTextSize(13);
        heroOverview.setEllipsize(TextUtils.TruncateAt.END);
        heroOverview.setMaxWidth(px(520));
        heroOverview.setLineSpacing(px(2), 1f);
        heroInfo.addView(heroOverview, lp(0, px(6), 0, 0));
        dots = new LinearLayout(this);
        dots.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-1, px(HERO_H));
        hlp.setMargins(0, px(22), 0, px(4));
        heroInfo.setClipChildren(true);
        homePage.addView(heroInfo, hlp);

        forYouLabel = label("Para você");
        LinearLayout labelLine = new LinearLayout(this);
        labelLine.setGravity(Gravity.CENTER_VERTICAL);
        labelLine.addView(forYouLabel, new LinearLayout.LayoutParams(0, -2, 1f));
        labelLine.addView(dots);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(-1, -2);
        llp.setMargins(0, px(10), 0, 0);
        homePage.addView(labelLine, llp);
        forYouRow = row();
        forYouScroll = scroller(forYouRow);
        homePage.addView(forYouScroll, scrollLp());

        continueLabel = label("Continue de onde parou");
        continueRow = row();
        continueScroll = scroller(continueRow);
        frequentLabel = label("Apps frequentes");
        frequentRow = row();
        frequentRow.setPadding(px(SIDE), px(10), px(8), px(12));
        continueRow.setPadding(px(16), px(10), px(SIDE), px(12));
        frequentScroll = scroller(frequentRow);
        continueSplit = new LinearLayout(this);
        continueSplit.setOrientation(LinearLayout.HORIZONTAL);
        continueSplit.setClipChildren(false);
        continueSplit.setClipToPadding(false);
        LinearLayout freqCol = new LinearLayout(this);
        freqCol.setOrientation(LinearLayout.VERTICAL);
        freqCol.setClipChildren(false);
        freqCol.addView(frequentLabel, lp(px(SIDE), px(6), 0, 0));
        freqCol.addView(frequentScroll, new LinearLayout.LayoutParams(-2, -2));
        LinearLayout contCol = new LinearLayout(this);
        contCol.setOrientation(LinearLayout.VERTICAL);
        contCol.setClipChildren(false);
        contCol.addView(continueLabel, lp(px(16), px(6), 0, 0));
        contCol.addView(continueScroll, new LinearLayout.LayoutParams(-1, -2));
        continueSplit.addView(freqCol, new LinearLayout.LayoutParams(-2, -2));
        continueSplit.addView(contCol, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams splitLp = new LinearLayout.LayoutParams(-1, -2);
        splitLp.setMargins(-px(SIDE), 0, -px(SIDE), 0);
        homePage.addView(continueSplit, splitLp);

        watchlistLabel = label("Minha lista");
        homePage.addView(watchlistLabel, lp(0, px(6), 0, 0));
        watchlistRow = row();
        watchlistScroll = scroller(watchlistRow);
        homePage.addView(watchlistScroll, scrollLp());

        weatherLabel = label("Clima");
        homePage.addView(weatherLabel, lp(0, px(8), 0, 0));
        weatherRow = row();
        LinearLayout.LayoutParams weatherRowParams = new LinearLayout.LayoutParams(-1, -2);
        weatherRowParams.setMargins(-px(SIDE), 0, -px(SIDE), 0);
        homePage.addView(weatherRow, weatherRowParams);

        cinemaLabel = label("Cinema");
        homePage.addView(cinemaLabel, lp(0, px(8), 0, 0));
        cinemaRow = row();
        cinemaScroll = scroller(cinemaRow);
        homePage.addView(cinemaScroll, scrollLp());

        reminderLabel = label("Lembretes de cinema");
        homePage.addView(reminderLabel, lp(0, px(8), 0, 0));
        reminderRow = row();
        reminderScroll = scroller(reminderRow);
        homePage.addView(reminderScroll, scrollLp());
        reminderLabel.setVisibility(View.GONE);
        reminderScroll.setVisibility(View.GONE);

        appsLabel = label("Seus apps");
        homePage.addView(appsLabel, lp(0, px(6), 0, 0));
        // "Seus apps" is a grid: as many as fit across the screen, the rest on the lines below.
        appsRow = new LinearLayout(this);
        appsRow.setOrientation(LinearLayout.VERTICAL);
        appsRow.setClipChildren(false);
        appsRow.setClipToPadding(false);
        appsRow.setPadding(0, px(10), 0, px(12));
        homePage.addView(appsRow, new LinearLayout.LayoutParams(-1, -2));

        histLabel = label("");
        histLabel.setVisibility(View.GONE);
        homePage.addView(histLabel, lp(0, px(2), 0, 0));
        histRow = row();
        histScroll = scroller(histRow);
        histScroll.setVisibility(View.GONE);
        homePage.addView(histScroll, scrollLp());
        // Taller than the screen on purpose: the rows below "Para você" wait under the bottom edge.
        content.addView(homePage, new LinearLayout.LayoutParams(-1, px(1600)));

        // Filmes, Séries, Apps
        gridPage = new ScrollView(this);
        gridPage.setVerticalScrollBarEnabled(false);
        // Focus zoom is applied to whole cards. Let the transformed card draw into the
        // reserved inset instead of clipping it at the ScrollView's viewport boundary.
        gridPage.setClipChildren(false);
        gridPage.setClipToPadding(false);
        // Reserve room for the largest focus zoom (including the focusLarge option)
        // so first/last rows in Movies, Series and Apps remain fully visible.
        gridPage.setPadding(0, px(28), 0, px(40));
        grid = createGrid();
        gridPage.addView(grid, new ViewGroup.LayoutParams(-1, -2));
        gridPage.setVisibility(View.GONE);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(-1, 0, 1f);
        glp.setMargins(-px(8), 0, -px(8), 0);
        content.addView(gridPage, glp);

        // Busca
        searchPage = new LinearLayout(this);
        searchPage.setOrientation(LinearLayout.VERTICAL);
        searchPage.setVisibility(View.GONE);
        searchPage.setClipChildren(false);
        searchBox = new android.widget.EditText(this);
        searchBox.setSingleLine(true);
        searchBox.setHint("Buscar");
        searchBox.setHintTextColor(DIM);
        searchBox.setTextColor(WHITE);
        searchBox.setTextSize(15);
        searchBox.setPadding(px(14), px(8), px(18), px(8));
        searchBox.setBackground(pill(0x24FFFFFF, 0x24FFFFFF, px(20)));
        Magnifier inBox = new Magnifier(px(2));
        inBox.setColor(DIM);
        inBox.setBounds(0, 0, px(26), px(26));
        searchBox.setCompoundDrawables(inBox, null, null, null);
        searchBox.setCompoundDrawablePadding(px(4));
        searchBox.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        searchBox.setOnEditorActionListener((v, action, ev) -> {
            ui.removeCallbacks(searchLater);
            runSearch(v.getText().toString());
            return false;
        });
        searchBox.addTextChangedListener(new SearchWatch(() -> {
            ui.removeCallbacks(searchLater);
            ui.postDelayed(searchLater, 800);
        }));
        searchBox.setOnFocusChangeListener((v, has) -> {
            searchBox.setBackground(pill(has ? 0x38FFFFFF : 0x24FFFFFF, has ? 0x80FFFFFF : 0x24FFFFFF, px(20)));
            inBox.setColor(has ? WHITE : DIM);
        });
        LinearLayout searchInputRow = new LinearLayout(this);
        searchInputRow.setGravity(Gravity.CENTER_VERTICAL);
        searchBox.setLayoutParams(new LinearLayout.LayoutParams(px(360), -2));
        searchInputRow.addView(searchBox);
        TextView voice = text("🎙  Falar", 15, true);
        voice.setTextColor(WHITE); voice.setFocusable(true); voice.setClickable(true);
        voice.setPadding(px(16), px(10), px(16), px(10));
        voice.setBackground(pill(0x33FFFFFF, 0x44FFFFFF, px(18)));
        voice.setOnClickListener(v -> startVoiceSearch());
        searchInputRow.addView(voice, lp(px(10), 0, 0, 0));
        LinearLayout.LayoutParams sb = new LinearLayout.LayoutParams(-2, -2);
        sb.setMargins(0, px(22), 0, px(6));
        searchPage.addView(searchInputRow, sb);
        ScrollView sr = new ScrollView(this);
        sr.setVerticalScrollBarEnabled(false);
        sr.setClipToPadding(false);
        sr.setPadding(0, 0, 0, px(30));
        searchResults = new LinearLayout(this);
        searchResults.setOrientation(LinearLayout.VERTICAL);
        searchResults.setClipChildren(false);
        sr.addView(searchResults, new ViewGroup.LayoutParams(-1, -2));
        LinearLayout.LayoutParams srl = new LinearLayout.LayoutParams(-1, 0, 1f);
        srl.setMargins(-px(SIDE), 0, -px(SIDE), 0);
        sr.setPadding(px(SIDE), 0, px(SIDE), px(30));
        sr.setClipChildren(false);
        searchPage.addView(sr, srl);
        content.addView(searchPage, new LinearLayout.LayoutParams(-1, 0, 1f));
        return root;
    }

    private LinearLayout createGrid() {
        LinearLayout result = new LinearLayout(this);
        result.setOrientation(LinearLayout.VERTICAL);
        result.setClipChildren(false);
        // The focus zoom and ring of the first and last column draw into this inset.
        result.setClipToPadding(false);
        result.setPadding(px(GRID_SAFE_INSET), 0, px(GRID_SAFE_INSET), 0);
        return result;
    }

    /** Keep each tab's already-built cards attached to its own cached grid. */
    private void activateGrid(int targetPage) {
        if (activeGridPage == targetPage) return;
        if (activeGridPage >= 0 && grid != null) {
            cachedGrids.put(activeGridPage, grid);
            cachedGridScroll.put(activeGridPage, gridPage.getScrollY());
        }
        LinearLayout target = cachedGrids.get(targetPage);
        if (target == null) {
            target = createGrid();
            cachedGrids.put(targetPage, target);
        }
        gridPage.removeAllViews();
        grid = target;
        gridPage.addView(grid, new ViewGroup.LayoutParams(-1, -2));
        activeGridPage = targetPage;
        int savedScroll = cachedGridScroll.containsKey(targetPage) ? cachedGridScroll.get(targetPage) : 0;
        gridPage.post(() -> gridPage.scrollTo(0, savedScroll));
    }

    private ImageView heroImage() {
        ImageView v = new ImageView(this);
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        return v;
    }

    private LinearLayout.LayoutParams scrollLp() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(-px(SIDE), 0, -px(SIDE), 0);
        return p;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setClipChildren(false);
        r.setClipToPadding(false);
        r.setPadding(px(SIDE), px(10), px(SIDE), px(12));
        return r;
    }

    private HorizontalScrollView scroller(LinearLayout row) {
        HorizontalScrollView s = new HorizontalScrollView(this);
        s.setHorizontalScrollBarEnabled(false);
        s.setOverScrollMode(View.OVER_SCROLL_NEVER);
        s.addView(row, new ViewGroup.LayoutParams(-2, -2));
        return s;
    }

    private TextView text(String t, int sp, boolean medium) {
        TextView v = new TextView(this);
        v.setText(t);
        float scale = prefs == null ? 1f : prefs.getFloat("textScale", 1f);
        v.setTextSize(sp * scale);
        v.setSingleLine(true);
        v.setIncludeFontPadding(false);
        v.setTypeface(Typeface.create(medium ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        return v;
    }

    private TextView label(String t) {
        TextView v = text(t, 13, true);
        v.setTextColor(SOFT);
        return v;
    }

    private static LinearLayout.LayoutParams lp(int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
        p.setMargins(l, t, r, b);
        return p;
    }

    private GradientDrawable pill(int fill, int stroke, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(radius);
        g.setStroke(Math.max(1, px(1)), stroke);
        return g;
    }

    private TextView tab(String t, int which) {
        TextView v = text(t, 15, true);
        v.setFocusable(true);
        v.setPadding(px(16), px(8), px(16), px(8));
        v.setOnFocusChangeListener((x, has) -> {
            if (has) showPage(which);
            styleTabs();
        });
        v.setOnClickListener(x -> showPage(which));
        return v;
    }

    private void styleTabs() {
        if (searchButton != null) {
            boolean f = searchButton.isFocused();
            searchButton.setBackground(f ? pill(WHITE, WHITE, px(18)) : page == SEARCH ? pill(0x40FFFFFF, 0x00FFFFFF, px(18)) : null);
            magnifier.setColor(f ? Color.BLACK : page == SEARCH ? WHITE : SOFT);
        }
        for (int i = 0; i < tabs.length; i++) {
            TextView v = tabs[i];
            boolean sel = i == page;
            if (v.isFocused()) {
                v.setBackground(pill(WHITE, WHITE, px(18)));
                v.setTextColor(Color.BLACK);
            } else if (sel) {
                v.setBackground(pill(0x40FFFFFF, 0x00FFFFFF, px(18)));
                v.setTextColor(WHITE);
            } else {
                v.setBackground(null);
                v.setTextColor(SOFT);
            }
        }
    }

    private void showPage(int p) {
        if (p != SEARCH && searchBox != null) {
            android.view.inputmethod.InputMethodManager im =
                    (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (im != null) im.hideSoftInputFromWindow(searchBox.getWindowToken(), 0);
        }
        if (page == p) return;
        if (p != HOME && p != SEARCH) activateGrid(p);
        page = p;
        hideHistory(false);
        homePage.setVisibility(p == HOME ? View.VISIBLE : View.GONE);
        gridPage.setVisibility(p == HOME || p == SEARCH ? View.GONE : View.VISIBLE);
        searchPage.setVisibility(p == SEARCH ? View.VISIBLE : View.GONE);
        scrimFull.animate().alpha(p == HOME ? 0f : 0.82f).setDuration(250).start();
        if (p != HOME && p != SEARCH && (!builtGridPages.contains(p) || (p == APPS && managingApps))) fillGrid();
        styleTabs();
    }

    // ------------------------------------------------------------------ hero

    private void applyTint() {
        int t = TINT[period];
        GradientDrawable left = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xF5000000 | t, 0xC8000000 | t, 0x50000000 | t, 0x10000000 | t});
        scrimLeft.setBackground(left);
        GradientDrawable bottom = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xB0000000 | t, 0x20000000 | t, 0xA0000000 | t, 0xFA000000 | t});
        scrimBottom.setBackground(bottom);
        scrimFull.setBackgroundColor(0xFF000000 | t);
    }

    private void applyWeatherPalette() {
        if (weatherCard == null) return;
        int[] colors = WEATHER_PALETTE[Math.max(0, Math.min(period, WEATHER_PALETTE.length - 1))];
        GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{colors[0], colors[1], colors[2]});
        background.setCornerRadius(px(18));
        background.setStroke(px(1), colors[3]);
        weatherCard.setBackground(background);
        if (weatherDivider != null) weatherDivider.setBackgroundColor(colors[3]);
    }

    private Bitmap decode(String path) {
        return decode(path, 1);
    }

    /** Reuse decoded artwork across tabs; source files remain in the persistent TMDB disk cache. */
    private Bitmap decode(String path, int sample) {
        if (path == null || path.isEmpty() || !new File(path).exists()) return null;
        String key = path + "#" + sample;
        Bitmap cached;
        synchronized (artworkCache) { cached = artworkCache.get(key); }
        if (cached != null && !cached.isRecycled()) return cached;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inPreferredConfig = Bitmap.Config.ARGB_8888;
        o.inSampleSize = sample;
        Bitmap loaded = BitmapFactory.decodeFile(path, o);
        if (loaded != null) {
            synchronized (artworkCache) {
                Bitmap previous = artworkCache.put(key, loaded);
                if (previous != null) artworkCacheBytes -= previous.getByteCount();
                artworkCacheBytes += loaded.getByteCount();
                Iterator<Map.Entry<String, Bitmap>> it = artworkCache.entrySet().iterator();
                while (artworkCacheBytes > ARTWORK_CACHE_MAX_BYTES && it.hasNext()) {
                    Map.Entry<String, Bitmap> eldest = it.next();
                    artworkCacheBytes -= eldest.getValue().getByteCount();
                    it.remove();
                }
            }
        }
        return loaded;
    }

    private void setHeroImage(Object key, Bitmap b) {
        if (key != null && key.equals(heroKey)) return;
        heroKey = key;
        ImageView in = heroOnA ? heroB : heroA, out = heroOnA ? heroA : heroB;
        heroOnA = !heroOnA;
        in.setImageBitmap(b);
        in.animate().alpha(1f).setDuration(500).start();
        out.animate().alpha(0f).setDuration(500).start();
    }

    private void setHeroText(App app, String provider, String title, String meta, String overview) {
        heroProvider.setText(provider);
        Drawable logo = app == null ? null : (app.banner != null ? app.banner : app.icon);
        heroLogo.setImageDrawable(logo);
        heroLogo.setVisibility(logo == null ? View.GONE : View.VISIBLE);
        heroTitle.setText(title);
        heroMeta.setText(meta);
        heroOverview.setText(overview);
        heroOverview.setVisibility(overview == null || overview.isEmpty() ? View.GONE : View.VISIBLE);
        heroInfo.setAlpha(0.4f);
        heroInfo.animate().alpha(1f).setDuration(300).start();
    }

    private void showPick(Tmdb.Pick p) {
        App a = byPkg.get(p.pkg);
        setHeroImage("p|" + p.backdrop, decode(p.backdrop));
        StringBuilder meta = new StringBuilder(p.kind);
        if (p.year != null && !p.year.isEmpty()) meta.append("  ·  ").append(p.year);
        if (p.vote > 0) meta.append("  ·  ★ ").append(String.format(new Locale("pt", "BR"), "%.1f", p.vote));
        setHeroText(a, a != null ? a.label : p.provider, p.title, meta.toString(), p.overview);
        int idx = heroPicks.indexOf(p);
        if (idx >= 0) heroIndex = idx;
        drawDots(idx);
    }

    private void showContinue(App a, History.Item it) {
        Bitmap art = it == null ? null : decode(it.art);
        setHeroImage("c|" + a.pkg + (it == null ? "" : it.title), art != null ? blur(art) : soft(a.banner != null ? a.banner : a.icon));
        String meta = "Continuar" + (it != null ? "  ·  " + ago(it.time) : "");
        String sub = it == null ? "" : (it.sub == null ? "" : it.sub)
                + (it.dur > 0 && it.pos >= 1000 ? (it.sub == null || it.sub.isEmpty() ? "" : "  ·  ") + "parou em " + DateUtils.formatElapsedTime(it.pos / 1000) : "");
        setHeroText(a, a.label, it != null ? it.title : a.label, meta, sub);
        drawDots(-1);
    }

    /** A blurred version of a drawable to use as the background: shrink to a few pixels and stretch back. */
    private static Bitmap soft(Drawable d) {
        if (d == null) return null;
        Bitmap small = Bitmap.createBitmap(32, 18, Bitmap.Config.ARGB_8888);
        d.setBounds(0, 0, 32, 18);
        d.draw(new Canvas(small));
        return Bitmap.createScaledBitmap(Bitmap.createScaledBitmap(small, 8, 5, true), 160, 90, true);
    }

    /** Low-resolution artwork would look pixelated full screen; a heavy blur of it looks intentional. */
    private static Bitmap blur(Bitmap b) {
        return Bitmap.createScaledBitmap(Bitmap.createScaledBitmap(b, 24, 14, true), 192, 108, true);
    }

    private void drawDots(int active) {
        dots.removeAllViews();
        if (heroPicks.size() < 2) return;
        for (int i = 0; i < heroPicks.size(); i++) {
            View d = new View(this);
            GradientDrawable g = new GradientDrawable();
            g.setColor(i == active ? WHITE : 0x66FFFFFF);
            g.setCornerRadius(px(4));
            d.setBackground(g);
            dots.addView(d, new LinearLayout.LayoutParams(px(i == active ? 18 : 7), px(7)));
            ((LinearLayout.LayoutParams) d.getLayoutParams()).setMargins(0, 0, px(6), 0);
        }
    }

    // ------------------------------------------------------------------ data

    private void reload() {
        bg.execute(() -> {
            List<App> list = loadApps();
            ui.post(() -> {
                apps = list;
                render();
            });
            Weather.Forecast forecast = Weather.forecast(this);
            ui.post(() -> renderWeather(forecast));
            List<Cinema.Movie> theater = Cinema.load(this);
            ui.post(() -> renderCinema(theater));
            Set<String> installed = new HashSet<>();
            for (App a : list) installed.add(a.pkg);
            if (Tmdb.refresh(this, installed, recommendationAge())) ui.post(this::render);
        });
    }

    private void renderWeather(Weather.Forecast f) {
        if (weather == null || weatherRow == null) return;
        weather.setText(f == null ? "☁  Local" : Weather.icon(f.currentCode, f.day) + "  " + Math.round(f.currentTemp) + "°");
        weatherRow.removeAllViews();
        FrameLayout card = new FrameLayout(this);
        weatherCard = card;
        card.setClickable(true);
        applyWeatherPalette();

        LinearLayout content = new LinearLayout(this);
        content.setGravity(Gravity.CENTER_VERTICAL);
        content.setPadding(px(16), px(10), px(16), px(10));
        card.addView(content, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout current = new LinearLayout(this);
        current.setGravity(Gravity.CENTER_VERTICAL);
        TextView icon = text(f == null ? "📍" : Weather.icon(f.currentCode, f.day), 30, true);
        icon.setTextColor(WHITE);
        icon.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams currentIconParams = new LinearLayout.LayoutParams(px(42), px(48));
        currentIconParams.rightMargin = px(9);
        current.addView(icon, currentIconParams);
        LinearLayout currentInfo = new LinearLayout(this);
        currentInfo.setOrientation(LinearLayout.VERTICAL);
        currentInfo.setGravity(Gravity.CENTER_VERTICAL);
        TextView place = text(weatherLocationLabel().toUpperCase(new Locale("pt", "BR")), 10, true);
        place.setTextColor(0xFFC6D8F2);
        place.setMaxLines(1);
        place.setEllipsize(TextUtils.TruncateAt.END);
        currentInfo.addView(place);
        LinearLayout tempLine = new LinearLayout(this);
        tempLine.setGravity(Gravity.CENTER_VERTICAL);
        TextView temperature = text(f == null ? "Clima" : Math.round(f.currentTemp) + "°", f == null ? 22 : 30, true);
        temperature.setTextColor(WHITE);
        tempLine.addView(temperature);
        currentInfo.addView(tempLine);
        TextView condition = text(f == null ? "Escolher cidade" : Weather.description(f.currentCode), 12, true);
        condition.setTextColor(0xFFE2ECFA);
        condition.setMaxLines(1);
        condition.setEllipsize(TextUtils.TruncateAt.END);
        currentInfo.addView(condition);
        TextView metrics = text(f == null ? "Toque para configurar" : "Sensação "
                + Math.round(f.apparentTemp) + "°  ·  Umid. " + f.humidity + "%", 10, false);
        metrics.setTextColor(0xFFB8CBE4);
        metrics.setMaxLines(1);
        metrics.setEllipsize(TextUtils.TruncateAt.END);
        currentInfo.addView(metrics, lp(0, px(2), 0, 0));
        current.addView(currentInfo, new LinearLayout.LayoutParams(0, -2, 1f));
        content.addView(current, new LinearLayout.LayoutParams(0, -1, 0.39f));

        View divider = new View(this);
        weatherDivider = divider;
        applyWeatherPalette();
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(px(1), px(72));
        dividerParams.setMargins(px(10), 0, px(10), 0);
        content.addView(divider, dividerParams);

        LinearLayout week = new LinearLayout(this);
        week.setOrientation(LinearLayout.HORIZONTAL);
        week.setGravity(Gravity.CENTER_VERTICAL);
        org.json.JSONArray days = f == null ? new org.json.JSONArray() : f.daily;
        org.json.JSONArray codes = f == null ? new org.json.JSONArray() : jsonArray(f.dailyData, "weather_code");
        org.json.JSONArray highs = f == null ? new org.json.JSONArray() : jsonArray(f.dailyData, "temperature_2m_max");
        org.json.JSONArray lows = f == null ? new org.json.JSONArray() : jsonArray(f.dailyData, "temperature_2m_min");
        java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US);
        java.text.SimpleDateFormat weekdayFormat = new java.text.SimpleDateFormat("EEE", new Locale("pt", "BR"));
        for (int i = 0; i < Math.min(7, days.length()); i++) {
            LinearLayout day = new LinearLayout(this);
            day.setOrientation(LinearLayout.VERTICAL);
            day.setGravity(Gravity.CENTER);
            String weekday = days.optString(i, "");
            try { weekday = weekdayFormat.format(dateFormat.parse(weekday)); }
            catch (Exception ignored) { if (weekday.length() > 5) weekday = weekday.substring(5); }
            TextView name = text(i == 0 ? "HOJE" : weekday.replace(".", "").toUpperCase(new Locale("pt", "BR")), 9, true);
            name.setTextColor(0xFFC6D8F2);
            name.setMaxLines(1);
            name.setGravity(Gravity.CENTER);
            day.addView(name, new LinearLayout.LayoutParams(-1, -2));
            TextView dayIcon = text(Weather.icon(codes.optInt(i), true), 17, true);
            dayIcon.setTextColor(WHITE);
            dayIcon.setGravity(Gravity.CENTER);
            day.addView(dayIcon, new LinearLayout.LayoutParams(-1, px(27)));
            TextView high = text(Math.round(highs.optDouble(i)) + "°", 11, true);
            high.setTextColor(WHITE);
            high.setGravity(Gravity.CENTER);
            day.addView(high, new LinearLayout.LayoutParams(-1, -2));
            TextView low = text(Math.round(lows.optDouble(i)) + "°", 10, false);
            low.setTextColor(0xFFB8CBE4);
            low.setGravity(Gravity.CENTER);
            day.addView(low, new LinearLayout.LayoutParams(-1, -2));
            LinearLayout.LayoutParams dayParams = new LinearLayout.LayoutParams(0, -2, 1f);
            if (i < Math.min(7, days.length()) - 1) dayParams.rightMargin = px(2);
            week.addView(day, dayParams);
        }
        content.addView(week, new LinearLayout.LayoutParams(0, -1, 0.61f));
        View focusAnchor = new View(this);
        focusAnchor.setFocusable(true);
        focusAnchor.setClickable(true);
        focusAnchor.setContentDescription(f == null ? "Clima. Toque para configurar a localização." : "Clima em "
                + weatherLocationLabel() + ". " + Weather.description(f.currentCode) + ", " + Math.round(f.currentTemp)
                + " graus. Toque para ver a previsão detalhada.");
        focusAnchor.setOnClickListener(v -> {
            if (f == null) weatherMenu(); else showWeatherDetail(f);
        });
        card.setOnClickListener(v -> {
            if (f == null) weatherMenu(); else showWeatherDetail(f);
        });
        focusAnchor.setOnKeyListener((v, code, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN
                    && (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN)) {
                if (code == KeyEvent.KEYCODE_DPAD_DOWN) return focusFirstCinemaItem();
                return focusAdjacentHomeSection(weatherRow, code == KeyEvent.KEYCODE_DPAD_DOWN);
            }
            return false;
        });
        // Keep the visual card wide, but anchor its DPAD focus geometry to column one.
        card.addView(focusAnchor, new FrameLayout.LayoutParams(px(176), -1, Gravity.LEFT));
        GradientDrawable weatherFocusRing = new GradientDrawable();
        weatherFocusRing.setColor(Color.TRANSPARENT);
        weatherFocusRing.setCornerRadius(px(18));
        weatherFocusRing.setStroke(px(prefs != null && prefs.getBoolean("focusBold", false) ? 5 : 3), WHITE);
        float weatherFocusScale = 1.035f + (prefs != null && prefs.getBoolean("focusLarge", false) ? 0.08f : 0f);
        focusAnchor.setOnFocusChangeListener((v, focused) -> {
            card.animate().scaleX(focused ? weatherFocusScale : 1f).scaleY(focused ? weatherFocusScale : 1f)
                    .setDuration(160).start();
            card.setTranslationZ(focused ? px(16) : 0);
            card.setForeground(focused ? weatherFocusRing : null);
        });
        weatherRow.addView(card, new LinearLayout.LayoutParams(px(3 * 176 + 2 * 16), px(124)));
        if (weatherLabel != null) weatherLabel.setVisibility(View.VISIBLE);
    }

    /** Vertical movement from the single weather widget enters the adjacent section at its first item. */
    private boolean focusAdjacentHomeSection(View current, boolean down) {
        View[] sections = {continueSplit, watchlistScroll, weatherRow, cinemaScroll, reminderScroll, appsRow};
        int currentIndex = -1;
        for (int i = 0; i < sections.length; i++) {
            if (sections[i] == current || isInside(current, sections[i])) {
                currentIndex = i;
                break;
            }
        }
        if (currentIndex < 0) return false;
        int step = down ? 1 : -1;
        for (int i = currentIndex + step; i >= 0 && i < sections.length; i += step) {
            View section = sections[i];
            if (section == null || section.getVisibility() != View.VISIBLE) continue;
            View target = firstFocusableContent(section);
            if (target != null && target.requestFocus()) return true;
        }
        return false;
    }

    /** The climate widget has one focus target; DPAD-down must land on cinema's first card, not a distant row. */
    private boolean focusFirstCinemaItem() {
        if (cinemaRow == null || cinemaScroll == null || cinemaScroll.getVisibility() != View.VISIBLE) return false;
        cinemaScroll.scrollTo(0, 0);
        for (int i = 0; i < cinemaRow.getChildCount(); i++) {
            View item = cinemaRow.getChildAt(i);
            View target = item.isFocusable() ? item : firstFocusableContent(item);
            if (target != null && target.requestFocus()) return true;
        }
        return false;
    }

    private String rainAlert(Weather.Forecast f) {
        org.json.JSONArray times = jsonArray(f.hourlyData, "time");
        org.json.JSONArray rain = jsonArray(f.hourlyData, "precipitation_probability");
        String now = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH", Locale.US).format(new java.util.Date());
        for (int i = 0; i < Math.min(times.length(), 8); i++) {
            String time = times.optString(i, "");
            if (time.compareTo(now) < 0 || rain.optInt(i) < 70) continue;
            String hour = time.length() >= 16 ? time.substring(11, 16) : "nas próximas horas";
            return "chuva provável às " + hour;
        }
        return "";
    }

    private String weatherLocationLabel() {
        if (!Weather.hasLocation(this)) return "Sua região";
        String place = Weather.place(this).trim();
        if (place.isEmpty() || place.matches("[-+0-9.,\\s]+")) return "Sua região";
        int separator = place.indexOf(" · ");
        if (separator > 0) place = place.substring(0, separator).trim();
        return place;
    }

    private void renderCinema(List<Cinema.Movie> movies) {
        if (cinemaRow == null) return;
        if (cinemaLabel != null) cinemaLabel.setVisibility(View.VISIBLE);
        if (cinemaScroll != null) cinemaScroll.setVisibility(View.VISIBLE);
        cinemaMovies = movies == null ? new ArrayList<>() : movies;
        if (cinemaLabel != null) cinemaLabel.setText(cinemaTitle());
        cinemaRow.removeAllViews();
        if (cinemaMovies.isEmpty()) {
            TextView empty = text(Tmdb.enabled() ? "Carregando filmes em cartaz…" : "Configure a chave TMDB para ver filmes em cartaz.", 14, false);
            empty.setTextColor(SOFT);
            cinemaRow.addView(empty, lp(0, 0, 0, 0));
        } else {
            for (Cinema.Movie m : cinemaMovies.subList(0, Math.min(12, cinemaMovies.size()))) {
                cinemaRow.addView(cinemaCard(m), lp(0, 0, px(16), 0));
            }
        }
        TextView nearby = text(Weather.hasLocation(this) ? "Buscar cinemas e horários" : "Ativar localização para buscar cinemas próximos",
                14, true);
        nearby.setTextColor(WHITE); nearby.setFocusable(true); nearby.setClickable(true);
        nearby.setPadding(px(16), px(12), px(16), px(12));
        nearby.setBackground(pill(0x88213D61, 0x33FFFFFF, px(14)));
        nearby.setOnClickListener(v -> openCinemaSearch(null));
        cinemaRow.addView(nearby, lp(0, 0, 0, 0));
        renderCinemaReminders();
    }

    private String cinemaTitle() {
        if (!Weather.hasLocation(this)) return "Cinema";
        return "Cinema · " + weatherLocationLabel();
    }

    private View cinemaCard(Cinema.Movie movie) {
        Bitmap image = decode(movie.image, 2);
        FrameLayout card = tile(image, null, px(12), false);
        card.setFocusable(true); card.setClickable(true);
        TextView title = text(movie.title, 14, true);
        title.setTextColor(WHITE); title.setMaxWidth(px(190));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.addView(card, new LinearLayout.LayoutParams(px(190), px(107)));
        content.addView(title, lp(0, px(7), 0, 0));
        String cinemaMeta = movie.screenings.isEmpty()
                ? (movie.release == null || movie.release.length() < 4 ? "Em cartaz" : "Em cartaz · " + movie.release.substring(0, 4))
                : movie.screenings.size() + " sessões próximas · segure OK para horários";
        TextView year = text(cinemaMeta, 12, false);
        year.setTextColor(SOFT); content.addView(year, lp(0, px(3), 0, 0));
        content.setFocusable(true); content.setClickable(true);
        content.setOnClickListener(v -> openYouTubeSearch(movie.title));
        content.setOnLongClickListener(v -> {
            if (movie.screenings.isEmpty()) askCinemaReminder(movie);
            else showCinemaSessions(movie);
            return true;
        });
        content.setContentDescription(movie.title + ". Toque para pesquisar no YouTube."
                + (movie.screenings.isEmpty() ? " Segure OK para criar lembrete." : " Segure OK para ver sessões e criar lembrete."));
        focusFx(content, card, 1.06f, null);
        return content;
    }

    private void showCinemaSessions(Cinema.Movie movie) {
        String[] options = new String[movie.screenings.size() + 3];
        options[0] = "Pesquisar no YouTube";
        for (int i = 0; i < movie.screenings.size(); i++) {
            Cinema.Screening show = movie.screenings.get(i);
            String when = formatCinemaTime(show.time);
            options[i + 1] = when + " · " + show.theater + (show.distance >= 0 ? " · " + Math.round(show.distance) + " km" : "");
        }
        options[options.length - 2] = "Criar lembrete";
        options[options.length - 1] = "Buscar mais cinemas";
        showSheet(movie.title, null, "Sessões próximas. Os horários e links são fornecidos pelo Open Cinema Project.", options, which -> {
            if (which == 0) { openYouTubeSearch(movie.title); return; }
            if (which == options.length - 2) { askCinemaReminder(movie); return; }
            if (which == options.length - 1) { openCinemaSearch(movie.title); return; }
            String target = movie.screenings.get(which - 1).url;
            if (target == null || target.isEmpty()) { openCinemaSearch(movie.title); return; }
            Intent intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(target));
            if (intent.resolveActivity(getPackageManager()) != null) open(intent);
            else hint("Nenhum navegador disponível para abrir o ingresso.");
        });
    }

    private void openYouTubeSearch(String title) {
        String url = "https://www.youtube.com/results?search_query=" + android.net.Uri.encode(title == null ? "" : title);
        Intent intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url));
        intent.setPackage("com.google.android.youtube.tv");
        if (intent.resolveActivity(getPackageManager()) != null) open(intent);
        else hint("O aplicativo do YouTube não está disponível para abrir a busca.");
    }

    private String formatCinemaTime(String value) {
        if (value == null || value.isEmpty()) return "Horário não informado";
        try {
            long millis = java.time.Instant.parse(value).toEpochMilli();
            return new java.text.SimpleDateFormat("EEE d MMM · HH:mm", new Locale("pt", "BR")).format(new java.util.Date(millis));
        } catch (Exception ignored) { return value; }
    }

    private void askCinemaReminder(Cinema.Movie movie) {
        java.util.Calendar now = java.util.Calendar.getInstance();
        now.add(java.util.Calendar.DAY_OF_MONTH, 1);
        new android.app.DatePickerDialog(this, (picker, year, month, day) -> {
            new android.app.TimePickerDialog(this, (timePicker, hour, minute) -> {
                java.util.Calendar at = java.util.Calendar.getInstance();
                at.set(year, month, day, hour, minute, 0); at.set(java.util.Calendar.MILLISECOND, 0);
                if (at.getTimeInMillis() <= System.currentTimeMillis()) {
                    new AlertDialog.Builder(this).setMessage("Escolha um horário futuro.").setPositiveButton("OK", null).show();
                    return;
                }
                Cinema.addReminder(this, movie.title, at.getTimeInMillis());
                renderCinemaReminders();
            }, 20, 0, true).show();
        }, now.get(java.util.Calendar.YEAR), now.get(java.util.Calendar.MONTH), now.get(java.util.Calendar.DAY_OF_MONTH)).show();
    }

    private void renderCinemaReminders() {
        if (reminderRow == null) return;
        List<Cinema.Reminder> reminders = Cinema.reminders(this);
        Collections.sort(reminders, (a, b) -> Long.compare(a.at, b.at));
        reminderRow.removeAllViews();
        int shown = 0;
        for (Cinema.Reminder reminder : reminders) {
            if (shown++ >= 8) break;
            String date = new java.text.SimpleDateFormat("EEE d MMM · HH:mm", new Locale("pt", "BR")).format(new java.util.Date(reminder.at));
            TextView card = text("◷  " + reminder.title + " · " + date + (reminder.notified ? " · avisado" : ""), 14, true);
            card.setTextColor(WHITE); card.setFocusable(true); card.setClickable(true);
            card.setPadding(px(16), px(12), px(16), px(12));
            card.setBackground(pill(0x88213D61, 0x33FFFFFF, px(14)));
            card.setOnClickListener(v -> showSheet("Lembrete de cinema", null, reminder.title + " · " + date,
                    new String[]{"Remover lembrete", "Fechar"}, choice -> {
                        if (choice == 0) { Cinema.removeReminder(this, reminder.at); renderCinemaReminders(); }
                    }));
            reminderRow.addView(card, lp(0, 0, px(12), 0));
        }
        int visible = reminders.isEmpty() ? View.GONE : View.VISIBLE;
        reminderLabel.setVisibility(visible); reminderScroll.setVisibility(visible);
    }

    private void checkCinemaReminders() {
        if (sheet != null || layer != null) return;
        long now = System.currentTimeMillis();
        for (Cinema.Reminder reminder : Cinema.reminders(this)) {
            if (!reminder.notified && reminder.at <= now) {
                Cinema.markNotified(this, reminder.at);
                renderCinemaReminders();
                showSheet("Sessão de cinema", null, reminder.title + " · o horário do lembrete chegou.",
                        new String[]{"Buscar cinema", "Dispensar"}, choice -> {
                            if (choice == 0) openCinemaSearch(reminder.title);
                            renderCinemaReminders();
                        });
                return;
            }
        }
    }

    private void openCinemaSearch(String title) {
        if (!Weather.hasLocation(this)) { weatherMenu(); return; }
        String query = title == null ? "cinemas e horários de cinema" : title + " horários cinema";
        String url = "https://www.google.com/search?q=" + android.net.Uri.encode(query + " perto de " + Weather.place(this));
        Intent i = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url));
        if (i.resolveActivity(getPackageManager()) != null) open(i);
        else hint("Nenhum navegador disponível para abrir cinemas próximos.");
    }

    private void askLocationIfNeeded() {
        if (Weather.hasLocation(this) || getSharedPreferences("tempo", MODE_PRIVATE).getBoolean("asked", false)) return;
        getSharedPreferences("tempo", MODE_PRIVATE).edit().putBoolean("asked", true).apply();
        new AlertDialog.Builder(this).setTitle("Clima e cinema perto de você")
                .setMessage("Permita a localização para mostrar a previsão local e encontrar cinemas próximos. A localização fica neste aparelho. Você também pode informar uma cidade manualmente.")
                .setPositiveButton("Permitir localização", (d, w) -> requestPermissions(
                        new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION, android.Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST))
                .setNegativeButton("Informar cidade", (d, w) -> enterCity())
                .setNeutralButton("Agora não", (d, w) -> { }).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != LOCATION_REQUEST) return;
        boolean granted = false;
        for (int result : grantResults) if (result == PackageManager.PERMISSION_GRANTED) granted = true;
        if (granted) readApprovedLocation(); else enterCity();
    }

    private void startVoiceSearch() {
        Intent voice = new Intent("android.speech.action.RECOGNIZE_SPEECH")
                .putExtra("android.speech.extra.LANGUAGE_MODEL", "free_form")
                .putExtra("android.speech.extra.LANGUAGE", "pt-BR")
                .putExtra("android.speech.extra.PROMPT", "O que você quer assistir?");
        if (voice.resolveActivity(getPackageManager()) == null) {
            hint("A pesquisa por voz não está disponível nesta TV.");
            return;
        }
        try { startActivityForResult(voice, VOICE_SEARCH_REQUEST); }
        catch (Exception e) { hint("Não foi possível iniciar o reconhecimento de voz."); }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != VOICE_SEARCH_REQUEST || resultCode != RESULT_OK || data == null) return;
        ArrayList<String> results = data.getStringArrayListExtra("android.speech.extra.RESULTS");
        if (results == null || results.isEmpty()) return;
        searchBox.setText(results.get(0));
        searchBox.setSelection(searchBox.length());
        runSearch(results.get(0));
    }

    private void readApprovedLocation() {
        bg.execute(() -> {
            Location best = null;
            try {
                LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
                for (String provider : new String[]{LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER}) {
                    try {
                        if (lm.isProviderEnabled(provider)) {
                            Location candidate = lm.getLastKnownLocation(provider);
                            if (candidate != null && (best == null || candidate.getTime() > best.getTime())) best = candidate;
                        }
                    } catch (SecurityException ignored) { }
                }
            } catch (Exception e) { android.util.Log.w("Sala", "Localização indisponível: " + e); }
            if (best == null) {
                ui.post(() -> new AlertDialog.Builder(this).setTitle("Localização indisponível")
                        .setMessage("A TV não forneceu uma posição atual. Informe sua cidade para configurar o clima e a busca de cinemas.")
                        .setPositiveButton("Informar cidade", (d, w) -> enterCity()).setNegativeButton("Depois", null).show());
                return;
            }
            double lat = best.getLatitude(), lon = best.getLongitude();
            String place = Weather.nameFor(lat, lon);
            Weather.saveLocation(this, lat, lon, place);
            ui.post(this::reload);
        });
    }

    private void enterCity() {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setSingleLine(true); input.setHint("Cidade, estado ou CEP");
        new AlertDialog.Builder(this).setTitle("Sua cidade").setView(input)
                .setPositiveButton("Buscar", (d, w) -> setCity(input.getText().toString()))
                .setNegativeButton("Cancelar", null).show();
    }

    private void setCity(String city) {
        if (city == null || city.trim().isEmpty()) return;
        bg.execute(() -> {
            org.json.JSONObject place = Weather.findPlace(city);
            if (place == null) { ui.post(() -> hint("Não encontrei essa cidade. Tente informar cidade e estado.")); return; }
            String name = place.optString("name", city);
            String admin = place.optString("admin1", "");
            Weather.saveLocation(this, place.optDouble("latitude"), place.optDouble("longitude"),
                    admin.isEmpty() || admin.equals(name) ? name : name + " · " + admin);
            ui.post(this::reload);
        });
    }

    private void weatherMenu() {
                showSheet("Clima e localização", null, Weather.hasLocation(this)
                        ? "Local configurado: " + weatherLocationLabel() : "Configure uma localização para ver clima e buscar cinemas próximos.",
                Weather.hasLocation(this) ? new String[]{"Previsão detalhada", "Alterar cidade", "Usar localização da TV", "Remover localização"}
                        : new String[]{"Informar cidade", "Permitir localização"}, choice -> {
                    if (!Weather.hasLocation(this)) {
                        if (choice == 0) enterCity();
                        else requestPermissions(new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION,
                                android.Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST);
                    } else if (choice == 0) bg.execute(() -> { Weather.Forecast f = Weather.forecast(this); ui.post(() -> showWeatherDetail(f)); });
                    else if (choice == 1) enterCity();
                    else if (choice == 2) {
                        if (checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                || checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) readApprovedLocation();
                        else requestPermissions(new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION,
                                android.Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_REQUEST);
                    } else { Weather.clearLocation(this); renderWeather(null); renderCinema(cinemaMovies); }
                });
    }

    private void showWeatherDetail(Weather.Forecast f) {
        if (f == null) { weatherMenu(); return; }
        StringBuilder msg = new StringBuilder();
        msg.append(Weather.description(f.currentCode)).append(" · ").append(Math.round(f.currentTemp)).append("°\n")
                .append("Umidade ").append(f.humidity).append("% · Vento ").append(Math.round(f.wind)).append(" km/h · UV ").append(String.format(Locale.getDefault(), "%.1f", f.uv));
        org.json.JSONArray times = f.daily, codes = jsonArray(f.dailyData, "weather_code"), highs = jsonArray(f.dailyData, "temperature_2m_max"),
                lows = jsonArray(f.dailyData, "temperature_2m_min"), rain = jsonArray(f.dailyData, "precipitation_probability_max"),
                amount = jsonArray(f.dailyData, "precipitation_sum");
        msg.append("\n\nPróximos dias");
        for (int i = 0; i < Math.min(7, times.length()); i++) {
            String date = times.optString(i, "");
            if (date.length() >= 10) date = date.substring(8, 10) + "/" + date.substring(5, 7);
            msg.append("\n").append(date).append("  ").append(Weather.icon(codes.optInt(i), true)).append("  ")
                    .append(Math.round(lows.optDouble(i))).append("° / ").append(Math.round(highs.optDouble(i))).append("°")
                    .append(" · chuva ").append(rain.optInt(i)).append("% (").append(String.format(Locale.getDefault(), "%.1f", amount.optDouble(i))).append(" mm)");
        }
        msg.append("\n\nPróximas horas");
        org.json.JSONArray hourTimes = jsonArray(f.hourlyData, "time"), temps = jsonArray(f.hourlyData, "temperature_2m"),
                probs = jsonArray(f.hourlyData, "precipitation_probability"), hourCodes = jsonArray(f.hourlyData, "weather_code");
        int first = 0;
        String now = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH", Locale.US).format(new java.util.Date());
        while (first < hourTimes.length() && hourTimes.optString(first).compareTo(now) < 0) first++;
        for (int i = first; i < Math.min(first + 6, hourTimes.length()); i++) {
            String hour = hourTimes.optString(i);
            if (hour.length() >= 16) hour = hour.substring(11, 16);
            msg.append("\n").append(hour).append("  ").append(Weather.icon(hourCodes.optInt(i), true)).append("  ")
                    .append(Math.round(temps.optDouble(i))).append("° · chuva ").append(probs.optInt(i)).append("%");
        }
        new AlertDialog.Builder(this).setTitle("Clima · " + (f.place == null ? "" : f.place)).setMessage(msg.toString())
                .setPositiveButton("Alterar localização", (d, w) -> weatherMenu())
                .setNegativeButton("Fechar", null).show();
    }

    private static org.json.JSONArray jsonArray(org.json.JSONObject object, String name) {
        org.json.JSONArray value = object == null ? null : object.optJSONArray(name);
        return value == null ? new org.json.JSONArray() : value;
    }

    private List<App> loadApps() {
        PackageManager pm = getPackageManager();
        List<App> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String cat : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            for (ResolveInfo ri : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(cat), 0)) {
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(getPackageName()) || !seen.add(pkg)) continue;
                App a = new App();
                a.pkg = pkg;
                a.label = String.valueOf(ri.loadLabel(pm));
                a.intent = new Intent(Intent.ACTION_MAIN).addCategory(cat)
                        .setComponent(new ComponentName(pkg, ri.activityInfo.name))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                a.banner = ri.activityInfo.loadBanner(pm);
                if (a.banner == null) a.banner = ri.activityInfo.applicationInfo.loadBanner(pm);
                a.icon = ri.loadIcon(pm);
                a.system = (ri.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                out.add(a);
            }
        }
        return out;
    }

    private boolean hidden(App a) {
        if (kidsAge() > 0) {
            // YouTube is explicitly allowed in Kids profiles, regardless of an older saved hide setting.
            if (a.pkg.equals("com.google.android.youtube.tv")) return false;
            // Globoplay is explicitly excluded from every Kids profile, including existing profiles.
            if (a.pkg.equals("com.globo.globotv")) return true;
            return !prefs.getBoolean("kid:" + a.pkg, Profiles.kidsApps(kidsAge()).contains(a.pkg));
        }
        return prefs.getBoolean("hide:" + a.pkg, HIDDEN_BY_DEFAULT.contains(a.pkg));
    }

    private boolean inDock(App a) {
        if (a.system && !isLauncherSystemException(a)) return false;
        return prefs.getBoolean("dock:" + a.pkg, true);
    }

    /** Selected preinstalled streaming apps remain manageable even when Android marks them as system apps. */
    private static boolean isLauncherSystemException(App a) {
        return isYouTube(a) || "com.netflix.ninja".equals(a.pkg);
    }

    private static boolean isYouTube(App a) {
        return "com.google.android.youtube.tv".equals(a.pkg);
    }

    private List<App> sortedApps() {
        List<String> ord = order();
        List<App> sorted = new ArrayList<>();
        // Keep user-installable apps, YouTube and Netflix; the latter two are preinstalled on this TV.
        for (App a : apps) if ((!a.system || isLauncherSystemException(a)) && !hidden(a)) sorted.add(a);
        Collections.sort(sorted, (x, y) -> {
            if ((x.pinned > 0) != (y.pinned > 0)) return x.pinned > 0 ? -1 : 1;
            if (x.pinned > 0 && y.pinned > 0 && x.pinned != y.pinned) return Long.compare(x.pinned, y.pinned);
            int ix = ord.indexOf(x.pkg), iy = ord.indexOf(y.pkg);
            if (ix >= 0 || iy >= 0) return ix < 0 ? 1 : iy < 0 ? -1 : ix - iy;
            if (x.launches != y.launches) return y.launches - x.launches;
            return x.label.compareToIgnoreCase(y.label);
        });
        return sorted;
    }

    private void render() {
        byPkg = new HashMap<>();
        for (App a : apps) {
            a.launches = prefs.getInt("n:" + a.pkg, 0);
            a.last = prefs.getLong("t:" + a.pkg, 0);
            a.pinned = prefs.getLong("pin:" + a.pkg, 0);
            byPkg.put(a.pkg, a);
        }
        picks = new ArrayList<>();
        for (Tmdb.Pick p : Tmdb.cached(this, recommendationAge())) {
            App a = byPkg.get(p.pkg);
            if (a != null && !hidden(a)) picks.add(p);
        }
        // The carousel changes every hour: a different mix of titles from the apps you have.
        List<Tmdb.Pick> pool = new ArrayList<>(picks.subList(0, Math.min(36, picks.size())));
        Collections.shuffle(pool, new java.util.Random(System.currentTimeMillis() / 3600_000L));
        heroPicks = new ArrayList<>(pool.subList(0, Math.min(6, pool.size())));
        picks.removeAll(heroPicks);
        picks.addAll(0, heroPicks);
        List<App> sorted = sortedApps();

        // Mostra somente o item mais recente de cada app, sem transformar a fileira
        // de retomada em uma lista de execuções anteriores.
        forYouRow.removeAllViews();
        List<History.Item> latest = History.latest(this);
        Collections.sort(latest, (x, y) -> Long.compare(y.time, x.time));
        renderContinueRow();
        watchlistRow.removeAllViews();
        for (Watchlist.Entry item : Watchlist.all(this)) {
            if (kidsAge() > 0 && item.kidsSafeUpToAge > kidsAge()) continue;
            watchlistRow.addView(titleCard(item), lp(0, 0, px(16), 0));
        }
        watchlistLabel.setVisibility(watchlistRow.getChildCount() > 0 ? View.VISIBLE : View.GONE);
        watchlistScroll.setVisibility(watchlistRow.getChildCount() > 0 ? View.VISIBLE : View.GONE);
        List<Tmdb.Pick> rest = picks.size() > heroPicks.size() ? picks.subList(heroPicks.size(), picks.size()) : picks;
        for (Tmdb.Pick p : rest.subList(0, Math.min(10, rest.size()))) forYouRow.addView(pickCard(p, 176, 99), lp(0, 0, px(16), 0));
        if (picks.isEmpty()) for (App a : sorted) if (inDock(a)) forYouRow.addView(appCard(a, 176, 99, false), lp(0, 0, px(16), 0));
        forYouLabel.setVisibility(forYouRow.getChildCount() > 0 ? View.VISIBLE : View.GONE);

        appsRow.removeAllViews();
        int perLine = Math.max(1, (getResources().getDisplayMetrics().widthPixels - 2 * px(SIDE) + px(14)) / px(112 + 14));
        LinearLayout line = null;
        int n = 0;
        if (kidsAge() == 0) {
            line = new LinearLayout(this);
            line.setClipChildren(false);
            appsRow.addView(line, lp(0, 0, 0, px(16)));
            line.addView(appManagerCard(112, 63), lp(0, 0, px(14), 0));
            n++;
        }
        for (App a : sorted) {
            if (!inDock(a)) continue;
            if (n++ % perLine == 0) {
                line = new LinearLayout(this);
                line.setClipChildren(false);
                appsRow.addView(line, lp(0, 0, 0, px(16)));
            }
            line.addView(appCard(a, 112, 63, true), lp(0, 0, px(14), 0));
        }
        // Always last: the store, to install new apps (not for kids).
        if (kidsAge() == 0) {
            if (line == null || n % perLine == 0) {
                line = new LinearLayout(this);
                line.setClipChildren(false);
                appsRow.addView(line, lp(0, 0, 0, px(16)));
            }
            line.addView(storeTile(), lp(0, 0, px(14), 0));
        }

        if (!heroPicks.isEmpty() && (heroKey == null || !String.valueOf(heroKey).startsWith("p|"))) showPick(heroPicks.get(0));
        else if (heroKey == null) {
            if (false) showPick(null);
            else if (!latest.isEmpty() && byPkg.containsKey(latest.get(0).pkg)) showContinue(byPkg.get(latest.get(0).pkg), latest.get(0));
            else if (!sorted.isEmpty()) showContinue(sorted.get(0), null);
        }
        builtGridPages.remove(MOVIES);
        builtGridPages.remove(SERIES);
        builtGridPages.remove(APPS);
        if (page == MOVIES || page == SERIES || page == APPS) fillGrid();
        if (histPkg != null && byPkg.containsKey(histPkg)) fillHistory(byPkg.get(histPkg));
        if (firstRender || getCurrentFocus() == null || !getCurrentFocus().isAttachedToWindow()) {
            firstRender = false;
            focusDefault();
        }
    }

    private void renderContinueRow() {
        if (continueRow == null || continueLabel == null || continueScroll == null || apps == null || byPkg == null) return;
        View focused = getCurrentFocus();
        boolean restoreFocus = focused != null && isInside(focused, continueRow);
        int focusedIndex = -1;
        if (restoreFocus) {
            for (int i = 0; i < continueRow.getChildCount(); i++) {
                if (isInside(focused, continueRow.getChildAt(i))) { focusedIndex = i; break; }
            }
        }
        continueRow.removeAllViews();
        List<History.Item> current = new ArrayList<>();
        for (App a : apps) {
            if (hidden(a)) continue;
            List<History.Item> items = History.get(this, a.pkg);
            if (!items.isEmpty()) current.add(items.get(0));
        }
        Collections.sort(current, (x, y) -> Long.compare(y.time, x.time));
        History.Item activePlayback = null;
        for (History.Item item : current) {
            if (item.playing) {
                activePlayback = item;
                break;
            }
        }
        for (History.Item it : current.subList(0, Math.min(15, current.size()))) {
            App a = byPkg.get(it.pkg);
            boolean showEqualizer = it == activePlayback && isMusicApp(it.pkg);
            if (a != null) continueRow.addView(continueCard(a, it, showEqualizer), lp(0, 0, px(16), 0));
        }
        int vis = continueRow.getChildCount() > 0 ? View.VISIBLE : View.GONE;
        continueLabel.setVisibility(vis);
        continueScroll.setVisibility(vis);
        renderFrequentRow();
        continueSplit.setVisibility(continueRow.getChildCount() > 0 || frequentRow.getChildCount() > 0
                ? View.VISIBLE : View.GONE);
        if (restoreFocus && continueRow.getChildCount() > 0) {
            int index = Math.min(Math.max(0, focusedIndex), continueRow.getChildCount() - 1);
            continueRow.getChildAt(index).requestFocus();
        }
    }

    private void renderFrequentRow() {
        View focused = getCurrentFocus();
        boolean restoreFocus = focused != null && isInside(focused, frequentRow);
        int focusedIndex = 0;
        for (int i = 0; restoreFocus && i < frequentRow.getChildCount(); i++) {
            if (isInside(focused, frequentRow.getChildAt(i))) { focusedIndex = i; break; }
        }
        frequentRow.removeAllViews();
        List<App> top = new ArrayList<>();
        for (App a : apps) if (a.launches > 0 && !hidden(a) && (!a.system || isLauncherSystemException(a))) top.add(a);
        Collections.sort(top, (x, y) -> y.launches != x.launches ? y.launches - x.launches : Long.compare(y.last, x.last));
        for (App a : top.subList(0, Math.min(3, top.size()))) {
            View c = appCard(a, 128, 72, false);
            c.setOnKeyListener((v, code, e) -> e.getAction() == KeyEvent.ACTION_DOWN
                    && code == KeyEvent.KEYCODE_DPAD_DOWN && focusFirstHomeSectionBelowContinue());
            frequentRow.addView(c, lp(0, 0, px(12), 0));
        }
        int vis = frequentRow.getChildCount() > 0 ? View.VISIBLE : View.GONE;
        frequentLabel.setVisibility(vis);
        frequentScroll.setVisibility(vis);
        if (restoreFocus && frequentRow.getChildCount() > 0)
            frequentRow.getChildAt(Math.min(focusedIndex, frequentRow.getChildCount() - 1)).requestFocus();
    }

    private View storeTile() {
        FrameLayout t = new FrameLayout(this);
        t.setBackground(pill(0x33FFFFFF, 0x40FFFFFF, px(12)));
        t.setOutlineProvider(new Round(px(12)));
        t.setClipToOutline(true);
        LinearLayout in = new LinearLayout(this);
        in.setGravity(Gravity.CENTER);
        Drawable play = null;
        try {
            play = getPackageManager().getApplicationIcon("com.android.vending");
        } catch (Exception ignored) {
        }
        if (play != null) {
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(play);
            in.addView(iv, new LinearLayout.LayoutParams(px(26), px(26)));
        }
        TextView l = text("Loja", 15, true);
        l.setTextColor(WHITE);
        in.addView(l, lp(px(8), 0, 0, 0));
        t.addView(in, new FrameLayout.LayoutParams(-1, -1));
        FrameLayout wrap = new FrameLayout(this);
        wrap.setFocusable(true);
        wrap.setClickable(true);
        wrap.addView(t, new FrameLayout.LayoutParams(px(112), px(63)));
        wrap.setOnClickListener(v -> {
            Intent i = getPackageManager().getLeanbackLaunchIntentForPackage("com.android.vending");
            if (i == null) i = getPackageManager().getLaunchIntentForPackage("com.android.vending");
            if (i != null) open(i);
        });
        focusFx(wrap, t, 1.1f, null);
        return wrap;
    }

    /** Every app tile of "Seus apps", line by line. */
    private List<View> appTiles() {
        List<View> out = new ArrayList<>();
        for (int i = 0; i < appsRow.getChildCount(); i++) {
            View l = appsRow.getChildAt(i);
            if (l instanceof LinearLayout) for (int j = 0; j < ((LinearLayout) l).getChildCount(); j++) out.add(((LinearLayout) l).getChildAt(j));
        }
        return out;
    }

    private void focusDefault() {
        ui.post(() -> {
            if (layer != null || sheet != null || page != HOME) return;
            if (tabs != null && tabs.length > HOME && tabs[HOME] != null) tabs[HOME].requestFocus();
        });
    }

    private void fillGrid() {
        builtGridPages.add(page);
        grid.removeAllViews();
        List<View> cards = new ArrayList<>();
        int perLine;
        boolean hasMoreTitles = false;
        if (page == APPS && managingApps) {
            fillAppManager();
            return;
        }
 else if (page == APPS) {
            perLine = 6;
            int cellWidth = appGridCellWidth(perLine, 14);
            int cellHeight = Math.round(cellWidth * 61f / 108f);
            cards.add(appManagerCard(cellWidth, cellHeight));
            for (App a : sortedApps()) cards.add(appCard(a, cellWidth, cellHeight, true));
        } else {
            perLine = 4;
            String kind = page == MOVIES ? "Filme" : "Série";
            int visibleCount = page == MOVIES ? movieVisibleCount : seriesVisibleCount;
            int matchingCount = 0;
            for (Tmdb.Pick p : picks) {
                if (!kind.equals(p.kind)) continue;
                if (matchingCount < visibleCount) {
                    View card = pickCard(p, 196, 110);
                    card.setTag("tmdb-grid-title-" + matchingCount);
                    cards.add(card);
                }
                matchingCount++;
            }
            if (matchingCount == 0) {
                TextView none = text(Tmdb.enabled() ? "Carregando destaques…" : "Destaques indisponíveis.", 16, false);
                none.setTextColor(SOFT);
                grid.addView(none);
                return;
            }
            hasMoreTitles = matchingCount > cards.size();
        }
        LinearLayout line = null;
        for (int i = 0; i < cards.size(); i++) {
            if (i % perLine == 0) {
                line = new LinearLayout(this);
                line.setClipChildren(false);
                grid.addView(line, lp(0, 0, 0, px(page == APPS ? 16 : 18)));
            }
            int rightGap = page == APPS ? (i % perLine == perLine - 1 || i == cards.size() - 1 ? 0 : 14) : 24;
            line.addView(cards.get(i), lp(0, 0, px(rightGap), 0));
        }
        if (hasMoreTitles) addLoadMoreTitlesButton();
    }

    private void addLoadMoreTitlesButton() {
        TextView more = text("Carregar mais", 15, true);
        more.setTag("tmdb-load-more");
        more.setGravity(Gravity.CENTER);
        more.setPadding(px(24), px(10), px(24), px(10));
        more.setTextColor(WHITE);
        more.setFocusable(true);
        more.setClickable(true);
        more.setBackground(pill(0x332F80ED, 0x668FC5FF, px(22)));
        more.setOnFocusChangeListener((v, focused) ->
                more.setBackground(pill(focused ? 0xFF347ED1 : 0x332F80ED,
                        focused ? WHITE : 0x668FC5FF, px(22))));
        more.setOnClickListener(v -> loadMoreTitles());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, px(48));
        params.gravity = Gravity.CENTER_HORIZONTAL;
        params.topMargin = px(18);
        params.bottomMargin = px(12);
        grid.addView(more, params);
    }

    private void loadMoreTitles() {
        int targetPage = page;
        int focusIndex;
        if (targetPage == MOVIES) {
            focusIndex = movieVisibleCount;
            movieVisibleCount += TITLE_PAGE_SIZE;
        } else if (targetPage == SERIES) {
            focusIndex = seriesVisibleCount;
            seriesVisibleCount += TITLE_PAGE_SIZE;
        } else {
            return;
        }
        fillGrid();
        gridPage.post(() -> {
            if (page != targetPage) return;
            View target = findGridTitle("tmdb-grid-title-" + focusIndex);
            if (target != null) target.requestFocus();
        });
    }

    private View findGridTitle(String tag) {
        for (int row = 0; row < grid.getChildCount(); row++) {
            View child = grid.getChildAt(row);
            if (tag.equals(child.getTag())) return child;
            if (child instanceof ViewGroup) {
                ViewGroup line = (ViewGroup) child;
                for (int i = 0; i < line.getChildCount(); i++) {
                    View card = line.getChildAt(i);
                    if (tag.equals(card.getTag())) return card;
                }
            }
        }
        return null;
    }







    /** Adds only the next visible batch; later rows are created as the viewer scrolls. */




    private View appManagerCard(int width, int height) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setFocusable(true);
        col.setClickable(true);
        FrameLayout tile = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF30547A, 0xFF18253A});
        bg.setCornerRadius(px(12));
        tile.setBackground(bg);
        tile.setOutlineProvider(new Round(px(12)));
        tile.setClipToOutline(true);
        tile.setElevation(px(4));
        TextView icon = text("⚙", Math.max(25, width / 4), true);
        icon.setTextColor(WHITE);
        icon.setGravity(Gravity.CENTER);
        tile.addView(icon, new FrameLayout.LayoutParams(-1, -1));
        col.addView(tile, new LinearLayout.LayoutParams(px(width), px(height)));
        TextView name = text("Gerenciar apps", width < 140 ? 11 : 13, true);
        name.setTextColor(SOFT);
        name.setMaxWidth(px(width));
        name.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(name, lp(px(2), px(width < 140 ? 5 : 8), 0, 0));
        col.setOnClickListener(v -> {
            managingApps = true;
            enteringAppManager = true;
            if (page != APPS) showPage(APPS);
            else fillGrid();
            gridPage.post(() -> gridPage.scrollTo(0, 0));
        });
        focusFx(col, tile, 1.08f, null);
        return col;
    }

    /** Fill the usable width with six app tiles and five equal gaps. */
    private int appGridCellWidth(int columns, int gap) {
        int available = getResources().getDisplayMetrics().widthPixels - 2 * px(SIDE)
                - 2 * px(GRID_SAFE_INSET) + 2 * px(8);
        int gaps = px(gap) * (columns - 1);
        int fitDp = Math.round(Math.max(0, available - gaps) / dp / columns);
        return Math.max(68, fitDp);
    }

    private void fillAppManager() {
        appManagerBack = null;
        appManagerFirst = null;
        appManagerFirstRow = null;
        TextView title = label("Apps que aparecem na tela");
        grid.addView(title, lp(0, 0, 0, px(6)));
        TextView hint = text("Selecione um ícone para mostrar ou ocultar em Seus apps.", 14, false);
        hint.setTextColor(SOFT);
        grid.addView(hint, lp(0, 0, 0, px(14)));
        TextView back = text("←  Voltar aos apps", 15, true);
        back.setId(View.generateViewId());
        back.setFocusable(true);
        back.setFocusableInTouchMode(true);
        back.setClickable(true);
        back.setPadding(px(18), px(10), px(18), px(10));
        back.setTextColor(WHITE);
        back.setBackground(pill(0x33FFFFFF, 0x22FFFFFF, px(18)));
        back.setOnFocusChangeListener((v, has) -> {
            back.setBackground(pill(has ? 0xFFFFFFFF : 0x33FFFFFF,
                    has ? 0xFFFFFFFF : 0x22FFFFFF, px(18)));
            back.setTextColor(has ? Color.BLACK : WHITE);
        });
        back.setOnClickListener(v -> { managingApps = false; fillGrid(); gridPage.scrollTo(0, 0); });
        appManagerBack = back;
        back.setOnKeyListener((v, code, e) -> {
            if (e.getAction() == KeyEvent.ACTION_DOWN && code == KeyEvent.KEYCODE_DPAD_DOWN && appManagerFirst != null) {
                appManagerFirst.requestFocus();
                return true;
            }
            return false;
        });
        grid.addView(back, lp(0, 0, 0, px(18)));

        List<App> all = new ArrayList<>(apps);
        for (int i = all.size() - 1; i >= 0; i--) if (all.get(i).system && !isLauncherSystemException(all.get(i))) all.remove(i);
        Collections.sort(all, (a, b) -> a.label.compareToIgnoreCase(b.label));
        if (all.isEmpty()) {
            TextView empty = text("Nenhum app instalável encontrado.", 16, false);
            empty.setTextColor(SOFT);
            grid.addView(empty);
            if (enteringAppManager) {
                enteringAppManager = false;
                back.requestFocus();
            }
            return;
        }
        LinearLayout line = null;
        int columns = 6;
        int cellWidth = appGridCellWidth(columns, 14);
        int cellHeight = Math.round(cellWidth * 62f / 110f);
        for (int i = 0; i < all.size(); i++) {
            if (i % columns == 0) {
                line = new LinearLayout(this);
                line.setClipChildren(false);
                grid.addView(line, lp(0, 0, 0, px(15)));
                if (i == 0) appManagerFirstRow = line;
            }
            View card = appVisibilityCard(all.get(i), i / columns, cellWidth, cellHeight);
            if (appManagerFirst == null) appManagerFirst = card;
            line.addView(card, lp(0, 0, px(i % columns == columns - 1 || i == all.size() - 1 ? 0 : 14), 0));
        }
        if (enteringAppManager) {
            enteringAppManager = false;
            if (appManagerFirst != null) {
                back.setNextFocusDownId(appManagerFirst.getId());
                appManagerFirst.setNextFocusUpId(back.getId());
            }
            gridPage.post(() -> {
                if (managingApps && page == APPS && appManagerBack == back) {
                    back.requestFocus();
                    gridPage.postDelayed(() -> {
                        if (managingApps && page == APPS && appManagerBack == back) gridPage.scrollTo(0, 0);
                    }, 250);
                }
            });
        }
    }

    private void refreshGridKeepingPlace() {
        View focused = getCurrentFocus();
        Object key = focused == null ? null : focused.getTag(R_KEY);
        int y = gridPage.getScrollY();
        fillGrid();
        gridPage.scrollTo(0, y);
        if (key != null) {
            View same = grid.findViewWithTag(key);
            if (same != null) same.requestFocus();
        }
    }

    private static final int R_KEY = 0x7f0b0001;

    private View appVisibilityCard(App a, int rowIndex, int width, int height) {
        boolean visible = !hidden(a);
        FrameLayout tile = tile(null, a.banner != null ? a.banner : a.icon, px(12), a.banner == null);
        if (tile.getChildCount() > 0) tile.getChildAt(0).setAlpha(visible ? 1f : 0.35f);
        TextView badge = text(visible ? "✓" : "×", 15, true);
        badge.setTextColor(WHITE);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(pill(visible ? 0xE62E8055 : 0xE6383D48,
                visible ? 0xAA9BE7B5 : 0xAAFFFFFF, px(18)));
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(px(26), px(26), Gravity.TOP | Gravity.RIGHT);
        bp.setMargins(0, px(5), px(5), 0);
        tile.addView(badge, bp);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setFocusable(true);
        col.setFocusableInTouchMode(true);
        col.setClickable(true);
        col.setId(View.generateViewId());
        col.setTag(R_KEY, "appvis:" + a.pkg);
        if (rowIndex == 0 && appManagerBack != null) col.setNextFocusUpId(appManagerBack.getId());
        col.setContentDescription(a.label + (visible ? ", visível. Toque para ocultar." : ", oculto. Toque para mostrar."));
        col.setOnKeyListener((v, code, e) -> {
            if (e.getAction() == KeyEvent.ACTION_DOWN && code == KeyEvent.KEYCODE_DPAD_UP
                    && rowIndex == 0 && appManagerBack != null) {
                appManagerBack.requestFocus();
                return true;
            }
            return false;
        });
        col.addView(tile, new LinearLayout.LayoutParams(px(width), px(height)));
        TextView name = text(a.label, 11, true);
        name.setTextColor(visible ? WHITE : SOFT);
        name.setSingleLine(true);
        name.setMaxWidth(px(width));
        name.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(name, lp(px(2), px(5), 0, 0));
        col.setOnClickListener(v -> {
            boolean show = hidden(a);
            String key = kidsAge() > 0 ? "kid:" + a.pkg : "hide:" + a.pkg;
            prefs.edit().putBoolean(key, kidsAge() > 0 ? show : !show).apply();
            refreshGridKeepingPlace();
        });
        focusFx(col, tile, 1.08f, null);
        return col;
    }

    // ------------------------------------------------------------------ cards









    private FrameLayout tile(Bitmap img, Drawable fallback, int radius, boolean fit) {
        FrameLayout t = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF1C2230);
        bg.setCornerRadius(radius);
        t.setBackground(bg);
        t.setOutlineProvider(new Round(radius));
        t.setClipToOutline(true);
        t.setElevation(px(4));
        ImageView v = new ImageView(this);
        if (img != null) v.setImageBitmap(img);
        else v.setImageDrawable(fallback);
        v.setScaleType(fit && img == null ? ImageView.ScaleType.FIT_CENTER : ImageView.ScaleType.CENTER_CROP);
        t.addView(v, new FrameLayout.LayoutParams(-1, -1));
        return t;
    }

    private void focusFx(View v, FrameLayout t, float scale, Runnable onFocus) {
        GradientDrawable ring = new GradientDrawable();
        ring.setColor(Color.TRANSPARENT);
        ring.setCornerRadius(px(12));
        ring.setStroke(px(prefs != null && prefs.getBoolean("focusBold", false) ? 5 : 3), WHITE);
        final float focusedScale = scale + (prefs != null && prefs.getBoolean("focusLarge", false) ? 0.08f : 0f);
        v.setOnFocusChangeListener((x, has) -> {
            x.animate().scaleX(has ? focusedScale : 1f).scaleY(has ? focusedScale : 1f).setDuration(160).start();
            x.setTranslationZ(has ? px(16) : 0);
            t.setForeground(has ? ring : null);
            if (has && x.getParent() instanceof View && ((View) x.getParent()).getParent() instanceof HorizontalScrollView) {
                // Keep the focused card at the left margin, like Google TV, so it never ends up under the TV's edge.
                ((HorizontalScrollView) ((View) x.getParent()).getParent()).smoothScrollTo(Math.max(0, x.getLeft() - px(SIDE)), 0);
            }
            if (has && onFocus != null) onFocus.run();
        });
    }

    private View pickCard(Tmdb.Pick p, int w, int h) {
        // Keep the full 780 px cached banner source for crisp artwork on large TV cards.
        FrameLayout t = tile(decode(p.thumb), null, px(12), false);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setFocusable(true);
        col.setClickable(true);
        col.addView(t, new LinearLayout.LayoutParams(px(w), px(h)));
        if (page != HOME) {
            TextView name = text(p.title, 14, true);
            name.setTextColor(WHITE);
            name.setMaxWidth(px(w));
            name.setEllipsize(TextUtils.TruncateAt.END);
            col.addView(name, lp(px(2), px(9), 0, 0));
            App a = byPkg.get(p.pkg);
            TextView s = text(a != null ? a.label : p.provider, 12, false);
            s.setTextColor(DIM);
            col.addView(s, lp(px(2), px(4), 0, 0));
        }
        col.setOnClickListener(v -> openTitle(p.pkg, p.title));
        focusFx(col, t, 1.08f, null);
        // Every title carries its app's logo and name in the resume row.
        App owner = byPkg.get(p.pkg);
        if (owner != null && page == HOME) footer(t, owner.banner != null ? owner.banner : owner.icon, p.title);
        return col;
    }

    /** Shade at the bottom of a card with the app's logo and the title. */
    private void footer(FrameLayout t, Drawable logoImg, String titleText) {
        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x00000000, 0xE6000000}));
        t.addView(shade, new FrameLayout.LayoutParams(-1, px(52), Gravity.BOTTOM));
        LinearLayout foot = new LinearLayout(this);
        foot.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setImageDrawable(logoImg);
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        logo.setOutlineProvider(new Round(px(4)));
        logo.setClipToOutline(true);
        foot.addView(logo, new LinearLayout.LayoutParams(px(38), px(22)));
        TextView title = text(titleText, 11, true);
        title.setTextColor(WHITE);
        title.setEllipsize(TextUtils.TruncateAt.END);
        foot.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        ((LinearLayout.LayoutParams) title.getLayoutParams()).setMargins(px(7), 0, 0, 0);
        FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        fl.setMargins(px(8), 0, px(8), px(7));
        t.addView(foot, fl);
    }

    private boolean isMusicApp(String pkg) {
        return "com.spotify.tv.android".equals(pkg)
                || "com.google.android.apps.youtube.music".equals(pkg)
                || "com.amazon.mp3".equals(pkg)
                || "com.apple.android.music".equals(pkg)
                || "deezer.android.app".equals(pkg)
                || "com.aspiro.tidal".equals(pkg)
                || "com.soundcloud.android".equals(pkg);
    }

    private View continueCard(App a, History.Item it, boolean showEqualizer) {
        Bitmap art = decode(it.art, 1);
        Drawable banner = a.banner != null ? a.banner : a.icon;
        boolean appLogo = art != null && (sameImage(art, a.icon) || sameImage(art, a.banner));
        if (appLogo) art = null;
        FrameLayout t = tile(art != null ? art : blur(soft(banner)), null, px(12), false);
        if (art == null) {
            ImageView mid = new ImageView(this);
            mid.setImageDrawable(a.icon != null ? a.icon : banner);
            mid.setScaleType(ImageView.ScaleType.FIT_CENTER);
            t.addView(mid, new FrameLayout.LayoutParams(px(52), px(30), Gravity.CENTER));
        }
        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x00000000, 0xE6000000}));
        t.addView(shade, new FrameLayout.LayoutParams(-1, px(38), Gravity.BOTTOM));
        LinearLayout foot = new LinearLayout(this);
        foot.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setImageDrawable(banner);
        logo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        logo.setOutlineProvider(new Round(px(4)));
        logo.setClipToOutline(true);
        foot.addView(logo, new LinearLayout.LayoutParams(px(28), px(16)));
        TextView title = text(it.title, 10, true);
        title.setTextColor(WHITE);
        title.setEllipsize(TextUtils.TruncateAt.END);
        foot.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        ((LinearLayout.LayoutParams) title.getLayoutParams()).setMargins(px(5), 0, 0, 0);
        if (showEqualizer) {
            EqualizerIndicator equalizer = new EqualizerIndicator(this);
            equalizer.setContentDescription("Reproduzindo agora");
            LinearLayout.LayoutParams eqp = new LinearLayout.LayoutParams(px(14), px(14));
            eqp.setMargins(px(6), 0, 0, 0);
            foot.addView(equalizer, eqp);
        }
        FrameLayout.LayoutParams fl = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        fl.setMargins(px(6), 0, px(6), px(it.dur > 0 && it.pos >= 1000 ? 8 : 5));
        t.addView(foot, fl);
        if (it.dur > 0 && it.pos >= 1000) t.addView(progress(it), new FrameLayout.LayoutParams(-1, px(3), Gravity.BOTTOM));
        FrameLayout wrap = new FrameLayout(this);
        wrap.setFocusable(true);
        wrap.setClickable(true);
        wrap.addView(t, new FrameLayout.LayoutParams(px(128), px(72)));
        wrap.setOnClickListener(v -> resume(a, it));
        wrap.setOnKeyListener((v, code, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && code == KeyEvent.KEYCODE_DPAD_DOWN
                    && focusFirstHomeSectionBelowContinue()) return true;
            return false;
        });
        focusFx(wrap, t, 1.08f, null);
        return wrap;
    }

    /** Small animated playback indicator for the current item. */
    private static final class EqualizerIndicator extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float density;

        EqualizerIndicator(android.content.Context context) {
            super(context);
            density = context.getResources().getDisplayMetrics().density;
            paint.setColor(Color.rgb(91, 239, 185));
            setFocusable(false);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float width = getWidth(), height = getHeight();
            float barWidth = Math.max(2f * density, width * 0.17f);
            float gap = (width - 3f * barWidth) / 4f;
            float now = android.os.SystemClock.uptimeMillis() / 180f;
            for (int i = 0; i < 3; i++) {
                float level = 0.28f + 0.72f * (0.5f + 0.5f * (float) Math.sin(now + i * 1.7f));
                float barHeight = Math.max(4f * density, height * level);
                float left = gap + i * (barWidth + gap);
                float top = (height - barHeight) / 2f;
                canvas.drawRoundRect(left, top, left + barWidth, top + barHeight,
                        barWidth / 2f, barWidth / 2f, paint);
            }
            postInvalidateDelayed(220);
        }
    }

    /** Detect provider logos reused as "art" so they stay a small centered app mark. */
    private boolean sameImage(Bitmap art, Drawable candidate) {
        if (art == null || candidate == null) return false;
        Bitmap left = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888);
        Bitmap right = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888);
        try {
            Canvas lc = new Canvas(left);
            Canvas rc = new Canvas(right);
            lc.drawColor(Color.BLACK);
            rc.drawColor(Color.BLACK);
            lc.drawBitmap(art, null, fitRect(art.getWidth(), art.getHeight(), 32, 32), null);
            Rect old = new Rect(candidate.getBounds());
            int iw = candidate.getIntrinsicWidth() > 0 ? candidate.getIntrinsicWidth() : 32;
            int ih = candidate.getIntrinsicHeight() > 0 ? candidate.getIntrinsicHeight() : 32;
            candidate.setBounds(fitRect(iw, ih));
            candidate.draw(rc);
            candidate.setBounds(old);
            int[] a = new int[32 * 32], b = new int[32 * 32];
            left.getPixels(a, 0, 32, 0, 0, 32, 32);
            right.getPixels(b, 0, 32, 0, 0, 32, 32);
            long diff = 0;
            for (int i = 0; i < a.length; i++) {
                diff += Math.abs(Color.red(a[i]) - Color.red(b[i]));
                diff += Math.abs(Color.green(a[i]) - Color.green(b[i]));
                diff += Math.abs(Color.blue(a[i]) - Color.blue(b[i]));
            }
            return diff / (a.length * 3) <= 18;
        } finally {
            left.recycle();
            right.recycle();
        }
    }

    private Rect fitRect(int width, int height) {
        float scale = Math.min(32f / Math.max(1, width), 32f / Math.max(1, height));
        int w = Math.max(1, Math.round(width * scale));
        int h = Math.max(1, Math.round(height * scale));
        int x = (32 - w) / 2;
        int y = (32 - h) / 2;
        return new Rect(x, y, x + w, y + h);
    }

    private Rect fitRect(int width, int height, int canvasWidth, int canvasHeight) {
        float scale = Math.min(canvasWidth / (float) Math.max(1, width), canvasHeight / (float) Math.max(1, height));
        int w = Math.max(1, Math.round(width * scale));
        int h = Math.max(1, Math.round(height * scale));
        int x = (canvasWidth - w) / 2;
        int y = (canvasHeight - h) / 2;
        return new Rect(x, y, x + w, y + h);
    }

    private View appCard(App a, int w, int h, boolean dock) {
        FrameLayout t = tile(null, a.banner != null ? a.banner : a.icon, px(12), a.banner == null);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setFocusable(true);
        col.setClickable(true);
        col.setTag(a);
        col.addView(t, new LinearLayout.LayoutParams(px(w), px(h)));
        if (page == APPS && isGridBuild) {
            TextView name = text(a.label, 12, false);
            name.setTextColor(SOFT);
            name.setMaxWidth(px(w));
            name.setEllipsize(TextUtils.TruncateAt.END);
            col.addView(name, lp(px(2), px(5), 0, 0));
        }
        col.setOnClickListener(v -> launch(a));
        col.setOnLongClickListener(v -> {
            options(a);
            return true;
        });
        if (dock) {
            col.setOnKeyListener((v, code, e) -> {
                // Last row: pressing down just stays put.
                if (code == KeyEvent.KEYCODE_DPAD_DOWN && isInside(v, appsRow)
                        && v.getParent() == appsRow.getChildAt(appsRow.getChildCount() - 1)) return true;
                return false;
            });
        }
        focusFx(col, t, 1.1f, null);
        return col;
    }

    private boolean isGridBuild = true;

    private View progress(History.Item it) {
        LinearLayout bar = new LinearLayout(this);
        bar.setBackgroundColor(0x66FFFFFF);
        View fill = new View(this);
        fill.setBackgroundColor(0xFFE50914);
        float f = Math.max(0.02f, Math.min(1f, it.pos / (float) it.dur));
        bar.addView(fill, new LinearLayout.LayoutParams(0, -1, f));
        bar.addView(new View(this), new LinearLayout.LayoutParams(0, -1, 1f - f));
        return bar;
    }

    private CharSequence ago(long t) {
        long d = System.currentTimeMillis() - t;
        return d < 60_000 ? "agora" : DateUtils.getRelativeTimeSpanString(t, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
    }

    // ------------------------------------------------------------------ history panel

    /** Moving down a row slides the page up just enough to show it whole (and back), like Google TV. */
    private void setRowFocus(View focused) {
        View row = isInside(focused, continueRow) || isInside(focused, frequentRow) ? continueSplit : null;
        int bottom = 0;
        if (row != null) bottom = homePage.getTop() + row.getBottom();
        if (isInside(focused, watchlistRow)) {
            row = watchlistScroll;
            bottom = homePage.getTop() + row.getBottom();
        } else if (isInside(focused, weatherRow)) {
            row = weatherRow;
            bottom = homePage.getTop() + weatherRow.getBottom();
        } else if (isInside(focused, cinemaRow)) {
            row = cinemaScroll;
            bottom = homePage.getTop() + row.getBottom();
        } else if (isInside(focused, reminderRow)) {
            row = reminderScroll;
            bottom = homePage.getTop() + row.getBottom();
        }
        if (isInside(focused, appsRow) && focused.getParent() instanceof View) {
            View line = (View) focused.getParent();
            bottom = homePage.getTop() + appsRow.getTop() + line.getBottom() + px(8);
            row = line;
        }
        int shift = 0;
        if (row != null) {
            shift = Math.min(0, rootView.getHeight() - px(34) - bottom);
        }
        homePage.animate().translationY(shift).setDuration(260).start();
        heroInfo.animate().alpha(shift < -px(40) ? 0f : 1f).setDuration(200).start();
        android.animation.ObjectAnimator.ofInt(topShade, "alpha", topShade.getAlpha(), shift < 0 ? 255 : 0).setDuration(220).start();
        scrimFull.animate().alpha(shift < 0 ? 0.35f : 0f).setDuration(260).start();
    }

    /** DPAD-down from Continue must enter the next visible home section, not jump to a distant app tile. */
    private boolean focusFirstHomeSectionBelowContinue() {
        View[] sections = {watchlistScroll, weatherRow, cinemaScroll, reminderScroll, appsRow};
        for (View section : sections) {
            if (section == null || section.getVisibility() != View.VISIBLE) continue;
            View target = firstFocusableContent(section);
            if (target != null && target.requestFocus()) return true;
        }
        return false;
    }

    private View firstFocusableContent(View view) {
        if (view == null || view.getVisibility() != View.VISIBLE || !view.isEnabled()) return null;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View target = firstFocusableContent(group.getChildAt(i));
                if (target != null) return target;
            }
        }
        return view.isFocusable() ? view : null;
    }

    private void showHistory(App a) {
        homePage.setTranslationY(0);
        heroInfo.setAlpha(1f);
        histPkg = a.pkg;
        heroInfo.setVisibility(View.GONE);
        forYouLabel.setVisibility(View.GONE);
        forYouScroll.setVisibility(View.GONE);
        histLabel.setVisibility(View.VISIBLE);
        histScroll.setVisibility(View.VISIBLE);
        scrimFull.animate().alpha(0.6f).setDuration(250).start();
        fillHistory(a);
        histScroll.scrollTo(0, 0);
        View first = histRow.getChildCount() > 0 ? histRow.getChildAt(0) : null;
        if (first != null && first.isFocusable()) first.requestFocus();
    }

    private void fillHistory(App a) {
        histLabel.setText("Assistido no " + a.label);
        histRow.removeAllViews();
        List<History.Item> items = History.get(this, a.pkg);
        if (items.isEmpty()) {
            TextView none = text("Nada por aqui ainda. O que você assistir no " + a.label + " aparece nesta fileira.", 16, false);
            none.setTextColor(SOFT);
            histRow.addView(none, lp(px(2), px(8), 0, 0));
            return;
        }
        for (History.Item it : items) histRow.addView(historyCard(a, it), lp(0, 0, px(20), 0));
    }

    private View historyCard(App a, History.Item it) {
        int w = 256, h = 144;
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setFocusable(true);
        col.setClickable(true);
        FrameLayout t = tile(decode(it.art), a.banner != null ? a.banner : a.icon, px(12), false);
        if (it.dur > 0) t.addView(progress(it), new FrameLayout.LayoutParams(-1, px(4), Gravity.BOTTOM));
        col.addView(t, new LinearLayout.LayoutParams(px(w), px(h)));
        TextView name = text(it.title, 15, true);
        name.setTextColor(WHITE);
        name.setMaxWidth(px(w));
        name.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(name, lp(px(2), px(10), 0, 0));
        String sub = (it.sub == null || it.sub.isEmpty() ? "" : it.sub + " · ")
                + (it.dur > 0 && it.pos >= 1000 ? "parou em " + DateUtils.formatElapsedTime(it.pos / 1000) + " · " : "") + ago(it.time);
        TextView s = text(sub, 13, false);
        s.setTextColor(DIM);
        s.setMaxWidth(px(w));
        s.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(s, lp(px(2), px(4), 0, 0));
        col.setOnClickListener(v -> launch(a));
        col.setOnKeyListener((v, code, e) -> {
            if (code == KeyEvent.KEYCODE_DPAD_UP && e.getAction() == KeyEvent.ACTION_DOWN) {
                hideHistory(true);
                return true;
            }
            return code == KeyEvent.KEYCODE_DPAD_DOWN;
        });
        focusFx(col, t, 1.06f, null);
        return col;
    }

    private void hideHistory(boolean refocus) {
        if (histPkg == null) return;
        String pkg = histPkg;
        histPkg = null;
        histLabel.setVisibility(View.GONE);
        histScroll.setVisibility(View.GONE);
        histRow.removeAllViews();
        heroInfo.setVisibility(View.VISIBLE);
        appsLabel.setAlpha(0f);
        forYouLabel.setVisibility(forYouRow.getChildCount() > 0 ? View.VISIBLE : View.GONE);
        forYouScroll.setVisibility(View.VISIBLE);
        scrimFull.animate().alpha(0f).setDuration(250).start();
        if (refocus) {
            for (View v : appTiles()) {
                if (v.getTag() instanceof App && ((App) v.getTag()).pkg.equals(pkg)) {
                    v.requestFocus();
                    return;
                }
            }
        }
    }

    /**
     * Opens a title inside its app. Services don't publish links to their titles, so this lands on the app's own
     * search for the exact name (the title is the first result); apps without a reachable search just open.
     */
    private void openTitle(String pkg, String title) {
        App a = byPkg.get(pkg);
        if (a == null) return;
        prefs.edit().putInt("n:" + pkg, prefs.getInt("n:" + pkg, 0) + 1).putLong("t:" + pkg, System.currentTimeMillis()).apply();
        Intent i = Search.inApp(this, pkg, title);
        if (i != null) open(i);
        else launch(a);
    }

    // ------------------------------------------------------------------ resuming what you were watching

    /** Resume an exact video link or matching media session; never substitute a YouTube title search. */
    private void resume(App a, History.Item it) {
        History.claimPackage(this, a.pkg);
        prefs.edit().putInt("n:" + a.pkg, prefs.getInt("n:" + a.pkg, 0) + 1).putLong("t:" + a.pkg, System.currentTimeMillis()).apply();
        if (a.pkg.equals("com.google.android.youtube.tv")) {
            String id = youtubeVideoId(it.mediaUri);
            if (id == null) id = youtubeVideoId(it.mediaId);
            if (id != null) {
                // YouTube TV uses its living-room deep-link route; watch?v can be
                // interpreted by some TV builds as a search/navigation request.
                android.net.Uri video = android.net.Uri.parse(
                        "https://www.youtube.com/tv?launch=launcher&v=" + android.net.Uri.encode(id));
                Intent direct = new Intent(Intent.ACTION_VIEW, video)
                        .addCategory(Intent.CATEGORY_BROWSABLE).setPackage(a.pkg);
                if (getPackageManager().resolveActivity(direct, 0) != null) {
                    open(direct);
                    return;
                }
            }
            // YouTube TV exposes neither a video id nor a media URI in its media session. When the
            // matching video session still exists, resume that exact session rather than searching.
            try {
                android.media.session.MediaSessionManager msm =
                        (android.media.session.MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
                for (android.media.session.MediaController c : msm.getActiveSessions(new ComponentName(this, MediaListener.class))) {
                    if (!c.getPackageName().equals(a.pkg) || c.getMetadata() == null) continue;
                    CharSequence title = c.getMetadata().getText(android.media.MediaMetadata.METADATA_KEY_TITLE);
                    CharSequence shown = c.getMetadata().getText(android.media.MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
                    if (!it.title.equals(String.valueOf(title).trim()) && !it.title.equals(String.valueOf(shown).trim())) continue;
                    if (c.getSessionActivity() != null) c.getSessionActivity().send();
                    if (it.pos > 0) c.getTransportControls().seekTo(it.pos);
                    c.getTransportControls().play();
                    if (c.getSessionActivity() == null) launch(a);
                    return;
                }
            } catch (Exception e) {
                android.util.Log.w("Sala", "Retomar sessão do YouTube: " + e);
            }
            // Older entries can lack a content ID because YouTube TV omits it from its media
            // session. Open the app rather than sending an ambiguous search for the title.
            launch(a);
            return;
        }
        if (openStoredResumeUri(a.pkg, it.mediaUri)) return;
        // Netflix and Prime often publish a media notification whose content intent
        // is the only app-supported route back to the current player.
        if (MediaListener.openResumeNotification(a.pkg, it.title)) return;

        // Apps that omit a content URI can still resume the current player through its media session.
        boolean generic = it.title.startsWith("Continuar no ");
        try {
            android.media.session.MediaSessionManager msm =
                    (android.media.session.MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
            for (android.media.session.MediaController c : msm.getActiveSessions(new ComponentName(this, MediaListener.class))) {
                if (!c.getPackageName().equals(a.pkg)) continue;
                android.media.session.PlaybackState state = c.getPlaybackState();
                if (state == null || (state.getState() != android.media.session.PlaybackState.STATE_PLAYING
                        && state.getState() != android.media.session.PlaybackState.STATE_PAUSED
                        && state.getState() != android.media.session.PlaybackState.STATE_BUFFERING)) continue;
                android.media.MediaMetadata metadata = c.getMetadata();
                CharSequence title = metadata == null ? null : metadata.getText(android.media.MediaMetadata.METADATA_KEY_TITLE);
                CharSequence shown = metadata == null ? null : metadata.getText(android.media.MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
                String sessionTitle = title == null ? (shown == null ? "" : shown.toString().trim()) : title.toString().trim();
                boolean same = generic || sessionTitle.isEmpty() || it.title.equals(sessionTitle)
                        || (shown != null && it.title.equals(shown.toString().trim()));
                if (!same) continue;
                if (c.getSessionActivity() != null) c.getSessionActivity().send();
                else launch(a);
                c.getTransportControls().play();
                return;
            }
        } catch (Exception e) {
            android.util.Log.w("Sala", "Retomar pela sessão: " + e);
        }
        if (generic) {
            launch(a);
            return;
        }
        if (it.mediaUri != null && !it.mediaUri.trim().isEmpty() && !"null".equalsIgnoreCase(it.mediaUri.trim())) {
            Intent i = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(it.mediaUri.trim())).setPackage(a.pkg);
            if (getPackageManager().resolveActivity(i, 0) != null) {
                open(i);
                return;
            }
        }
        // A title search is not a resume action: it can select a different item.
        // Without a content URI or a matching live media session, open the app only.
        launch(a);
    }

    private boolean openStoredResumeUri(String pkg, String value) {
        if (value == null || value.trim().isEmpty() || "null".equalsIgnoreCase(value.trim())) return false;
        try {
            Intent intent;
            if (value.startsWith("intent:")) {
                intent = Intent.parseUri(value, Intent.URI_INTENT_SCHEME);
                if (intent.getComponent() != null && !pkg.equals(intent.getComponent().getPackageName())) return false;
                if (intent.getPackage() != null && !pkg.equals(intent.getPackage())) return false;
                if (intent.getPackage() == null && intent.getComponent() == null) intent.setPackage(pkg);
            } else {
                intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(value)).setPackage(pkg);
            }
            if (getPackageManager().resolveActivity(intent, 0) == null) return false;
            open(intent);
            return true;
        } catch (Exception e) {
            android.util.Log.i("Sala", "URI de retomada indisponível para " + pkg + ": " + e.getClass().getSimpleName());
            return false;
        }
    }

    private static String youtubeVideoId(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        String candidate = value.trim();
        if (candidate.matches("[A-Za-z0-9_-]{11}")) return candidate;
        try {
            android.net.Uri uri = android.net.Uri.parse(candidate);
            String host = uri.getHost();
            if (host != null && (host.equals("youtu.be") || host.endsWith(".youtu.be"))) {
                String path = uri.getLastPathSegment();
                if (path != null && path.matches("[A-Za-z0-9_-]{11}")) return path;
            }
            String queryId = uri.getQueryParameter("v");
            if (queryId != null && queryId.matches("[A-Za-z0-9_-]{11}")) return queryId;
            for (String part : uri.getPathSegments()) if (part.matches("[A-Za-z0-9_-]{11}")) return part;
        } catch (Exception ignored) { }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                "(?:/vi(?:_webp)?/|/embed/|/shorts/|/live/)([A-Za-z0-9_-]{11})(?:[/?.]|$)").matcher(candidate);
        return matcher.find() ? matcher.group(1) : null;
    }

    // ------------------------------------------------------------------ actions

    private void launch(App a) {
        History.claimPackage(this, a.pkg);
        prefs.edit().putInt("n:" + a.pkg, prefs.getInt("n:" + a.pkg, 0) + 1)
                .putLong("t:" + a.pkg, System.currentTimeMillis()).apply();
        try {
            startActivity(a.intent);
        } catch (Exception e) {
            android.util.Log.w("SalaRemote", "Falha ao abrir " + a.pkg + "; tentando o launcher do pacote", e);
            PackageManager pm = getPackageManager();
            Intent i = pm.getLeanbackLaunchIntentForPackage(a.pkg);
            if (i == null) i = pm.getLaunchIntentForPackage(a.pkg);
            if (i != null) open(i);
        }
    }

    private void open(Intent i) {
        try {
            String pkg = i.getPackage();
            if (pkg == null && i.getComponent() != null) pkg = i.getComponent().getPackageName();
            if (pkg != null) History.claimPackage(this, pkg);
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) {
        }
    }

    private void options(App a) {
        if (kidsAge() > 0) {
            showSheet(a.label, a.banner != null ? a.banner : a.icon, null,
                    new String[]{"Abrir", "Assistido recentemente"}, which -> {
                        if (which == 0) launch(a); else showHistory(a);
                    });
            return;
        }
        boolean docked = inDock(a);
        boolean favorite = a.pinned > 0;
        Drawable logo = a.banner != null ? a.banner : a.icon;
        List<String> choices = new ArrayList<>(Arrays.asList("Abrir", "Mover", "Informações do app", "Assistido recentemente",
                favorite ? "Remover dos favoritos" : "Adicionar aos favoritos",
                docked ? "Tirar de Seus apps" : "Colocar em Seus apps", "Ocultar app"));
        if (!a.system) choices.add("Desinstalar app");
        showSheet(a.label, logo, null, choices.toArray(new String[0]),
                which -> {
                    String c = choices.get(which);
                    if (c.equals("Abrir")) launch(a);
                    else if (c.equals("Mover")) startMove(a);
                    else if (c.equals("Informações do app")) {
                        open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + a.pkg)));
                    } else if (c.equals("Assistido recentemente")) showHistory(a);
                    else if (c.startsWith("Remover dos") || c.startsWith("Adicionar aos")) {
                        prefs.edit().putLong("pin:" + a.pkg, favorite ? 0 : System.currentTimeMillis()).apply();
                        render();
                    } else if (c.startsWith("Tirar de") || c.startsWith("Colocar em")) {
                        prefs.edit().putBoolean("dock:" + a.pkg, !docked).apply();
                        render();
                    } else if (c.equals("Ocultar app")) {
                        prefs.edit().putBoolean("hide:" + a.pkg, true).apply();
                        render();
                    } else if (c.equals("Desinstalar app")) {
                        open(new Intent(Intent.ACTION_DELETE, android.net.Uri.parse("package:" + a.pkg)));
                    }
                });
    }

    private void menu() {
        if (kidsAge() > 0) {
            showSheet(profile.name, null, null, new String[]{"Trocar perfil"}, w -> pickProfile());
            return;
        }
        int hiddenCount = 0;
        for (App a : apps) if (hidden(a)) hiddenCount++;
        showSheet("Launcher TV", null, null,
                new String[]{"Perfis", "Mostrar apps ocultos (" + hiddenCount + ")", "Limpar histórico", "Wi-Fi",
                        "Configurações do Android", "Acessibilidade", "Temporizador de sono", "Sobre"},
                choice -> {
                    if (choice == 0) {
                        pickProfile();
                        return;
                    }
                    int which = choice - 1;
                    if (which == 0) {
                        SharedPreferences.Editor e = prefs.edit();
                        for (App a : apps) e.putBoolean("hide:" + a.pkg, false);
                        e.apply();
                        render();
                    } else if (which == 1) {
                        getSharedPreferences(History.prefs(this), MODE_PRIVATE).edit().clear().apply();
                        SharedPreferences.Editor e = prefs.edit();
                        for (App a : apps) e.remove("t:" + a.pkg);
                        e.apply();
                        File[] files = getFilesDir().listFiles();
                        if (files != null) for (File f : files) if (f.getName().startsWith("art_")) f.delete();
                        render();
                    } else if (which == 2) {
                        open(new Intent(Settings.ACTION_WIFI_SETTINGS));
                    } else if (which == 3) {
                        open(new Intent(Settings.ACTION_SETTINGS));
                    } else if (which == 4) {
                        accessibilityMenu();
                    } else if (which == 5) {
                        sleepMenu();
                    } else {
                        ui.post(() -> showSheet("Sobre", null,
                                "Destaques de filmes e séries: dados e imagens do TMDB. Este produto usa a API do TMDB, "
                                        + "mas não é endossado nem certificado pelo TMDB. Temperatura: Open-Meteo.",
                                new String[]{"OK"}, w -> { }));
                    }
                });
    }

    private void accessibilityMenu() {
        String size = prefs.getFloat("textScale", 1f) > 1.05f ? "Texto maior ✓" : "Texto maior";
        String focus = prefs.getBoolean("focusLarge", false) ? "Foco mais visível ✓" : "Foco mais visível";
        String contrast = prefs.getBoolean("focusBold", false) ? "Contorno forte ✓" : "Contorno forte";
        showSheet("Acessibilidade", null, "Ajustes para leitura e navegação pelo controle remoto.",
                new String[]{size, focus, contrast}, choice -> {
                    if (choice == 0) prefs.edit().putFloat("textScale", prefs.getFloat("textScale", 1f) > 1.05f ? 1f : 1.16f).apply();
                    else if (choice == 1) prefs.edit().putBoolean("focusLarge", !prefs.getBoolean("focusLarge", false)).apply();
                    else prefs.edit().putBoolean("focusBold", !prefs.getBoolean("focusBold", false)).apply();
                    recreate();
                });
    }

    private void sleepMenu() {
        showSheet("Temporizador de sono", null,
                        "O launcher pode avisar quando o tempo terminar. Colocar a TV em espera depende da ação que o firmware TCL permite a apps comuns.",
                new String[]{"15 minutos", "30 minutos", "60 minutos", "90 minutos", "Cancelar temporizador"}, choice -> {
                    if (choice == 4) {
                        prefs.edit().remove("sleepAt").apply();
                        hint("Temporizador cancelado.");
                        return;
                    }
                    int[] mins = {15, 30, 60, 90};
                    long at = System.currentTimeMillis() + mins[choice] * 60_000L;
                    prefs.edit().putLong("sleepAt", at).putBoolean("sleepNotified", false).apply();
                    hint("Aviso de sono definido para " + mins[choice] + " minutos.");
                });
    }

    // ------------------------------------------------------------------ sheet (the launcher's own menu)

    private void showSheet(String title, Drawable icon, String message, String[] items, java.util.function.IntConsumer onPick) {
        closeSheet(false);
        sheetReturn = getCurrentFocus();
        sheet = new FrameLayout(this);
        sheet.setBackgroundColor(0xA6000000);
        sheet.setClickable(true);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(px(22), px(22), px(22), px(16));
        panel.setBackground(pill(0xF2161B26, 0x26FFFFFF, px(22)));
        panel.setElevation(px(24));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        if (icon != null) {
            ImageView iv = new ImageView(this);
            iv.setImageDrawable(icon);
            iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
            iv.setOutlineProvider(new Round(px(8)));
            iv.setClipToOutline(true);
            head.addView(iv, new LinearLayout.LayoutParams(px(64), px(36)));
        }
        TextView t = text(title, 20, true);
        t.setTextColor(WHITE);
        head.addView(t, lp(icon != null ? px(14) : 0, 0, 0, 0));
        panel.addView(head, lp(px(6), 0, 0, px(14)));
        if (message != null) {
            TextView m = text(message, 14, false);
            m.setSingleLine(false);
            m.setTextColor(SOFT);
            m.setLineSpacing(px(3), 1f);
            panel.addView(m, lp(px(6), 0, 0, px(14)));
        }
        TextView[] rows = new TextView[items.length];
        for (int i = 0; i < items.length; i++) {
            final int which = i;
            TextView r = text(items[i], 16, true);
            r.setTextColor(WHITE);
            r.setFocusable(true);
            r.setClickable(true);
            r.setPadding(px(18), px(12), px(18), px(12));
            r.setOnFocusChangeListener((v, has) -> {
                r.setBackground(has ? pill(WHITE, WHITE, px(14)) : null);
                r.setTextColor(has ? Color.BLACK : WHITE);
            });
            r.setOnClickListener(v -> {
                closeSheet(true);
                onPick.accept(which);
            });
            final int idx = i;
            r.setOnKeyListener((v, code, e) -> {
                if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) return true;
                if (code == KeyEvent.KEYCODE_DPAD_UP) {
                    if (idx > 0) rows[idx - 1].requestFocus();
                    return true;
                }
                if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (idx < rows.length - 1) rows[idx + 1].requestFocus();
                    return true;
                }
                return false;
            });
            rows[i] = r;
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
            rl.setMargins(0, 0, 0, px(4));
            panel.addView(r, rl);
        }
        sheet.addView(panel, new FrameLayout.LayoutParams(px(380), -2, Gravity.CENTER));
        sheet.setAlpha(0f);
        rootView.addView(sheet, new FrameLayout.LayoutParams(-1, -1));
        sheet.animate().alpha(1f).setDuration(160).start();
        panel.setScaleX(0.96f);
        panel.setScaleY(0.96f);
        panel.animate().scaleX(1f).scaleY(1f).setDuration(180).start();
        rows[0].requestFocus();
    }

    private void closeSheet(boolean refocus) {
        if (sheet == null) return;
        rootView.removeView(sheet);
        sheet = null;
        if (refocus && sheetReturn != null && sheetReturn.isAttachedToWindow()) sheetReturn.requestFocus();
    }

    // ------------------------------------------------------------------ search

    private static final class SearchWatch implements android.text.TextWatcher {
        private final Runnable onChange;
        SearchWatch(Runnable onChange) { this.onChange = onChange; }
        public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
        public void onTextChanged(CharSequence s, int a, int b, int c) { }
        public void afterTextChanged(android.text.Editable s) { onChange.run(); }
    }

    /** The magnifier on the search button, drawn (no image files). */
    private static final class Magnifier extends Drawable {
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        Magnifier(int stroke) {
            paint.setStyle(android.graphics.Paint.Style.STROKE);
            paint.setStrokeWidth(stroke);
            paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
            paint.setColor(Color.WHITE);
        }
        void setColor(int c) { paint.setColor(c); invalidateSelf(); }
        @Override public void draw(Canvas c) {
            float w = getBounds().width(), h = getBounds().height(), r = Math.min(w, h) * 0.17f;
            float cx = w / 2 - r * 0.3f, cy = h / 2 - r * 0.3f;
            c.drawCircle(cx, cy, r, paint);
            c.drawLine(cx + r * 0.72f, cy + r * 0.72f, cx + r * 1.55f, cy + r * 1.55f, paint);
        }
        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(android.graphics.ColorFilter f) { }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    private void showSearch() {
        showPage(SEARCH);
        if (searchResults.getChildCount() == 0) hint("Digite ou fale o que você procura. A busca olha filmes, séries e apps instalados.");
        searchBox.requestFocus();
        android.view.inputmethod.InputMethodManager im =
                (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (im != null) im.showSoftInput(searchBox, 0);
    }

    private void hint(String msg) {
        searchResults.removeAllViews();
        TextView t = text(msg, 15, false);
        t.setSingleLine(false);
        t.setTextColor(SOFT);
        searchResults.addView(t, lp(0, px(16), 0, 0));
    }

    private void runSearch(String raw) {
        String q = raw.trim();
        if (q.length() < 2 || q.equals(lastQuery)) return;
        lastQuery = q;
        hint("Buscando “" + q + "”…");
        Set<String> installed = new HashSet<>(byPkg.keySet());
        bg.execute(() -> {
            List<Search.Title> found = Search.titles(this, q, installed, kidsAge());
            ui.post(() -> {
                if (!q.equals(lastQuery)) return;
                showResults(q, found);
            });
        });
    }

    private void showResults(String q, List<Search.Title> titles) {
        searchResults.removeAllViews();
        String low = q.toLowerCase();
        List<View> appCards = new ArrayList<>();
        for (App a : sortedApps()) if (a.label.toLowerCase().contains(low)) appCards.add(appCard(a, 152, 86, false));
        List<View> titleCards = new ArrayList<>();
        for (Search.Title t : titles) titleCards.add(titleCard(t));
        List<View> inside = new ArrayList<>();
        for (App a : sortedApps()) if (inDock(a) && Search.canSearchIn(this, a.pkg)) inside.add(searchInCard(a, q));
        section("Filmes e séries", titleCards);
        section("Apps", appCards);
        section("Procurar “" + q + "” dentro do app", inside);
        if (titleCards.isEmpty() && appCards.isEmpty()) {
            TextView none = text("Nada encontrado para “" + q + "”. Tente procurar dentro de um app abaixo.", 15, false);
            none.setTextColor(SOFT);
            searchResults.addView(none, 0, lp(0, px(16), 0, 0));
        }
    }

    private void section(String title, List<View> cards) {
        if (cards.isEmpty()) return;
        TextView l = label(title);
        searchResults.addView(l, lp(0, px(18), 0, 0));
        LinearLayout r = row();
        for (View v : cards) r.addView(v, lp(0, 0, px(16), 0));
        HorizontalScrollView h = scroller(r);
        searchResults.addView(h, scrollLp());
    }

    private View titleCard(Search.Title t) {
        FrameLayout tile = tile(decode(t.image, 2), null, px(12), false);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setFocusable(true);
        col.setClickable(true);
        col.addView(tile, new LinearLayout.LayoutParams(px(208), px(117)));
        TextView n = text(t.name, 14, true);
        n.setTextColor(WHITE);
        n.setMaxWidth(px(208));
        n.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(n, lp(px(2), px(9), 0, 0));
        StringBuilder where = new StringBuilder();
        List<String> availableApps = new ArrayList<>();
        for (String pkg : t.apps) {
            App a = byPkg.get(pkg);
            if (a != null && !hidden(a)) {
                availableApps.add(pkg);
                where.append(where.length() > 0 ? " · " : "").append(a.label);
            }
        }
        TextView w = text(where.length() > 0 ? where.toString() : t.kind + (t.year.isEmpty() ? "" : " · " + t.year) + " · app não instalado", 12, false);
        w.setTextColor(where.length() > 0 ? 0xFF8FD18F : DIM);
        w.setMaxWidth(px(208));
        w.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(w, lp(px(2), px(4), 0, 0));
        col.setContentDescription(t.name + (Watchlist.contains(this, t.name, t.kind) ? ", na minha lista. Segure OK para remover." : ", segure OK para salvar na minha lista."));
        col.setOnLongClickListener(v -> {
            boolean saved = !Watchlist.contains(this, t.name, t.kind);
            Watchlist.toggle(this, t);
            hint(saved ? "Adicionado à Minha lista" : "Removido da Minha lista");
            render();
            return true;
        });
        col.setOnClickListener(v -> {
            if (availableApps.isEmpty()) return;
            if (availableApps.size() == 1) {
                Intent i = Search.inApp(this, availableApps.get(0), t.name);
                if (i != null) open(i);
                return;
            }
            String[] names = new String[availableApps.size()];
            for (int k = 0; k < names.length; k++) {
                App a = byPkg.get(availableApps.get(k));
                names[k] = "Assistir no " + (a != null ? a.label : availableApps.get(k));
            }
            showSheet(t.name, null, null, names, which -> {
                Intent i = Search.inApp(this, availableApps.get(which), t.name);
                if (i != null) open(i);
            });
        });
        focusFx(col, tile, 1.08f, null);
        return col;
    }





    private View searchInCard(App a, String q) {
        FrameLayout tile = tile(null, a.banner != null ? a.banner : a.icon, px(12), a.banner == null);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setFocusable(true);
        col.setClickable(true);
        col.addView(tile, new LinearLayout.LayoutParams(px(136), px(77)));
        TextView n = text("Procurar no " + a.label, 12, false);
        n.setTextColor(SOFT);
        n.setMaxWidth(px(136));
        n.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(n, lp(px(2), px(8), 0, 0));
        col.setOnClickListener(v -> {
            Intent i = Search.inApp(this, a.pkg, q);
            if (i != null) open(i);
        });
        focusFx(col, tile, 1.1f, null);
        return col;
    }

    // ------------------------------------------------------------------ profiles

    private void openLayer(View content) {
        closeLayer();
        layer = new FrameLayout(this);
        layer.setBackgroundColor(0xF20A0D12);
        layer.setClickable(true);
        layer.setClipChildren(false);
        layer.setClipToPadding(false);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        FrameLayout viewport = new FrameLayout(this);
        viewport.setClipChildren(false);
        viewport.setClipToPadding(false);
        viewport.setMinimumHeight(getResources().getDisplayMetrics().heightPixels);
        viewport.addView(content, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
        scroll.addView(viewport, new ScrollView.LayoutParams(-1, -2));
        layer.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        layer.setAlpha(0f);
        mainContent.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        rootView.addView(layer, new FrameLayout.LayoutParams(-1, -1));
        layer.animate().alpha(1f).setDuration(200).start();
    }

    private void closeLayer() {
        if (layer == null) return;
        rootView.removeView(layer);
        layer = null;
        mainContent.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        focusDefault();
    }

    private View avatarItem(Bitmap img, String name, int size, Runnable onClick, Runnable onLong) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setFocusable(true);
        col.setClickable(true);
        col.setClipChildren(false);
        FrameLayout circle = new FrameLayout(this);
        ImageView iv = new ImageView(this);
        iv.setImageBitmap(img);
        circle.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        col.addView(circle, new LinearLayout.LayoutParams(px(size), px(size)));
        TextView n = null;
        if (name != null) {
            n = text(name, 16, true);
            n.setTextColor(SOFT);
            col.addView(n, lp(0, px(14), 0, 0));
        }
        final TextView label = n;
        col.setOnClickListener(v -> onClick.run());
        if (onLong != null) col.setOnLongClickListener(v -> {
            onLong.run();
            return true;
        });
        col.setOnFocusChangeListener((v, has) -> {
            circle.animate().scaleX(has ? 1.12f : 1f).scaleY(has ? 1.12f : 1f).setDuration(160).start();
            GradientDrawable ring = new GradientDrawable();
            ring.setShape(GradientDrawable.OVAL);
            ring.setStroke(px(4), WHITE);
            circle.setForeground(has ? ring : null);
            if (label != null) label.setTextColor(has ? WHITE : SOFT);
        });
        return col;
    }

    private Bitmap plusCircle(int size) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        p.setColor(0x33FFFFFF);
        c.drawCircle(size / 2f, size / 2f, size / 2f, p);
        p.setColor(WHITE);
        p.setStrokeWidth(size * 0.05f);
        p.setStrokeCap(android.graphics.Paint.Cap.ROUND);
        c.drawLine(size * 0.35f, size / 2f, size * 0.65f, size / 2f, p);
        c.drawLine(size / 2f, size * 0.35f, size / 2f, size * 0.65f, p);
        return b;
    }

    /** "Quem está assistindo?" — choose, add or (holding OK) edit a profile. */
    private void pickProfile() {
        closeSheet(false);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setClipChildren(false);
        box.setPadding(0, px(20), 0, px(20));
        TextView t = text("Quem está assistindo?", 34, true);
        t.setTextColor(WHITE);
        box.addView(t, lp(0, 0, 0, px(40)));
        LinearLayout row = new LinearLayout(this);
        row.setClipChildren(false);
        row.setGravity(Gravity.CENTER_HORIZONTAL);
        View first = null;
        for (Profiles.Profile p : Profiles.all(this)) {
            View item = avatarItem(Profiles.avatar(p.avatar, px(220)), p.name + (p.kids ? "  ·  Kids" : ""), 110,
                    () -> switchTo(p), () -> editProfile(p, false));
            row.addView(item, lp(px(22), 0, px(22), 0));
            if (profile != null && p.id.equals(profile.id)) first = item;
            if (first == null) first = item;
        }
        View add = avatarItem(plusCircle(px(220)), "Adicionar", 110, () -> editProfile(null, false), null);
        row.addView(add, lp(px(22), 0, px(22), 0));
        box.addView(row, new LinearLayout.LayoutParams(-1, -2));
        TextView tip = text("Segure OK num perfil para editar", 14, false);
        tip.setTextColor(DIM);
        box.addView(tip, lp(0, px(40), 0, 0));
        openLayer(box);
        (first != null ? first : add).requestFocus();
    }

    private void switchTo(Profiles.Profile p) {
        if (profile != null && profile.kids && !profile.id.equals(p.id)) {
            if (profile.pinRequired && Profiles.hasPin(profile)) {
                askProfilePin(profile, () -> enterProfile(p));
                return;
            }
        }
        enterProfile(p);
    }

    private void enterProfile(Profiles.Profile p) {
        if (p.kids && p.pinRequired && !Profiles.hasPin(p)) {
            askToCreatePin(p, () -> performSwitchTo(p));
            return;
        }
        performSwitchTo(p);
    }

    private void performSwitchTo(Profiles.Profile p) {
        Profiles.setCurrent(this, p.id);
        closeLayer();
        Intent i = new Intent(this, MainActivity.class).putExtra("trocou", true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    private void askProfilePin(Profiles.Profile locked, Runnable onSuccess) {
        android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setSingleLine(true); input.setHint("PIN do perfil");
        new AlertDialog.Builder(this).setTitle("Perfil protegido · " + locked.name).setView(input)
                .setPositiveButton("Continuar", (d, w) -> {
                    if (Profiles.verifyPin(locked, input.getText().toString())) onSuccess.run();
                    else hint("PIN incorreto.");
                }).setNegativeButton("Cancelar", null).show();
    }

    private void askToCreatePin(Profiles.Profile locked, Runnable onSuccess) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL);
        android.widget.EditText first = new android.widget.EditText(this), second = new android.widget.EditText(this);
        for (android.widget.EditText field : new android.widget.EditText[]{first, second}) {
            field.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
            field.setSingleLine(true); box.addView(field);
        }
        first.setHint("Crie um PIN com 4 ou mais números"); second.setHint("Confirme o PIN");
        new AlertDialog.Builder(this).setTitle("Proteja o perfil Kids").setMessage("Defina um PIN para sair do perfil e alterar suas restrições.")
                .setView(box).setPositiveButton("Salvar PIN", (d, w) -> {
                    String a = first.getText().toString(), b = second.getText().toString();
                    if (a.length() < 4 || !a.equals(b)) { hint("Os PINs precisam ter 4 ou mais números e ser iguais."); return; }
                    Profiles.setPin(locked, a);
                    List<Profiles.Profile> all = Profiles.all(this);
                    for (Profiles.Profile p : all) if (p.id.equals(locked.id)) {
                        p.pinSalt = locked.pinSalt; p.pinHash = locked.pinHash;
                    }
                    Profiles.save(this, all); onSuccess.run();
                }).setNegativeButton("Cancelar", null).show();
    }

    /** Create or edit a profile: name, avatar and whether it is a kids profile (and the age range). */
    private void editProfile(Profiles.Profile existing, boolean first) {
        if (existing != null && existing.kids && existing.pinRequired && Profiles.hasPin(existing)) {
            if (Profiles.hasPin(existing)) askProfilePin(existing, () -> editProfileForm(existing, first));
            return;
        }
        editProfileForm(existing, first);
    }

    private void editProfileForm(Profiles.Profile existing, boolean first) {
        final Profiles.Profile p = new Profiles.Profile();
        if (existing != null) {
            p.id = existing.id;
            p.name = existing.name;
            p.avatar = existing.avatar;
            p.kids = existing.kids;
            p.pinRequired = existing.pinRequired;
            p.age = existing.age;
            p.pinSalt = existing.pinSalt;
            p.pinHash = existing.pinHash;
        } else {
            p.name = "";
            p.avatar = new java.util.Random().nextInt(Profiles.AVATARS.length);
            p.age = 18;
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(px(120), px(30), px(120), px(36));
        TextView t = text(first ? "Crie seu perfil" : existing == null ? "Novo perfil" : "Editar perfil", 30, true);
        t.setTextColor(WHITE);
        box.addView(t, lp(0, 0, 0, px(6)));
        TextView subtitle = text("Personalize quem está assistindo e o que pode acessar.", 15, false);
        subtitle.setTextColor(DIM);
        box.addView(subtitle, lp(0, 0, 0, px(16)));

        LinearLayout identityCard = profileSection();
        TextView identityTitle = label("Perfil");
        identityCard.addView(identityTitle, lp(0, 0, 0, px(10)));
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        ImageView big = new ImageView(this);
        big.setImageBitmap(Profiles.avatar(p.avatar, px(180)));
        top.addView(big, new LinearLayout.LayoutParams(px(90), px(90)));
        android.widget.EditText name = new android.widget.EditText(this);
        name.setSingleLine(true);
        name.setText(p.name);
        name.setHint("Nome");
        name.setHintTextColor(DIM);
        name.setTextColor(WHITE);
        name.setTextSize(18);
        name.setPadding(px(18), px(10), px(18), px(10));
        name.setBackground(pill(0x24FFFFFF, 0x24FFFFFF, px(22)));
        name.setOnFocusChangeListener((v, has) -> name.setBackground(pill(has ? 0x38FFFFFF : 0x24FFFFFF, has ? 0x80FFFFFF : 0x24FFFFFF, px(22))));
        top.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
        ((LinearLayout.LayoutParams) name.getLayoutParams()).setMargins(px(22), 0, 0, 0);
        identityCard.addView(top, new LinearLayout.LayoutParams(-1, -2));
        box.addView(identityCard, profileSectionLp(12));

        LinearLayout avatarCard = profileSection();
        TextView al = label("Avatar");
        avatarCard.addView(al, lp(0, 0, 0, px(6)));
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        LinearLayout line = null;
        for (int i = 0; i < Profiles.AVATARS.length; i++) {
            if (i % 8 == 0) {
                line = new LinearLayout(this);
                line.setClipChildren(false);
                grid.addView(line, lp(0, px(6), 0, px(6)));
            }
            final int idx = i;
            line.addView(avatarItem(Profiles.avatar(i, px(104)), null, 52, () -> {
                p.avatar = idx;
                big.setImageBitmap(Profiles.avatar(idx, px(180)));
            }, null), lp(0, 0, px(14), 0));
        }
        avatarCard.addView(grid, lp(0, 0, 0, 0));
        box.addView(avatarCard, profileSectionLp(12));

        LinearLayout controlsCard = profileSection();
        TextView controlsTitle = label("Conteúdo e privacidade");
        controlsCard.addView(controlsTitle, lp(0, 0, 0, px(10)));
        LinearLayout kidsLine = new LinearLayout(this);
        kidsLine.setGravity(Gravity.CENTER_VERTICAL);
        TextView kl = label("Perfil infantil");
        kl.setSingleLine(false);
        kidsLine.addView(kl, new LinearLayout.LayoutParams(0, -2, 1f));
        ((LinearLayout.LayoutParams) kl.getLayoutParams()).setMargins(0, 0, px(16), 0);
        TextView no = chip("Não"), yes = chip("Sim");
        kidsLine.addView(no, lp(0, 0, px(8), 0));
        kidsLine.addView(yes);
        controlsCard.addView(kidsLine, lp(0, 0, 0, px(12)));
        LinearLayout pinLine = new LinearLayout(this);
        pinLine.setGravity(Gravity.CENTER_VERTICAL);
        TextView pinLabel = label("Exigir PIN ao entrar em Kids");
        pinLine.addView(pinLabel, new LinearLayout.LayoutParams(0, -2, 1f));
        android.widget.Switch pinToggle = new android.widget.Switch(this);
        pinToggle.setTextOn("Sim");
        pinToggle.setTextOff("Não");
        pinToggle.setChecked(p.pinRequired);
        pinToggle.setContentDescription("Exigir cadastro de PIN ao entrar neste perfil Kids");
        pinLine.addView(pinToggle, lp(0, 0, 0, 0));
        controlsCard.addView(pinLine, lp(0, 0, 0, px(10)));
        TextView pinHelp = text("Desligado por padrão. Ligado: o PIN será cadastrado na primeira entrada e protegerá a saída e as restrições.", 14, false);
        pinHelp.setTextColor(DIM);
        controlsCard.addView(pinHelp, lp(0, 0, 0, px(8)));
        LinearLayout ages = new LinearLayout(this);
        ages.setOrientation(LinearLayout.VERTICAL);
        TextView agl = label("Faixa etária");
        ages.addView(agl, lp(0, 0, 0, px(8)));
        int[] values = new int[15];
        for (int i = 0; i < values.length; i++) values[i] = i + 3;
        TextView[] ageChips = new TextView[values.length];
        LinearLayout ageRow = null;
        for (int i = 0; i < values.length; i++) {
            if (i % 5 == 0) {
                ageRow = new LinearLayout(this);
                ageRow.setGravity(Gravity.CENTER_VERTICAL);
                ages.addView(ageRow, lp(0, 0, 0, px(8)));
            }
            ageChips[i] = chip(Profiles.ageLabel(values[i]));
            ageRow.addView(ageChips[i], lp(0, 0, px(8), 0));
        }
        controlsCard.addView(ages, lp(0, 0, 0, px(8)));
        TextView ageHelp = text("Limita as recomendações e os apps pela idade selecionada.", 14, false);
        ageHelp.setTextColor(DIM);
        controlsCard.addView(ageHelp, lp(0, 0, 0, px(12)));
        box.addView(controlsCard, profileSectionLp(14));
        Runnable paint = () -> {
            selectChip(no, !p.kids);
            selectChip(yes, p.kids);
            for (int i = 0; i < values.length; i++) selectChip(ageChips[i], p.age == values[i]);
            pinLine.setVisibility(p.kids ? View.VISIBLE : View.GONE);
            pinHelp.setVisibility(p.kids ? View.VISIBLE : View.GONE);
            ages.setVisibility(p.kids ? View.VISIBLE : View.GONE);
            ageHelp.setVisibility(p.kids ? View.VISIBLE : View.GONE);
        };
        no.setOnClickListener(v -> { p.kids = false; paint.run(); });
        yes.setOnClickListener(v -> { p.kids = true; paint.run(); });
        selectOnFocus(no, () -> { p.kids = false; paint.run(); });
        selectOnFocus(yes, () -> { p.kids = true; paint.run(); });
        pinToggle.setOnCheckedChangeListener((button, checked) -> {
            p.pinRequired = checked;
            if (!checked) { p.pinSalt = ""; p.pinHash = ""; }
            paint.run();
        });
        for (int i = 0; i < values.length; i++) {
            final int v = values[i];
            ageChips[i].setOnClickListener(x -> { p.age = v; paint.run(); });
            selectOnFocus(ageChips[i], () -> { p.age = v; paint.run(); });
        }
        paint.run();

        LinearLayout actions = new LinearLayout(this);
        TextView save = chip("Salvar");
        actions.addView(save, lp(0, 0, px(10), 0));
        if (existing != null && Profiles.all(this).size() > 1) {
            TextView del = chip("Excluir perfil");
            del.setOnClickListener(v -> {
                List<Profiles.Profile> all = Profiles.all(this);
                for (int i = 0; i < all.size(); i++) if (all.get(i).id.equals(p.id)) all.remove(i);
                Profiles.save(this, all);
                if (profile != null && profile.id.equals(p.id)) switchTo(all.get(0));
                else pickProfile();
            });
            actions.addView(del, lp(0, 0, px(10), 0));
        }
        if (!first) {
            TextView cancel = chip("Cancelar");
            cancel.setOnClickListener(v -> pickProfile());
            actions.addView(cancel);
        }
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.setPadding(px(8), px(6), px(8), px(6));
        box.addView(actions, lp(0, 0, 0, px(10)));
        save.setOnClickListener(v -> {
            String n = name.getText().toString().trim();
            if (n.isEmpty()) {
                name.setHint("Digite um nome");
                name.requestFocus();
                return;
            }
            if (!p.kids || !p.pinRequired) { p.pinSalt = ""; p.pinHash = ""; }
            p.name = n;
            List<Profiles.Profile> all = Profiles.all(this);
            boolean found = false;
            for (int i = 0; i < all.size(); i++) {
                if (all.get(i).id.equals(p.id)) {
                    all.set(i, p);
                    found = true;
                }
            }
            if (!found) {
                p.id = Profiles.newId(this);
                all.add(p);
            }
            Profiles.save(this, all);
            switchTo(p);
        });
        openLayer(box);
        name.requestFocus();
    }

    private LinearLayout profileSection() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(px(20), px(16), px(20), px(16));
        section.setBackground(pill(0x241B2940, 0x338FAEFF, px(20)));
        section.setClipChildren(false);
        section.setClipToPadding(false);
        return section;
    }

    private LinearLayout.LayoutParams profileSectionLp(int bottomMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = px(bottomMargin);
        return params;
    }

    private TextView chip(String s) {
        TextView c = text(s, 15, true);
        c.setFocusable(true);
        c.setClickable(true);
        c.setPadding(px(18), px(9), px(18), px(9));
        c.setTag(Boolean.FALSE);
        c.setOnFocusChangeListener((v, has) -> styleChip(c));
        styleChip(c);
        return c;
    }

    /** Moving the focus onto the chip selects it; no extra OK press needed. */
    private void selectOnFocus(TextView c, Runnable select) {
        c.setOnFocusChangeListener((v, has) -> {
            if (has) select.run();
            styleChip(c);
        });
    }

    private void selectChip(TextView c, boolean on) {
        c.setTag(on);
        styleChip(c);
    }

    private void styleChip(TextView c) {
        boolean on = Boolean.TRUE.equals(c.getTag());
        if (c.isFocused()) {
            c.setBackground(pill(WHITE, WHITE, px(20)));
            c.setTextColor(Color.BLACK);
        } else {
            c.setBackground(pill(on ? 0x66FFFFFF : 0x1FFFFFFF, on ? WHITE : 0x33FFFFFF, px(20)));
            c.setTextColor(WHITE);
        }
    }

    // ------------------------------------------------------------------ moving apps

    private List<String> order() {
        String saved = prefs.getString("order", "");
        List<String> out = new ArrayList<>();
        for (String p : saved.split(",")) if (!p.isEmpty()) out.add(p);
        return out;
    }

    private int appsPerLine() {
        if (page == APPS) return 6;
        return Math.max(1, (getResources().getDisplayMetrics().widthPixels - 2 * px(SIDE) + px(14)) / px(112 + 14));
    }

    /** The app tiles on screen: the Apps page grid, or the home strip. */
    private List<View> movableTiles() {
        if (page != APPS) return appTiles();
        List<View> out = new ArrayList<>();
        for (int i = 0; i < grid.getChildCount(); i++) {
            View l = grid.getChildAt(i);
            if (l instanceof LinearLayout) for (int j = 0; j < ((LinearLayout) l).getChildCount(); j++) out.add(((LinearLayout) l).getChildAt(j));
        }
        return out;
    }

    private void startMove(App a) {
        List<String> current = new ArrayList<>();
        for (View tile : movableTiles()) {
            Object t = tile.getTag();
            if (t instanceof App) current.add(((App) t).pkg);
        }
        prefs.edit().putString("order", TextUtils.join(",", current)).apply();
        moving = a.pkg;
        if (moveHint == null) {
            moveHint = text("◀ ▶ ▲ ▼ para mover   ·   OK para concluir", 14, true);
            moveHint.setTextColor(Color.BLACK);
            moveHint.setPadding(px(18), px(9), px(18), px(9));
            moveHint.setBackground(pill(WHITE, WHITE, px(18)));
            FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            hl.setMargins(0, 0, 0, px(18));
            rootView.addView(moveHint, hl);
        }
        moveHint.setVisibility(View.VISIBLE);
        markMoving();
    }

    private void markMoving() {
        for (View v : movableTiles()) {
            boolean me = v.getTag() instanceof App && ((App) v.getTag()).pkg.equals(moving);
            v.setAlpha(moving == null || me ? 1f : 0.55f);
            if (me) v.requestFocus();
        }
    }

    private void moveBy(int delta) {
        List<String> o = order();
        int i = o.indexOf(moving);
        int j = i + delta;
        if (i < 0 || j < 0 || j >= o.size()) return;
        o.add(j, o.remove(i));
        prefs.edit().putString("order", TextUtils.join(",", o)).apply();
        render();
        markMoving();
    }

    private void endMove() {
        moving = null;
        if (moveHint != null) moveHint.setVisibility(View.GONE);
        markMoving();
    }
}
