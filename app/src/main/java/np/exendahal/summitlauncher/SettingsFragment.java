package np.exendahal.summitlauncher;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.media.tv.TvInputInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;

import com.google.android.material.R.attr;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.imageview.ShapeableImageView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Launcher settings in the Android TV preference pattern (PRD 6.6, revised): one list at a time.
 * OK on a category shows its options; OK on an option shows its values (radio list, current one checked);
 * OK on a value applies it and returns to the options. Left/right do nothing; Back goes up one level.
 * A live preview of Home sits beside the list. Every change is persisted and applied immediately.
 */
public class SettingsFragment extends Screen {
    public static final int CAT_THEME = 0, CAT_FONT = 1, CAT_LANGUAGE = 2, CAT_WEATHER = 3,
            CAT_HOME = 4, CAT_LIVE = 5, CAT_TILES = 6, CAT_SCORES = 7, CAT_SCREENSAVER = 8;
    private static final int LEVEL_CATEGORIES = 0, LEVEL_OPTIONS = 1, LEVEL_VALUES = 2;

    /** Navigation state; static so it survives the activity recreate that theme/language changes cause. */
    static int level = LEVEL_CATEGORIES;
    static int openCategory = CAT_THEME;
    private static String openOption;
    /** Tag of the row to focus after the next render. */
    private static String focusTag;

    /** Supported languages; display names in R.array.languages (same order). */
    static final String[] LANG_CODES = {"en", "ne", "hi", "zh-Hans"};

    /** League chips (PRD 6.6.2): group label, then id/name pairs. */
    private static final Object[][] LEAGUES = {
            {R.string.grp_cricket, "npl", "NPL", "ipl", "IPL", "bbl", "Big Bash", "intl_cricket", "International"},
            {R.string.grp_football, "pl", "Premier League", "laliga", "LaLiga", "ucl", "Champions League", "seriea", "Serie A",
                    "bundesliga", "Bundesliga", "ligue1", "Ligue 1"},
            {R.string.grp_basketball, "nba", "NBA", "euroleague", "EuroLeague"},
            {R.string.grp_us, "nfl", "NFL", "nhl", "NHL", "mlb", "MLB"},
            {R.string.grp_tennis, "atp", "ATP", "wta", "WTA"},
            {R.string.grp_motor, "f1", "Formula 1", "motogp", "MotoGP"},
            {R.string.grp_golf, "pga", "PGA Tour"},
    };

    /** One option: stored values and their display names. */
    private static final class Opt {
        final String key; final int label; final String[] values; final String[] names;
        Opt(String key, int label, String[] values, String[] names) {
            this.key = key; this.label = label; this.values = values; this.names = names;
        }
    }

    /** Not a stored setting: the grid of the TV's pictures, opened from Wallpaper › My picture. */
    private static final String PICTURE = "wallpaper_picture";

    private final ActivityResultLauncher<String> askPictures = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (!isAdded()) return;
                if (granted) render(0);
                else Toast.makeText(requireContext(), R.string.picture_denied, Toast.LENGTH_LONG).show();
            });

    private View root;
    private LinearLayout list;
    private View listPane;
    private TextView listTitle, listCrumb;

    /** Leading icon per category, Android TV settings style (index = CAT_*). */
    private static final int[] CAT_ICONS = {R.drawable.ic_palette, R.drawable.ic_format_size,
            R.drawable.ic_language, R.drawable.ic_partly_cloudy_day, R.drawable.ic_home, R.drawable.ic_live_tv,
            R.drawable.ic_grid_view, R.drawable.ic_apps, R.drawable.ic_bedtime};

    /** Opens Settings at the top level (entry from Home). */
    static void resetNavigation() {
        level = LEVEL_CATEGORIES;
        openCategory = CAT_THEME;
        openOption = null;
        focusTag = null;
    }

    /** Opens Settings directly on one category's options (e.g. "Change units in Settings"). */
    static void openAt(int category) {
        level = LEVEL_OPTIONS;
        openCategory = category;
        openOption = null;
        focusTag = null;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup parent, @Nullable Bundle b) {
        root = inf.inflate(R.layout.fragment_settings, parent, false);
        list = root.findViewById(R.id.list);
        listTitle = root.findViewById(R.id.list_title);
        listCrumb = root.findViewById(R.id.list_crumb);
        listPane = root.findViewById(R.id.list_pane);
        // Fade without an off-screen buffer: far cheaper on low-end TV GPUs, and rows do not overlap anyway.
        listPane.forceHasOverlappingRendering(false);
        bindHeader(root, R.id.nav_settings, true);
        Ui.scaleText(root, prefs().decimal(Prefs.TEXT_SCALE)); // before render(), which scales the rows it adds
        render(0);
        root.post(this::focusNav); // after render's first-row focus, which waits for attach too
        onTick();
        root.findViewById(R.id.preview).post(this::bindPreview); // the preview scales to the box's laid-out width
        return root;
    }

    @Override
    public void onTick() { if (root != null) bindPreview(); }

    /** Left/right do nothing in lists (Android TV settings pattern); in grids they move between cells only. */
    @Override
    public boolean onKey(KeyEvent e) {
        int k = e.getKeyCode();
        boolean sideways = k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT;
        if (!sideways || !list.hasFocus()) return false;
        if (level != LEVEL_VALUES || !isGrid(openOption)) return true;
        View next = list.findFocus().focusSearch(k == KeyEvent.KEYCODE_DPAD_LEFT ? View.FOCUS_LEFT : View.FOCUS_RIGHT);
        return !isInList(next);
    }

    private boolean isInList(View v) {
        for (; v != null; v = v.getParent() instanceof View ? (View) v.getParent() : null) {
            if (v == list) return true;
        }
        return false;
    }

    /** Back: values -> options -> categories -> Home. */
    @Override
    public boolean onBack() {
        if (level == LEVEL_VALUES && PICTURE.equals(openOption)) {
            openOption = Prefs.WALLPAPER;
            focusTag = "val:custom";
        } else if (level == LEVEL_VALUES) {
            level = LEVEL_OPTIONS;
            focusTag = openOption;
        } else if (level == LEVEL_OPTIONS) {
            level = LEVEL_CATEGORIES;
            focusTag = "cat:" + openCategory;
        } else {
            return false;
        }
        render(-1);
        return true;
    }

    /** Switches the system screensaver; without the permission, the TV's screensaver settings or setup help. */
    private void useScreensaver(boolean on) {
        if (AmbientDreamService.canChange(requireContext())) {
            AmbientDreamService.setActive(requireContext(), on);
            focusTag = "use_screensaver";
            render(0);
        } else if (!TvSource.open(requireActivity(), Settings.ACTION_DREAM_SETTINGS)) {
            Dialogs.screensaverSetup(requireActivity());
        }
    }

    // ---- Option model ----

    private String[] arr(int res) { return getResources().getStringArray(res); }

    private static String[] v(String... values) { return values; }

    private List<Opt> optionsFor(int cat) {
        Prefs p = prefs();
        List<Opt> o = new ArrayList<>();
        String[] bool = v("true", "false"), boolOff = v("false", "true");
        switch (cat) {
            case CAT_THEME:
                o.add(new Opt(Prefs.THEME_MODE, R.string.opt_mode, v("auto", "dark", "light"), arr(R.array.e_theme_mode)));
                List<String> wv = new ArrayList<>(Arrays.asList(ThemeController.WALLPAPERS));
                List<String> wn = new ArrayList<>(Arrays.asList(arr(R.array.e_wallpaper)));
                for (OnlineWallpapers.Item w : OnlineWallpapers.list(requireContext())) {
                    wv.add(OnlineWallpapers.PREFIX + w.id);
                    wn.add(w.name(p.str(Prefs.LANGUAGE)));
                }
                wv.add("custom");
                wn.add(getString(R.string.wallpaper_custom));
                o.add(new Opt(Prefs.WALLPAPER, R.string.opt_wallpaper, wv.toArray(new String[0]), wn.toArray(new String[0])));
                o.add(new Opt(PICTURE, R.string.wallpaper_custom, v("pick"), v("")));
                o.add(new Opt(Prefs.WALLPAPER_DIM, R.string.opt_dim, v("0", "30", "60"), arr(R.array.e_dim)));
                o.add(new Opt(Prefs.THEME_COLOUR, R.string.opt_colour, v("wallpaper", "coral", "green", "custom"), arr(R.array.e_theme_colour)));
                if (p.is(Prefs.THEME_COLOUR, "custom")) {
                    o.add(new Opt(Prefs.THEME_CUSTOM, R.string.opt_custom_colour, ThemeController.CUSTOM, arr(R.array.e_custom_colour)));
                }
                o.add(new Opt(Prefs.CONTRAST, R.string.opt_contrast, v("standard", "high"), arr(R.array.e_contrast)));
                o.add(new Opt(Prefs.ANIMATIONS, R.string.opt_animations, v("full", "reduced", "off"), arr(R.array.e_animations)));
                break;
            case CAT_FONT:
                o.add(new Opt(Prefs.TEXT_SCALE, R.string.opt_text_size, v("1.0", "1.15", "1.3"), arr(R.array.e_text_size)));
                if (Ui.inNepal(requireContext())) { // Devanagari digits are offered only in Nepal
                    o.add(new Opt(Prefs.NEPALI_DIGITS, R.string.opt_digits, boolOff, arr(R.array.e_off_on)));
                }
                break;
            case CAT_LANGUAGE:
                o.add(new Opt(Prefs.LANGUAGE, R.string.opt_language, LANG_CODES, arr(R.array.languages)));
                if (Ui.inNepal(requireContext())) { // outside Nepal only the Gregorian date is shown
                    o.add(new Opt(Prefs.CALENDAR, R.string.opt_calendar, v("both", "bs", "ad"), arr(R.array.e_calendar)));
                }
                o.add(new Opt(Prefs.TIME_FORMAT, R.string.opt_time, v("12h", "24h"), arr(R.array.e_time)));
                break;
            case CAT_WEATHER:
                o.add(new Opt(Prefs.TEMP_UNIT, R.string.opt_temp, v("c", "f"), arr(R.array.e_temp)));
                o.add(new Opt(Prefs.WIND_UNIT, R.string.opt_wind, v("kmh", "ms", "mph"), arr(R.array.e_wind)));
                o.add(new Opt(Prefs.RAIN_UNIT, R.string.opt_rain, v("mm", "in"), arr(R.array.e_rain)));
                o.add(new Opt(Prefs.WEATHER_SHOW, R.string.opt_show_home, bool, arr(R.array.e_show_hide)));
                break;
            case CAT_HOME:
                o.add(new Opt(Prefs.WATCH_TILE, R.string.opt_watch_tile, v("first", "hidden"), arr(R.array.e_watch_tile)));
                o.add(new Opt(Prefs.CONTINUE_ROW, R.string.opt_continue, bool, arr(R.array.e_show_hide)));
                o.add(new Opt(Prefs.FAV_ORDER, R.string.opt_fav_order, v("most_used", "manual"), arr(R.array.e_fav_order)));
                o.add(new Opt(Prefs.CARD_OPACITY, R.string.opt_card_opacity, v("100", "80", "60", "40", "20", "0"),
                        v("100%", "80%", "60%", "40%", "20%", "0%")));
                o.add(new Opt(Prefs.HEALTH_LINE, R.string.opt_health, v("ambient", "home_ambient", "off"), arr(R.array.e_health)));
                break;
            case CAT_LIVE:
                o.add(new Opt(Prefs.LIVE_TYPE, R.string.opt_live_type, v("input", "app"), arr(R.array.e_live_type)));
                if (p.is(Prefs.LIVE_TYPE, "input")) {
                    List<TvInputInfo> inputs = TvSource.inputs(requireContext());
                    if (!inputs.isEmpty()) {
                        String[] ids = new String[inputs.size()], labels = new String[inputs.size()];
                        for (int i = 0; i < ids.length; i++) { ids[i] = inputs.get(i).getId(); labels[i] = TvSource.label(requireContext(), inputs.get(i)); }
                        o.add(new Opt(Prefs.LIVE_INPUT, R.string.opt_input, ids, labels));
                    }
                } else {
                    List<AppsRepo.App> apps = TvSource.liveApps(requireContext());
                    String[] pkgs = new String[apps.size() + 1], labels = new String[apps.size() + 1];
                    pkgs[0] = "ask"; labels[0] = getString(R.string.ask_every_time);
                    for (int i = 0; i < apps.size(); i++) { pkgs[i + 1] = apps.get(i).pkg; labels[i + 1] = apps.get(i).label; }
                    o.add(new Opt(Prefs.LIVE_APP, R.string.opt_live_app, pkgs, labels));
                }
                o.add(new Opt(Prefs.LIVE_ON_BOOT, R.string.opt_boot, boolOff, arr(R.array.e_off_on)));
                break;
            case CAT_TILES:
                o.add(new Opt(Prefs.TILE_SIZE, R.string.opt_tile_size, v("medium", "large", "small"), arr(R.array.e_tile_size)));
                o.add(new Opt(Prefs.TILE_SHAPE, R.string.opt_tile_shape, v("rounded", "square"), arr(R.array.e_tile_shape)));
                o.add(new Opt(Prefs.TILE_LABELS, R.string.opt_labels, v("true", "focus", "false"), arr(R.array.e_labels)));
                break;
            case CAT_SCORES:
                o.add(new Opt(Prefs.SCORE_MAX, R.string.opt_score_max, v("4", "6", "3", "2"), arr(R.array.e_score_max)));
                o.add(new Opt(Prefs.SCORE_LIVE_FIRST, R.string.opt_live_first, bool, arr(R.array.e_on_off)));
                o.add(new Opt(Prefs.SCORE_STALE, R.string.opt_stale, v("unavailable", "hide"), arr(R.array.e_stale)));
                break;
            case CAT_SCREENSAVER:
                if (BuildConfig.SCORES_ENABLED) o.add(new Opt(Prefs.AMBIENT_SCORES, R.string.opt_amb_scores, bool, arr(R.array.e_on_off)));
                o.add(new Opt(Prefs.AMBIENT_SHIFT, R.string.opt_amb_shift, bool, arr(R.array.e_on_off)));
                o.add(new Opt(Prefs.AMBIENT_NIGHT_DIM, R.string.opt_amb_dim, bool, arr(R.array.e_on_off)));
                break;
            default:
                break;
        }
        return o;
    }

    // ---- Rendering ----

    /**
     * Swaps the list with a shared-axis transition (PRD 5.7): the old list slides and fades out, then the new one
     * is built and slides in. {@code dir} 1 = deeper (from the right), -1 = back up (from the left), 0 = instant.
     * Full animations slide, Reduced fades, Off (or system animations off) swaps instantly.
     */
    private void render(int dir) {
        Context c = requireContext();
        float systemScale = Settings.Global.getFloat(c.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
        String motion = systemScale == 0f ? "off" : prefs().str(Prefs.ANIMATIONS);
        listPane.animate().cancel(); // a newer render wins; its build() uses the latest level
        if (dir == 0 || "off".equals(motion) || listPane.getWidth() == 0) {
            listPane.setAlpha(1f);
            listPane.setTranslationX(0f);
            build();
            return;
        }
        float slide = "full".equals(motion) ? Ui.px(c, R.dimen.pref_slide) : 0f;
        listPane.animate().translationX(-dir * slide).alpha(0f)
                .setDuration(getResources().getInteger(R.integer.anim_list_out))
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> {
                    build();
                    listPane.setTranslationX(dir * slide);
                    listPane.animate().translationX(0f).alpha(1f)
                            .setDuration(getResources().getInteger(R.integer.anim_list_in))
                            .setInterpolator(new DecelerateInterpolator(2f))
                            .start();
                })
                .start();
    }

    private void build() {
        // Park focus on the list while its rows are replaced; otherwise focus jumps into the header for a moment.
        if (list.hasFocus()) {
            list.setFocusable(true);
            list.requestFocus();
        }
        list.removeAllViews();
        String[] cats = getResources().getStringArray(R.array.categories);
        if (level == LEVEL_VALUES && findOpt(openOption) == null) level = LEVEL_OPTIONS; // option vanished (e.g. no inputs)
        String settings = getString(R.string.nav_settings);
        if (level == LEVEL_CATEGORIES) {
            listCrumb.setText("");
            listTitle.setText(settings);
            renderCategories(cats);
        } else if (level == LEVEL_OPTIONS) {
            listCrumb.setText(settings);
            listTitle.setText(cats[openCategory]);
            renderOptions();
        } else {
            Opt o = findOpt(openOption);
            listCrumb.setText(settings + "  \u203a  " + cats[openCategory]);
            listTitle.setText(getString(o.label));
            renderValues(o);
        }
        Ui.scaleText(list, prefs().decimal(Prefs.TEXT_SCALE));
        ScrollView scroll = (ScrollView) list.getParent();
        scroll.scrollTo(0, 0);
        View target = focusTag == null ? null : list.findViewWithTag(focusTag);
        if (target == null) target = firstFocusable();
        if (target != null) {
            // Straight from the parked list to the new row; on first open the list is not attached yet, so wait a frame.
            if (list.isAttachedToWindow()) target.requestFocus(); else root.post(target::requestFocus);
        }
        list.setFocusable(false);
        View focused = target;
        // After layout, bring the focused row into view (e.g. the current value far down a long list).
        if (focused != null) scroll.post(() -> {
            if (focused.isAttachedToWindow()) {
                focused.requestRectangleOnScreen(new Rect(0, 0, focused.getWidth(), focused.getHeight()), true);
            }
        });
        bindPreview();
    }

    private View firstFocusable() {
        for (View v : list.getFocusables(View.FOCUS_FORWARD)) if (v != list) return v;
        return null;
    }

    private void renderCategories(String[] cats) {
        for (int i = 0; i < cats.length; i++) {
            if (i == CAT_SCORES && !BuildConfig.SCORES_ENABLED) continue;
            int cat = i;
            list.addView(row(cats[i], null, CAT_ICONS[i], PrefRow.Mark.CHEVRON, "cat:" + i, () -> {
                level = LEVEL_OPTIONS;
                openCategory = cat;
                focusTag = null;
                render(1);
            }));
        }
        View setup = row(getString(R.string.run_setup), null, R.drawable.ic_restart_alt, PrefRow.Mark.CHEVRON, "setup", () -> {
            SetupFragment.step = 1;
            host().show(HomeActivity.SETUP, true);
        });
        ((LinearLayout.LayoutParams) setup.getLayoutParams()).topMargin = Ui.px(requireContext(), R.dimen.tile_gap) * 2;
        list.addView(setup);
    }

    private void renderOptions() {
        Context c = requireContext();
        if (openCategory == CAT_WEATHER) { // where the weather is for: search or current location
            list.addView(row(getString(R.string.opt_location), Weather.placeName(c), 0, PrefRow.Mark.CHEVRON, Prefs.LOCATION,
                    () -> Dialogs.cityPicker(requireActivity(), () -> { focusTag = Prefs.LOCATION; render(0); })));
        }
        for (Opt o : optionsFor(openCategory)) {
            if (PICTURE.equals(o.key)) continue;
            list.addView(row(getString(o.label), o.names[index(o)], 0, PrefRow.Mark.CHEVRON, o.key, () -> {
                level = LEVEL_VALUES;
                openOption = o.key;
                focusTag = "val:" + o.values[index(o)]; // start on the current value
                render(1);
            }));
        }
        if (openCategory == CAT_SCREENSAVER) {
            boolean on = AmbientDreamService.isActive(c);
            View use = row(getString(R.string.opt_use_screensaver), arr(R.array.e_on_off)[on ? 0 : 1], 0, PrefRow.Mark.NONE,
                    "use_screensaver", () -> useScreensaver(!on));
            list.addView(use, 0);
        }
        if (openCategory == CAT_SCORES) addLeagues();
        View reset = row(getString(R.string.reset), null, R.drawable.ic_restart_alt, PrefRow.Mark.NONE, "reset", () -> Dialogs.resetConfirm(c, () -> {
            prefs().reset();
            resetNavigation();
            host().recreate(); // onCreate re-applies the (default) language and theme
        }));
        ((LinearLayout.LayoutParams) reset.getLayoutParams()).topMargin = Ui.px(c, R.dimen.tile_gap) * 2;
        list.addView(reset);
    }

    /** Radio list of every value, like an Android ListPreference; wallpapers use a preview grid. */
    private void renderValues(Opt o) {
        if (PICTURE.equals(o.key)) { renderPictures(); return; }
        if (isGrid(o.key)) { renderGrid(o); return; }
        int current = index(o);
        for (int i = 0; i < o.values.length; i++) {
            String value = o.values[i];
            list.addView(row(o.names[i], null, 0, i == current ? PrefRow.Mark.RADIO_ON : PrefRow.Mark.RADIO_OFF, "val:" + value,
                    () -> choose(o, value)));
        }
    }

    /** Wallpapers, pictures and custom colours are picked from a grid of previews. */
    private static boolean isGrid(String key) {
        return Prefs.WALLPAPER.equals(key) || Prefs.THEME_CUSTOM.equals(key) || PICTURE.equals(key);
    }

    /**
     * Applies a value and returns to the options list. Wallpaper › My picture and Colour › Custom go on to
     * their grids instead.
     */
    private void choose(Opt o, String value) {
        if (Prefs.WALLPAPER.equals(o.key) && value.startsWith(OnlineWallpapers.PREFIX)) {
            useOnline(value.substring(OnlineWallpapers.PREFIX.length()));
            return;
        }
        if (Prefs.WALLPAPER.equals(o.key) && "custom".equals(value)) {
            openOption = PICTURE;
            focusTag = null;
            render(1);
            return;
        }
        prefs().set(o.key, value);
        if (Prefs.THEME_COLOUR.equals(o.key) && "custom".equals(value)) {
            openOption = Prefs.THEME_CUSTOM;
            focusTag = "val:" + prefs().str(Prefs.THEME_CUSTOM);
            render(1);
        } else {
            level = LEVEL_OPTIONS;
            focusTag = o.key;
            render(-1);
        }
        host().onSettingChanged(o.key); // theme/language changes rebuild the screen shortly after
    }

    /** Wallpapers or custom colours as a grid of previews (quicker to scan than a list). */
    private void renderGrid(Opt o) {
        Context c = requireContext();
        Context app = c.getApplicationContext();
        int current = index(o);
        GridLayout grid = addGrid();
        Map<String, OnlineWallpapers.Item> online = new HashMap<>();
        for (OnlineWallpapers.Item w : OnlineWallpapers.list(c)) online.put(OnlineWallpapers.PREFIX + w.id, w);
        for (int i = 0; i < o.values.length; i++) {
            String value = o.values[i];
            ImageView img = addCell(grid, "val:" + value, o.names[i], i == current, () -> choose(o, value));
            if (Prefs.THEME_CUSTOM.equals(o.key)) {
                img.setBackgroundColor(c.getColor(ThemeController.CUSTOM_SWATCH[i]));
            } else if ("custom".equals(value)) {
                Bitmap picture = CustomWallpaper.bitmap(c);
                if (picture != null) img.setImageBitmap(picture);
                else { // nothing picked yet
                    int inset = Ui.px(c, R.dimen.wallpaper_thumb_h) / 4;
                    img.setImageResource(R.drawable.ic_landscape);
                    img.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    img.setPadding(0, inset, 0, inset);
                    img.setImageTintList(AppCompatResources.getColorStateList(c, R.color.pref_icon));
                    img.setBackgroundColor(Ui.color(c, android.R.attr.colorBackground));
                }
            } else if (online.containsKey(value)) {
                OnlineWallpapers.Item item = online.get(value);
                img.setBackgroundColor(Ui.color(c, android.R.attr.colorBackground));
                Weather.IO.execute(() -> {
                    Bitmap thumb = OnlineWallpapers.thumbnail(app, item);
                    if (thumb != null) img.post(() -> img.setImageBitmap(thumb));
                });
            } else {
                int res = ThemeController.wallpaperRes(value);
                if (res != 0) img.setImageResource(res); else img.setBackgroundColor(Ui.color(c, android.R.attr.colorBackground));
            }
        }
        if (Prefs.WALLPAPER.equals(o.key)) {
            Weather.IO.execute(() -> {
                if (OnlineWallpapers.refresh(app)) root.post(() -> {
                    if (isAdded() && level == LEVEL_VALUES && Prefs.WALLPAPER.equals(openOption)) render(0);
                });
            });
        }
    }

    /** The TV's newest pictures as a grid (after asking for access); picking one makes it the wallpaper. */
    private void renderPictures() {
        Context c = requireContext();
        if (!CustomWallpaper.canRead(c)) {
            list.addView(row(getString(R.string.picture_allow), null, 0, PrefRow.Mark.CHEVRON, "allow",
                    () -> askPictures.launch(CustomWallpaper.permission())));
            return;
        }
        List<Long> ids = CustomWallpaper.pictures(c);
        if (ids.isEmpty()) {
            TextView none = new TextView(c, null, 0, R.style.Himal_Body);
            none.setText(R.string.picture_none);
            none.setPadding(Ui.px(c, R.dimen.tile_gap), 0, 0, 0);
            list.addView(none);
            return;
        }
        GridLayout grid = addGrid();
        Context app = c.getApplicationContext();
        for (int i = 0; i < ids.size(); i++) {
            long id = ids.get(i);
            ImageView img = addCell(grid, "pic:" + id, null, false, () -> pickPicture(id));
            ((View) img.getParent()).setContentDescription(getString(R.string.picture_n, i + 1));
            Weather.IO.execute(() -> {
                Bitmap thumb = CustomWallpaper.thumbnail(app, id);
                if (thumb != null) img.post(() -> img.setImageBitmap(thumb));
            });
        }
    }

    /** Saves the picture off the main thread, then applies it like any wallpaper choice. */
    /** Downloads an online wallpaper (once) off the main thread, then applies it like any wallpaper choice. */
    private void useOnline(String id) {
        OnlineWallpapers.Item item = OnlineWallpapers.find(requireContext(), id);
        if (item == null) return;
        Context app = requireContext().getApplicationContext();
        Weather.IO.execute(() -> {
            boolean ok = OnlineWallpapers.use(app, item);
            root.post(() -> {
                if (!isAdded()) return;
                if (!ok) {
                    Toast.makeText(requireContext(), R.string.wallpaper_download_failed, Toast.LENGTH_LONG).show();
                    return;
                }
                level = LEVEL_OPTIONS;
                focusTag = Prefs.WALLPAPER;
                render(-1);
                host().bindWallpaper();
                host().onSettingChanged(Prefs.WALLPAPER);
            });
        });
    }

    private void pickPicture(long id) {
        Context app = requireContext().getApplicationContext();
        Weather.IO.execute(() -> {
            boolean ok = CustomWallpaper.save(app, id);
            root.post(() -> {
                if (!isAdded()) return;
                if (!ok) {
                    Toast.makeText(requireContext(), R.string.picture_failed, Toast.LENGTH_LONG).show();
                    return;
                }
                prefs().set(Prefs.WALLPAPER, "custom");
                level = LEVEL_OPTIONS;
                focusTag = Prefs.WALLPAPER;
                render(-1);
                host().bindWallpaper();
                host().onSettingChanged(Prefs.WALLPAPER);
            });
        });
    }

    private GridLayout addGrid() {
        GridLayout grid = new GridLayout(requireContext());
        grid.setColumnCount(3);
        list.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return grid;
    }

    /**
     * Adds one 16:9 preview cell to {@code grid}: preview, then radio + name (omitted when {@code name} is null).
     * The focused cell gets the list rows' inverse highlight. @return the preview to fill.
     */
    private ImageView addCell(GridLayout grid, String tag, String name, boolean on, Runnable onClick) {
        Context c = requireContext();
        int pad = Ui.px(c, R.dimen.tile_gap);
        LinearLayout cell = new LinearLayout(c);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setTag(tag);
        cell.setBackgroundResource(R.drawable.bg_pref_row);
        cell.setFocusable(true);
        cell.setClickable(true);
        cell.setPadding(pad / 2, pad / 2, pad / 2, pad / 2);
        cell.setContentDescription(name);
        cell.setOnClickListener(v -> onClick.run());

        ShapeableImageView img = new ShapeableImageView(c);
        img.setShapeAppearanceModel(img.getShapeAppearanceModel().withCornerSize(pad * 2 / 3f));
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cell.addView(img, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.px(c, R.dimen.wallpaper_thumb_h)));

        if (name != null) {
            LinearLayout label = new LinearLayout(c);
            label.setGravity(Gravity.CENTER_VERTICAL);
            label.setDuplicateParentStateEnabled(true);
            label.setPadding(0, pad / 2, 0, 0);
            ImageView radio = new ImageView(c);
            radio.setImageResource(on ? R.drawable.ic_radio_button_checked : R.drawable.ic_radio_button_unchecked);
            radio.setImageTintList(AppCompatResources.getColorStateList(c, on ? R.color.pref_radio_on : R.color.pref_icon));
            radio.setDuplicateParentStateEnabled(true);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(pad * 3 / 2, pad * 3 / 2);
            rlp.setMarginEnd(pad / 2);
            label.addView(radio, rlp);
            TextView text = new TextView(c, null, 0, R.style.Himal_Body);
            text.setText(name);
            text.setSingleLine(true);
            text.setEllipsize(TextUtils.TruncateAt.END);
            text.setTextColor(AppCompatResources.getColorStateList(c, R.color.pref_title));
            text.setDuplicateParentStateEnabled(true);
            label.addView(text);
            cell.addView(label);
        }

        GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED),
                GridLayout.spec(GridLayout.UNDEFINED, 1f));
        lp.width = 0;
        lp.setMargins(pad / 4, pad / 4, pad / 4, pad / 4);
        grid.addView(cell, lp);
        return img;
    }

    /** @param icon leading symbol drawable, or 0 */
    private View row(String title, String summary, int icon, PrefRow.Mark mark, String tag, Runnable onClick) {
        Context c = requireContext();
        return PrefRow.create(c, title, summary, icon == 0 ? null : AppCompatResources.getDrawable(c, icon), true, mark, tag, onClick);
    }

    private Opt findOpt(String key) {
        if (key == null) return null;
        for (Opt o : optionsFor(openCategory)) if (o.key.equals(key)) return o;
        return null;
    }

    private int index(Opt o) {
        int i = Arrays.asList(o.values).indexOf(prefs().str(o.key));
        return Math.max(i, 0);
    }

    private void addLeagues() {
        Context c = requireContext();
        Set<String> selected = prefs().leagues();
        TextView header = new TextView(c, null, 0, R.style.Himal_Caption);
        header.setText(getString(R.string.leagues_header, selected.size()));
        header.setPadding(0, Ui.px(c, R.dimen.tile_gap), 0, 0);
        list.addView(header);
        for (Object[] group : LEAGUES) {
            LinearLayout line = new LinearLayout(c);
            line.setGravity(Gravity.CENTER_VERTICAL);
            TextView label = new TextView(c, null, 0, R.style.Himal_Caption);
            label.setText((int) group[0]);
            line.addView(label, new LinearLayout.LayoutParams(Ui.px(c, R.dimen.header_h) * 2, ViewGroup.LayoutParams.WRAP_CONTENT));
            ChipGroup chips = new ChipGroup(c);
            for (int i = 1; i < group.length; i += 2) {
                String id = (String) group[i], name = (String) group[i + 1];
                Chip chip = new Chip(c);
                bindChip(chip, name, selected.contains(id));
                chip.setOnClickListener(v -> {
                    Set<String> s = prefs().leagues();
                    if (!s.remove(id)) s.add(id);
                    prefs().setLeagues(s);
                    bindChip(chip, name, s.contains(id));
                    header.setText(getString(R.string.leagues_header, s.size()));
                });
                chips.addView(chip);
            }
            line.addView(chips, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            list.addView(line);
        }
    }

    private void bindChip(Chip chip, String name, boolean on) {
        Context c = requireContext();
        chip.setText(on ? getString(R.string.checked, name) : name); // never colour alone (PRD 11)
        chip.setChipBackgroundColor(ColorStateList.valueOf(Ui.color(c, on ? attr.colorPrimaryContainer : attr.colorSurfaceContainer)));
        chip.setTextColor(Ui.color(c, on ? attr.colorOnPrimaryContainer : attr.colorOnSurface));
    }

    // ---- Preview (PRD 6.6 column 3) ----

    /** Previews the screensaver while in its category, otherwise Home. */
    private void bindPreview() {
        bindHeaderClock();
        FrameLayout box = root.findViewById(R.id.preview);
        if (level != LEVEL_CATEGORIES && openCategory == CAT_SCREENSAVER) Preview.screensaver(box);
        else Preview.home(box);
    }
}
