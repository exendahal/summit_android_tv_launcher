package np.exendahal.summitlauncher;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Launcher settings (PRD section 9), stored in the "himal_settings" SharedPreferences.
 * Every value is stored as a stable string so the settings UI can step through values uniformly;
 * typed getters parse them. Keys and defaults mirror docs/settings.md.
 */
public final class Prefs {
    public static final String THEME_MODE = "theme_mode", THEME_COLOUR = "theme_colour", THEME_CUSTOM = "theme_custom_colour", CONTRAST = "contrast",
            ANIMATIONS = "animations", WALLPAPER = "wallpaper", WALLPAPER_DIM = "wallpaper_dim",
            TEXT_SCALE = "text_scale", NEPALI_DIGITS = "nepali_digits", LANGUAGE = "language",
            CALENDAR = "calendar", TIME_FORMAT = "time_format", TEMP_UNIT = "weather_temp_unit",
            WIND_UNIT = "weather_wind_unit", RAIN_UNIT = "weather_rain_unit", LOCATION = "weather_location",
            WEATHER_SHOW = "weather_show_home", WATCH_TILE = "home_watch_tile", CONTINUE_ROW = "home_continue_row",
            FAV_ORDER = "home_fav_order", HEALTH_LINE = "health_line", LIVE_TYPE = "live_type",
            LIVE_INPUT = "live_input_id", LIVE_APP = "live_app_package", LIVE_ON_BOOT = "live_open_on_boot",
            TILE_SIZE = "tile_size", TILE_SHAPE = "tile_shape", TILE_LABELS = "tile_labels",
            SCORE_LEAGUES = "score_leagues", SCORE_MAX = "score_max", SCORE_LIVE_FIRST = "score_live_first",
            SCORE_STALE = "score_stale", AMBIENT_SCORES = "ambient_scores", AMBIENT_SHIFT = "ambient_shift",
            AMBIENT_NIGHT_DIM = "ambient_night_dim";

    /** Non-setting state kept in the same file; untouched by {@link #reset()}. */
    public static final String FAVOURITES = "favourites", BANNER_DISMISSED = "default_banner_dismissed",
            BOOT_PENDING = "boot_pending", WALLPAPER_SEED = "wallpaper_seed", PREVIOUS_SCREENSAVER = "previous_screensaver", SETUP_COMPLETED = "setup_completed", USAGE_ASKED = "usage_access_asked";

    private static final Map<String, String> DEFAULTS = new HashMap<>();

    static {
        String[][] d = {
                {THEME_MODE, "auto"}, {THEME_COLOUR, "wallpaper"}, {THEME_CUSTOM, "blue"}, {CONTRAST, "standard"}, {ANIMATIONS, "full"},
                {WALLPAPER, "mountains"}, {WALLPAPER_DIM, "0"}, {TEXT_SCALE, "1.0"}, {NEPALI_DIGITS, "false"},
                {CALENDAR, "both"}, {TIME_FORMAT, "12h"}, {TEMP_UNIT, "c"}, {WIND_UNIT, "kmh"}, {RAIN_UNIT, "mm"},
                {LOCATION, "auto"}, {WEATHER_SHOW, "true"}, {WATCH_TILE, "first"}, {CONTINUE_ROW, "true"},
                {FAV_ORDER, "most_used"}, {HEALTH_LINE, "ambient"}, {LIVE_TYPE, "input"}, {LIVE_INPUT, ""},
                {LIVE_APP, "ask"}, {LIVE_ON_BOOT, "false"}, {TILE_SIZE, "medium"}, {TILE_SHAPE, "rounded"},
                {TILE_LABELS, "false"}, {SCORE_LEAGUES, "npl,pl,laliga,ucl"}, {SCORE_MAX, "4"},
                {SCORE_LIVE_FIRST, "true"}, {SCORE_STALE, "unavailable"}, {AMBIENT_SCORES, "true"},
                {AMBIENT_SHIFT, "true"}, {AMBIENT_NIGHT_DIM, "true"},
        };
        for (String[] kv : d) DEFAULTS.put(kv[0], kv[1]);
        // PRD 9: device locale if supported, else English. v1 ships en + ne translations.
        DEFAULTS.put(LANGUAGE, "ne".equals(Locale.getDefault().getLanguage()) ? "ne" : "en");
    }

    private static Prefs instance;
    private final SharedPreferences sp;
    private final Context app;

    private Prefs(Context c) {
        app = c.getApplicationContext();
        sp = app.getSharedPreferences("himal_settings", Context.MODE_PRIVATE);
    }

    /** Application context, for settings whose effect depends on the device (e.g. region). */
    public Context context() { return app; }

    /** @return the process-wide settings instance. */
    public static synchronized Prefs get(Context c) {
        if (instance == null) instance = new Prefs(c);
        return instance;
    }

    /** @return the stored value for {@code key}, or its default ("" if none). */
    public String str(String key) {
        String def = DEFAULTS.get(key);
        return sp.getString(key, def == null ? "" : def);
    }

    public boolean bool(String key) { return Boolean.parseBoolean(str(key)); }

    public int integer(String key) {
        try { return Integer.parseInt(str(key)); } catch (NumberFormatException e) { return 0; }
    }

    public float decimal(String key) {
        try { return Float.parseFloat(str(key)); } catch (NumberFormatException e) { return 1f; }
    }

    public boolean is(String key, String value) { return value.equals(str(key)); }

    /** Persists {@code value} immediately. */
    public void set(String key, String value) { sp.edit().putString(key, value).apply(); }

    /** @return the selected score league ids, in stored order. */
    public Set<String> leagues() {
        String v = str(SCORE_LEAGUES);
        return v.isEmpty() ? new LinkedHashSet<>() : new LinkedHashSet<>(Arrays.asList(v.split(",")));
    }

    public void setLeagues(Set<String> ids) { set(SCORE_LEAGUES, String.join(",", ids)); }

    /** @return favourite package names in display order, or null if never set (use defaults). */
    public List<String> favourites() {
        String v = sp.getString(FAVOURITES, null);
        if (v == null) return null;
        return v.isEmpty() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(v.split(",")));
    }

    public void setFavourites(List<String> pkgs) { sp.edit().putString(FAVOURITES, String.join(",", pkgs)).apply(); }

    /** Restores every launcher setting to default. Favourites and other state are kept (PRD 6.6). */
    public void reset() {
        SharedPreferences.Editor e = sp.edit();
        for (String k : DEFAULTS.keySet()) e.remove(k);
        e.apply();
    }

    public SharedPreferences raw() { return sp; }
}
