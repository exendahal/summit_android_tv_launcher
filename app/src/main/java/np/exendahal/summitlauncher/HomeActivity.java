package np.exendahal.summitlauncher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.transition.MaterialFadeThrough;

import java.util.Arrays;
import java.util.List;

/**
 * The HOME activity. Hosts Home, Apps and Settings screens, owns the wallpaper layer, global keys
 * (Home, Search, Settings), the Back hierarchy (PRD 7.2) and the minute tick.
 */
public class HomeActivity extends AppCompatActivity {
    public static final String HOME = "home", APPS = "apps", SETTINGS = "settings", SETUP = "setup";

    /** Settings that change theme resources and therefore need an activity recreate. */
    private static final List<String> RECREATE_KEYS = Arrays.asList(Prefs.THEME_MODE, Prefs.THEME_COLOUR, Prefs.THEME_CUSTOM,
            Prefs.CONTRAST, Prefs.WALLPAPER, Prefs.TEXT_SCALE, Prefs.ANIMATIONS, Prefs.TILE_SIZE, Prefs.TILE_SHAPE);

    /** Current screen; static so it survives theme recreates. */
    private static String current = HOME;
    private boolean dark;

    private final BroadcastReceiver tick = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (ThemeController.isDark(HomeActivity.this) != dark) { recreate(); return; } // Auto by time flip
            Screen s = screen();
            if (s != null) s.onTick();
        }
    };

    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            AppsRepo.invalidate();
            Screen s = screen();
            if (s != null) s.onPackagesChanged();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeController.apply(this);
        dark = ThemeController.isDark(this);
        super.onCreate(null); // stateNotNeeded: screens are rebuilt from settings, not restored
        applyLocale();
        setContentView(R.layout.activity_home);
        bindWallpaper();
        if (!Prefs.get(this).bool(Prefs.SETUP_COMPLETED)) current = SETUP; // first run (PRD 6.1)
        show(current, false);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                Screen s = screen();
                if (s != null && s.onBack()) return;
                if (!HOME.equals(current)) show(HOME, true);
                // On Home, Back is consumed: never exit, never flicker (PRD 6.2)
            }
        });

        IntentFilter pf = new IntentFilter();
        pf.addAction(Intent.ACTION_PACKAGE_ADDED);
        pf.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pf.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pf.addDataScheme("package");
        registerReceiver(packages, pf);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null); // never recreate a destroyed activity
        unregisterReceiver(packages);
        super.onDestroy();
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter f = new IntentFilter(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(tick, f);
    }

    @Override
    protected void onStop() {
        unregisterReceiver(tick);
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ThemeController.isDark(this) != dark) { recreate(); return; }
        Prefs p = Prefs.get(this);
        if (p.bool(Prefs.BOOT_PENDING) && p.bool(Prefs.SETUP_COMPLETED)) { // "Open when TV starts" (PRD 6.9)
            p.set(Prefs.BOOT_PENDING, "false");
            TvSource.watch(this);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (Intent.ACTION_MAIN.equals(intent.getAction()) && intent.hasCategory(Intent.CATEGORY_HOME)) {
            if (!Prefs.get(this).bool(Prefs.SETUP_COMPLETED)) return; // stay in first-run setup
            if (!HOME.equals(current)) show(HOME, true);
            else if (screen() instanceof HomeFragment) ((HomeFragment) screen()).goHome();
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        Screen s = screen();
        if (s != null && s.onKey(e)) return true;
        if (e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0) {
            switch (e.getKeyCode()) {
                case KeyEvent.KEYCODE_SEARCH:
                    TvSource.voiceSearch(this);
                    return true;
                case KeyEvent.KEYCODE_SETTINGS:
                    TvSource.open(this, Settings.ACTION_SETTINGS);
                    return true;
                default:
                    break;
            }
        }
        return super.dispatchKeyEvent(e);
    }

    /** Switches top-level screen with a fade-through (PRD 5.7). */
    public void show(String screen, boolean animate) {
        current = screen;
        Screen next = APPS.equals(screen) ? new AppsFragment()
                : SETTINGS.equals(screen) ? new SettingsFragment()
                : SETUP.equals(screen) ? new SetupFragment() : new HomeFragment();
        Fragment old = getSupportFragmentManager().findFragmentById(R.id.content);
        if (animate && !"off".equals(Ui.motion(this))) {
            next.setEnterTransition(new MaterialFadeThrough());
            if (old != null) old.setExitTransition(new MaterialFadeThrough());
        }
        getSupportFragmentManager().beginTransaction().replace(R.id.content, next).commitAllowingStateLoss();
    }

    public Screen screen() {
        Fragment f = getSupportFragmentManager().findFragmentById(R.id.content);
        return f instanceof Screen ? (Screen) f : null;
    }

    public void bindWallpaper() {
        ThemeController.bindWallpaper(this, findViewById(R.id.wallpaper), findViewById(R.id.tint), findViewById(R.id.dim));
    }

    /** Applies a settings change everywhere (PRD 6.6: changes apply instantly). */
    public void onSettingChanged(String key) {
        if (Prefs.WALLPAPER_DIM.equals(key)) bindWallpaper();
        if (Prefs.LANGUAGE.equals(key) || RECREATE_KEYS.contains(key)) {
            // Rebuilding for a new theme or language takes a moment on low-end boxes,
            // so apply once the user stops stepping through values.
            handler.removeCallbacks(applyPending);
            handler.postDelayed(applyPending, getResources().getInteger(R.integer.recreate_delay));
        }
    }

    private final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());

    /** A locale change recreates the activity by itself; otherwise recreate for the new theme. */
    private final Runnable applyPending = () -> {
        if (!applyLocale()) recreate();
    };

    /** @return true if the locale changed (AppCompat then recreates the activity). */
    private boolean applyLocale() {
        String lang = Prefs.get(this).str(Prefs.LANGUAGE);
        if (lang.equals(AppCompatDelegate.getApplicationLocales().toLanguageTags())) return false;
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang));
        return true;
    }
}
