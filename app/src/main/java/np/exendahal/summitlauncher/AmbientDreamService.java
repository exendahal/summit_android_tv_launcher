package np.exendahal.summitlauncher;

import android.Manifest;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.service.dreams.DreamService;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Ambient mode screensaver (PRD 6.8): clock, BS/AD date, weather, health line and scores.
 * Wakes once per minute; shifts the content block every minute for burn-in protection and dims at night.
 */
public class AmbientDreamService extends DreamService {
    private static final String COMPONENTS = "screensaver_components", ENABLED = "screensaver_enabled";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private View root;
    private int shiftStep;
    private String weatherLine;
    /** Latest scores, refreshed in the background each minute; null until loaded. */
    private Scores.Result scores;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            update();
            long now = System.currentTimeMillis();
            handler.postDelayed(this, 60_000 - now % 60_000 + 50);
        }
    };

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false); // any key ends the dream; the system returns to Home
        setFullscreen(true);
        ContextThemeWrapper themed = new ContextThemeWrapper(this, 0);
        ThemeController.apply(themed);
        root = LayoutInflater.from(themed).inflate(R.layout.dream, null);
        setContentView(root);
        ThemeController.bindWallpaper(themed, (ImageView) root.findViewById(R.id.wallpaper), null, null);
        Ui.scaleText(root, Prefs.get(this).decimal(Prefs.TEXT_SCALE));
        Weather.IO.execute(() -> {
            String line = weatherLine(getApplicationContext(), Weather.load(getApplicationContext()));
            handler.post(() -> { weatherLine = line; update(); });
        });
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        handler.post(tick);
    }

    @Override
    public void onDreamingStopped() {
        handler.removeCallbacksAndMessages(null);
        super.onDreamingStopped();
    }

    private void update() {
        if (root == null) return;
        Prefs p = Prefs.get(this);
        if (BuildConfig.SCORES_ENABLED && p.bool(Prefs.AMBIENT_SCORES)) {
            Weather.IO.execute(() -> { Scores.Result fresh = Scores.load(getApplicationContext()); handler.post(() -> scores = fresh); });
        }
        bind(this, root, weatherLine, scores);

        if (p.bool(Prefs.AMBIENT_SHIFT)) { // 4 offsets, 2 s ease (PRD 6.8)
            int[][] offsets = {{0, 0}, {R.dimen.burn_in_x1, R.dimen.burn_in_y1}, {R.dimen.burn_in_x2, R.dimen.burn_in_y2},
                    {R.dimen.burn_in_x3, R.dimen.burn_in_y3}};
            int[] o = offsets[shiftStep++ % 4];
            float x = o[0] == 0 ? 0 : getResources().getDimension(o[0]);
            float y = o[1] == 0 ? 0 : getResources().getDimension(o[1]);
            root.findViewById(R.id.block).animate().translationX(x).translationY(y)
                    .setDuration(getResources().getInteger(R.integer.anim_shift)).start();
        }
    }

    /** @return true if this is the system screensaver. */
    static boolean isActive(Context c) {
        String current = Settings.Secure.getString(c.getContentResolver(), COMPONENTS);
        return current != null && current.contains(new ComponentName(c, AmbientDreamService.class).flattenToString());
    }

    /** True once {@code adb shell pm grant <package> android.permission.WRITE_SECURE_SETTINGS} has been run. */
    static boolean canChange(Context c) {
        return c.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED;
    }

    /** Makes this the system screensaver, or restores the one it replaced. Requires {@link #canChange}. */
    static void setActive(Context c, boolean on) {
        ContentResolver r = c.getContentResolver();
        Prefs p = Prefs.get(c);
        if (on) {
            String current = Settings.Secure.getString(r, COMPONENTS);
            if (current != null && !isActive(c)) p.set(Prefs.PREVIOUS_SCREENSAVER, current);
            Settings.Secure.putString(r, COMPONENTS, new ComponentName(c, AmbientDreamService.class).flattenToString());
            Settings.Secure.putInt(r, ENABLED, 1);
        } else if (!p.str(Prefs.PREVIOUS_SCREENSAVER).isEmpty()) {
            Settings.Secure.putString(r, COMPONENTS, p.str(Prefs.PREVIOUS_SCREENSAVER));
        } else {
            Settings.Secure.putInt(r, ENABLED, 0); // nothing to restore (e.g. set over adb): turn the screensaver off
        }
    }

    /** @return "Kathmandu 14° · partly cloudy", or null without weather. */
    static String weatherLine(Context c, Weather w) {
        if (w == null) return null;
        Prefs p = Prefs.get(c);
        return c.getString(R.string.ambient_weather, Weather.placeName(c), Weather.temp(p, w.temp),
                c.getString(Weather.label(w.code, w.isDay)).toLowerCase(Ui.locale(p)));
    }

    /** Fills {@code dream.xml} for now; shared by the screensaver and its Settings preview. */
    static void bind(Context c, View root, String weatherLine, Scores.Result scores) {
        Prefs p = Prefs.get(c);
        Resources res = c.getResources();
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();
        ((TextView) root.findViewById(R.id.clock)).setText(Ui.clock(p, now));
        Ui.text(root.findViewById(R.id.bs), Ui.bsDate(c, p, today));
        String adLine = Ui.adDate(c, p, today);
        if (weatherLine != null) adLine = adLine == null ? weatherLine : adLine + " · " + weatherLine;
        Ui.text(root.findViewById(R.id.ad), adLine);
        Ui.text(root.findViewById(R.id.health), p.is(Prefs.HEALTH_LINE, "off") ? null : Ui.healthTip(c, today));

        String scoreLine = null;
        if (BuildConfig.SCORES_ENABLED && p.bool(Prefs.AMBIENT_SCORES) && scores != null
                && !Scores.isStale(scores.updatedAt, res.getInteger(R.integer.scores_stale_min))) {
            List<String> live = new ArrayList<>();
            for (Scores.Match m : Scores.forHome(scores.matches, p.leagues(), true, p.integer(Prefs.SCORE_MAX), System.currentTimeMillis())) {
                if (m.live()) live.add("● " + m.leagueName + " " + Scores.chipText(m));
            }
            long min = (System.currentTimeMillis() - scores.updatedAt) / 60_000;
            if (!live.isEmpty()) {
                scoreLine = Ui.digits(p, String.join(" · ", live) + " · " + res.getQuantityString(R.plurals.updated_ago, (int) min, (int) min));
            }
        }
        Ui.text(root.findViewById(R.id.scores), scoreLine);

        int h = now.getHour();
        boolean night = h >= res.getInteger(R.integer.night_start_hour) || h < res.getInteger(R.integer.night_end_hour);
        root.setAlpha(p.bool(Prefs.AMBIENT_NIGHT_DIM) && night ? Ui.fraction(c, R.dimen.night_dim_alpha) : 1f);
    }
}
