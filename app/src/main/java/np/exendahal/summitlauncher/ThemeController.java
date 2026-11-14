package np.exendahal.summitlauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.ImageView;

import java.time.LocalTime;

/**
 * Resolves theme mode, seed colour and wallpaper from settings and applies them (PRD 5.2, 6.7).
 * A base theme (dark/light neutrals) is set first, then the seed overlay and optional high-contrast overlay.
 */
public final class ThemeController {
    private ThemeController() {}

    /** Theme › Colour › Custom choices, in grid order; {@link #CUSTOM_SWATCH} holds each one's swatch. */
    public static final String[] CUSTOM = {"red", "orange", "yellow", "green", "teal", "blue", "indigo", "purple", "pink"};
    public static final int[] CUSTOM_SWATCH = {R.color.c_red, R.color.c_orange, R.color.c_yellow, R.color.c_green,
            R.color.c_teal, R.color.c_blue, R.color.c_indigo, R.color.c_purple, R.color.c_pink};

    private static final String[] SEEDS = {"mountain", "phewa", "night", "terai", "flags",
            "red", "orange", "yellow", "green", "teal", "blue", "indigo", "purple", "pink"};
    private static final int[][] OVERLAYS = {
            {R.style.ThemeOverlay_Himal_Mountain_Dark, R.style.ThemeOverlay_Himal_Mountain_Light},
            {R.style.ThemeOverlay_Himal_Phewa_Dark, R.style.ThemeOverlay_Himal_Phewa_Light},
            {R.style.ThemeOverlay_Himal_Night_Dark, R.style.ThemeOverlay_Himal_Night_Light},
            {R.style.ThemeOverlay_Himal_Terai_Dark, R.style.ThemeOverlay_Himal_Terai_Light},
            {R.style.ThemeOverlay_Himal_Flags_Dark, R.style.ThemeOverlay_Himal_Flags_Light},
            {R.style.ThemeOverlay_Himal_CRed_Dark, R.style.ThemeOverlay_Himal_CRed_Light},
            {R.style.ThemeOverlay_Himal_COrange_Dark, R.style.ThemeOverlay_Himal_COrange_Light},
            {R.style.ThemeOverlay_Himal_CYellow_Dark, R.style.ThemeOverlay_Himal_CYellow_Light},
            {R.style.ThemeOverlay_Himal_CGreen_Dark, R.style.ThemeOverlay_Himal_CGreen_Light},
            {R.style.ThemeOverlay_Himal_CTeal_Dark, R.style.ThemeOverlay_Himal_CTeal_Light},
            {R.style.ThemeOverlay_Himal_CBlue_Dark, R.style.ThemeOverlay_Himal_CBlue_Light},
            {R.style.ThemeOverlay_Himal_CIndigo_Dark, R.style.ThemeOverlay_Himal_CIndigo_Light},
            {R.style.ThemeOverlay_Himal_CPurple_Dark, R.style.ThemeOverlay_Himal_CPurple_Light},
            {R.style.ThemeOverlay_Himal_CPink_Dark, R.style.ThemeOverlay_Himal_CPink_Light},
    };

    /**
     * Picks the custom colour that best matches a picture: each vivid pixel votes for the swatch nearest in hue,
     * weighted by saturation × brightness, so greys and shadows don't count.
     *
     * @param pixels   ARGB pixels of a small copy of the picture
     * @param swatches ARGB swatch colours to choose from
     * @return index into {@code swatches}, or -1 if the picture is too grey to have a colour
     */
    static int closestSwatch(int[] pixels, int[] swatches) {
        float[] hues = new float[swatches.length];
        for (int i = 0; i < swatches.length; i++) hues[i] = hsv(swatches[i])[0];
        float[] votes = new float[swatches.length];
        for (int px : pixels) {
            float[] h = hsv(px);
            if (h[1] < 0.25f || h[2] < 0.2f) continue; // grey or near-black
            int best = 0;
            for (int i = 1; i < hues.length; i++) if (hueGap(h[0], hues[i]) < hueGap(h[0], hues[best])) best = i;
            votes[best] += h[1] * h[2];
        }
        int win = -1;
        float most = pixels.length * 0.02f; // need some real colour, not a few stray pixels
        for (int i = 0; i < votes.length; i++) if (votes[i] > most) { most = votes[i]; win = i; }
        return win;
    }

    private static float hueGap(float a, float b) {
        float d = Math.abs(a - b);
        return Math.min(d, 360 - d);
    }

    /** @return {hue 0..360, saturation 0..1, value 0..1} (android.graphics.Color is not available in unit tests). */
    private static float[] hsv(int argb) {
        float r = (argb >> 16 & 0xFF) / 255f, g = (argb >> 8 & 0xFF) / 255f, b = (argb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        float h = d == 0 ? 0 : max == r ? 60 * (((g - b) / d) % 6) : max == g ? 60 * ((b - r) / d + 2) : 60 * ((r - g) / d + 4);
        return new float[]{h < 0 ? h + 360 : h, max == 0 ? 0 : d / max, max};
    }

    /** @return the custom colour id matching {@code picture}, or "" if it is too grey to have one. */
    static String seedFor(Context c, Bitmap picture) {
        int w = 32, h = 18;
        Bitmap small = Bitmap.createScaledBitmap(picture, w, h, true);
        int[] px = new int[w * h];
        small.getPixels(px, 0, w, 0, 0, w, h);
        if (small != picture) small.recycle();
        int[] swatches = new int[CUSTOM_SWATCH.length];
        for (int i = 0; i < swatches.length; i++) swatches[i] = c.getColor(CUSTOM_SWATCH[i]);
        int i = closestSwatch(px, swatches);
        return i < 0 ? "" : CUSTOM[i];
    }

    /** "Auto by time" = dark from 18:00 to 06:00 local time (PRD 9). */
    public static boolean isDark(Context c) {
        Prefs p = Prefs.get(c);
        if (p.is(Prefs.THEME_MODE, "dark")) return true;
        if (p.is(Prefs.THEME_MODE, "light")) return false;
        int h = LocalTime.now().getHour();
        return h >= c.getResources().getInteger(R.integer.dark_from_hour)
                || h < c.getResources().getInteger(R.integer.dark_until_hour);
    }

    /** @return the seed id for the current colour setting and wallpaper. */
    public static String seed(Prefs p) {
        if (p.is(Prefs.THEME_COLOUR, "coral")) return "phewa";
        if (p.is(Prefs.THEME_COLOUR, "green")) return "terai";
        if (p.is(Prefs.THEME_COLOUR, "custom")) return p.str(Prefs.THEME_CUSTOM);
        switch (p.str(Prefs.WALLPAPER)) {
            case "phewa": case "night": case "terai": case "flags": return p.str(Prefs.WALLPAPER);
            case "custom": return p.str(Prefs.WALLPAPER_SEED).isEmpty() ? "mountain" : p.str(Prefs.WALLPAPER_SEED);
            default: return "mountain";
        }
    }

    /** Applies base theme + seed + contrast overlays to an Activity or other themed context. */
    public static void apply(ContextThemeWrapper w) {
        Prefs p = Prefs.get(w);
        boolean dark = isDark(w);
        w.setTheme(dark ? R.style.Theme_Himal_Dark : R.style.Theme_Himal_Light);
        int seed = java.util.Arrays.asList(SEEDS).indexOf(seed(p));
        w.getTheme().applyStyle(OVERLAYS[Math.max(seed, 0)][dark ? 0 : 1], true);
        if (p.is(Prefs.CONTRAST, "high")) {
            w.getTheme().applyStyle(dark ? R.style.ThemeOverlay_Himal_Contrast_Dark : R.style.ThemeOverlay_Himal_Contrast_Light, true);
        }
    }

    /** Built-in wallpaper ids, in the order of {@code R.array.e_wallpaper}. */
    public static final String[] WALLPAPERS = {"mountains", "ridge", "plain", "phewa", "night", "terai", "flags"};

    /** @return the vector drawable for a wallpaper id, or 0 for Plain. */
    public static int wallpaperRes(String id) {
        switch (id) {
            case "ridge": return R.drawable.wp_ridge;
            case "phewa": return R.drawable.wp_phewa;
            case "night": return R.drawable.wp_night;
            case "terai": return R.drawable.wp_terai;
            case "flags": return R.drawable.wp_flags;
            case "plain": return 0;
            default: return R.drawable.wp_mountains;
        }
    }

    /** Illustrated wallpapers and pictures get the legibility tint; Mountain, Ridge and Plain do not (PRD 5.2). */
    public static boolean needsTint(String id) {
        return id.equals("custom") || id.equals("phewa") || id.equals("night") || id.equals("terai") || id.equals("flags");
    }

    /** Binds wallpaper, tint and dim views. Any argument may be null. */
    public static void bindWallpaper(Context c, ImageView wallpaper, View tint, View dim) {
        Prefs p = Prefs.get(c);
        String id = p.str(Prefs.WALLPAPER);
        Bitmap picture = "custom".equals(id) ? CustomWallpaper.bitmap(c) : null;
        if (wallpaper != null) {
            if (picture != null) wallpaper.setImageBitmap(picture); else wallpaper.setImageResource(wallpaperRes(id));
        }
        if (tint != null) tint.setVisibility(needsTint(id) ? View.VISIBLE : View.GONE);
        if (dim != null) {
            int d = p.integer(Prefs.WALLPAPER_DIM);
            dim.setAlpha(d == 60 ? Ui.fraction(c, R.dimen.dim_60) : d == 30 ? Ui.fraction(c, R.dimen.dim_30) : 0f);
        }
    }
}
