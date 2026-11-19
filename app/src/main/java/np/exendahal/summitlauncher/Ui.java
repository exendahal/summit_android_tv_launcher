package np.exendahal.summitlauncher;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;

import com.google.android.material.card.MaterialCardView;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Small shared UI helpers: units, theme colours, focus visuals, digits and date text. */
public final class Ui {
    public static boolean isTv(Context c) {
        return c.getPackageManager().hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK);
    }

    /** The screen a 1080p TV gives layouts, in dp. */
    private static final float TV_W_DP = 960f, TV_H_DP = 540f;

    /**
     * On phones and tablets (for trying UI changes without a TV) rescales dp so the screen is at least 960x540 dp,
     * like a 1080p TV, so every layout fits as it does there, only scaled. TVs are returned unchanged.
     */
    public static Context tvSized(Context c) {
        if (isTv(c)) return c;
        android.util.DisplayMetrics m = c.getResources().getDisplayMetrics();
        int w = m.widthPixels, h = m.heightPixels;
        float density = Math.min(Math.max(w, h) / TV_W_DP, Math.min(w, h) / TV_H_DP);
        android.content.res.Configuration cfg = new android.content.res.Configuration(c.getResources().getConfiguration());
        cfg.densityDpi = Math.round(density * android.util.DisplayMetrics.DENSITY_DEFAULT);
        cfg.screenWidthDp = Math.round(w / density);
        cfg.screenHeightDp = Math.round(h / density);
        cfg.smallestScreenWidthDp = Math.round(Math.min(w, h) / density);
        return c.createConfigurationContext(cfg);
    }

    private Ui() {}

    public static int px(Context c, int dimenRes) { return c.getResources().getDimensionPixelSize(dimenRes); }

    public static float fraction(Context c, int dimenRes) { return ResourcesCompat.getFloat(c.getResources(), dimenRes); }

    /** @return the colour value of a theme attribute. */
    public static int color(Context c, int attr) {
        TypedValue v = new TypedValue();
        c.getTheme().resolveAttribute(attr, v, true);
        return v.resourceId != 0 ? c.getColor(v.resourceId) : v.data;
    }

    /** Replaces ASCII digits with Devanagari digits. */
    public static String nepaliDigits(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (char ch : s.toCharArray()) b.append(ch >= '0' && ch <= '9' ? (char) ('०' + (ch - '0')) : ch);
        return b.toString();
    }

    /** Devanagari digits apply when the language is Nepali, or the setting is On and the TV is in Nepal (PRD 8.5). */
    public static String digits(Prefs p, String s) {
        boolean setting = p.bool(Prefs.NEPALI_DIGITS) && inNepal(p.context()); // the option only exists in Nepal
        return setting || p.is(Prefs.LANGUAGE, "ne") ? nepaliDigits(s) : s;
    }

    /** Device tier (PRD 12): low = low-RAM flag or under 2 GB; high = API 31+ with 3 GB+. */
    public static boolean isLowTier(Context c) {
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        return am.isLowRamDevice() || mi.totalMem < 2L * 1024 * 1024 * 1024;
    }

    /** Motion level: "full", "reduced" or "off". System animator scale 0 forces "off" (PRD 5.7). */
    public static String motion(Context c) {
        float scale = Settings.Global.getFloat(c.getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
        if (scale == 0f) return "off";
        String m = Prefs.get(c).str(Prefs.ANIMATIONS);
        return "full".equals(m) && isLowTier(c) ? "reduced" : m;
    }

    /**
     * Adds the focus visuals of PRD 7.3: scale up, ring (from the view's selector or card stroke),
     * corner morph on cards, and raised z so the tile draws above neighbours.
     */
    public static void focusable(View v, boolean pill) {
        Context c = v.getContext();
        String motion = motion(c);
        float scale = fraction(c, pill ? R.dimen.scale_focus_pill : R.dimen.scale_focus);
        int ring = px(c, R.dimen.focus_ring);
        int focusedRadius = px(c, R.dimen.r_focused);
        int dur = c.getResources().getInteger(R.integer.anim_focus);
        View.OnFocusChangeListener previous = v.getOnFocusChangeListener();
        v.setOnFocusChangeListener((view, has) -> {
            if (previous != null) previous.onFocusChange(view, has);
            if (view instanceof MaterialCardView) {
                MaterialCardView card = (MaterialCardView) view;
                card.setStrokeWidth(has ? ring : 0);
                Object base = card.getTag(R.id.tag_radius);
                if (base == null) { base = card.getRadius(); card.setTag(R.id.tag_radius, base); }
                if ((float) base > 0 && (float) base < focusedRadius) {
                    card.setRadius(has ? focusedRadius : (float) base);
                }
            }
            view.setTranslationZ(has ? 4 : 0);
            float target = has ? scale : 1f;
            if ("off".equals(motion)) {
                return; // ring only, no scale (PRD 5.7 reduced motion)
            }
            view.animate().scaleX(target).scaleY(target).setDuration(dur)
                    .setInterpolator("full".equals(motion) ? new OvershootInterpolator(1.4f) : new DecelerateInterpolator())
                    .start();
        });
    }

    /** Multiplies every TextView size under {@code root} (text size setting, PRD 5.3). */
    public static void scaleText(View root, float factor) {
        if (factor == 1f) return;
        if (root instanceof TextView) {
            TextView t = (TextView) root;
            t.setTextSize(TypedValue.COMPLEX_UNIT_PX, t.getTextSize() * factor);
        } else if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) scaleText(g.getChildAt(i), factor);
        }
    }

    /** @return the app language as a Locale. */
    public static Locale locale(Prefs p) { return Locale.forLanguageTag(p.str(Prefs.LANGUAGE)); }

    /** @return the health tip for {@code day} (Home, Settings preview and screensaver). */
    public static String healthTip(Context c, LocalDate day) {
        String[] tips = c.getResources().getStringArray(R.array.health);
        return tips[day.getDayOfMonth() % tips.length];
    }

    /** @return "7:42" or "19:42" per the time format setting. */
    public static String clock(Prefs p, LocalTime t) {
        String s = t.format(DateTimeFormatter.ofPattern(p.is(Prefs.TIME_FORMAT, "24h") ? "HH:mm" : "h:mm"));
        return digits(p, s);
    }

    /** @return an epoch-ms time as "8:30 PM" / "20:30" per the time format setting. */
    public static String time(Prefs p, long epochMs) {
        LocalTime t = java.time.Instant.ofEpochMilli(epochMs).atZone(java.time.ZoneId.systemDefault()).toLocalTime();
        String s = t.format(DateTimeFormatter.ofPattern(p.is(Prefs.TIME_FORMAT, "24h") ? "HH:mm" : "h:mm a", locale(p)));
        return digits(p, s);
    }

    /**
     * True when the TV is in Nepal: system region NP, or the Kathmandu time zone. The time zone matters because
     * many Android TV boxes sold in Nepal run an en-US locale (Nepal is often missing from the region list).
     * Reads the system configuration, not the app's per-app language.
     */
    public static boolean inNepal(Context c) {
        android.os.LocaleList system = android.content.res.Resources.getSystem().getConfiguration().getLocales();
        if (!system.isEmpty() && "NP".equalsIgnoreCase(system.get(0).getCountry())) return true;
        return isNepalTimeZone(java.util.TimeZone.getDefault().getID());
    }

    static boolean isNepalTimeZone(String id) { return "Asia/Kathmandu".equals(id) || "Asia/Katmandu".equals(id); }

    /** Effective calendar: the user's choice in Nepal; Gregorian only elsewhere (Bikram Sambat is Nepal's calendar). */
    public static String calendar(Context c, Prefs p) { return inNepal(c) ? p.str(Prefs.CALENDAR) : "ad"; }

    /** @return the BS line or null when the calendar setting hides it. */
    public static String bsDate(Context c, Prefs p, LocalDate d) {
        if (calendar(c, p).equals("ad")) return null;
        try {
            BikramSambat.load(c);
            return BikramSambat.fromGregorian(d).format(true);
        } catch (IllegalArgumentException e) {
            return null; // outside table range
        }
    }

    /** @return the AD line or null when the calendar setting hides it. */
    public static String adDate(Context c, Prefs p, LocalDate d) {
        if (calendar(c, p).equals("bs")) return null;
        return digits(p, d.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", locale(p))));
    }

    /** Sets text and hides the view when text is null. */
    public static void text(TextView v, CharSequence s) {
        v.setVisibility(s == null ? View.GONE : View.VISIBLE);
        if (s != null) v.setText(s);
    }

    /** Material 3 Expressive "cookie": 9-lobe scallop r(t) = R(1 + 0.085 cos 9t) (PRD 5.4). */
    public static final class CookieDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();

        /** Fills the cookie with the image (e.g. an app icon) instead of a colour; null when colour-filled. */
        private final Drawable image;

        public CookieDrawable(int color) { paint.setColor(color); image = null; }

        /** A cookie cut from {@code image}, used for app icons on Continue tiles. */
        public CookieDrawable(Drawable image) { this.image = image; }

        @Override public int getIntrinsicWidth() { return image == null ? -1 : image.getIntrinsicWidth(); }
        @Override public int getIntrinsicHeight() { return image == null ? -1 : image.getIntrinsicHeight(); }

        @Override
        protected void onBoundsChange(@NonNull Rect b) {
            if (image != null && b.width() > 0 && b.height() > 0) {
                // Paint the image through a shader so the scalloped edge stays anti-aliased (clipPath would not be).
                android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(b.width(), b.height(), android.graphics.Bitmap.Config.ARGB_8888);
                image.setBounds(0, 0, b.width(), b.height());
                image.draw(new Canvas(bmp));
                android.graphics.BitmapShader shader = new android.graphics.BitmapShader(bmp,
                        android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP);
                android.graphics.Matrix m = new android.graphics.Matrix();
                m.setTranslate(b.left, b.top);
                shader.setLocalMatrix(m);
                paint.setShader(shader);
            }
            path.reset();
            float cx = b.exactCenterX(), cy = b.exactCenterY();
            float r = Math.min(b.width(), b.height()) / 2f / 1.085f;
            for (int i = 0; i <= 360; i += 2) {
                double t = Math.toRadians(i);
                float rr = (float) (r * (1 + 0.085 * Math.cos(9 * t)));
                float x = cx + rr * (float) Math.cos(t), y = cy + rr * (float) Math.sin(t);
                if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
            }
            path.close();
        }

        @Override public void draw(@NonNull Canvas canvas) { canvas.drawPath(path, paint); }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter cf) { paint.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

}
