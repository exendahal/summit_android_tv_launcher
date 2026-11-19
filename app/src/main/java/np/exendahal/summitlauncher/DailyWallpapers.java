package np.exendahal.summitlauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.view.View;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Wallpapers that change every day: Wikimedia Commons' Picture of the Day and NASA's Image of the Day (NASA's own
 * pictures, public domain; APOD is not used because most of its pictures are the photographers' copyright).
 * Each is fetched at most once a day, retried hourly on failure, and kept so the last one still shows offline.
 * Prefs store a chosen one as {@link #PREFIX} + source; its credit is shown over the picture.
 */
final class DailyWallpapers {
    private DailyWallpapers() {}

    static final String PREFIX = "daily:", WIKIMEDIA = "wikimedia", NASA = "nasa";
    /** Sources in the order of the wallpaper grid. */
    static final String[] SOURCES = {WIKIMEDIA, NASA};

    private static final String WIKIMEDIA_FEED = "https://en.wikipedia.org/api/rest_v1/feed/featured/";
    private static final String NASA_FEED = "https://www.nasa.gov/feeds/iotd-feed/";
    private static final Pattern NASA_ITEM = Pattern.compile("<item>.*?<title>(.*?)</title>.*?<enclosure url=\"([^\"]+)\"", Pattern.DOTALL);
    /** Wide enough for a 1080p TV; both sources resize on their side, so a download is well under 1 MB. */
    private static final int WIDTH = 1920;
    private static final long RETRY_MS = 60 * 60 * 1000L;

    private static final Map<String, Long> lastTry = new ConcurrentHashMap<>();
    private static final Map<String, Bitmap> cached = new ConcurrentHashMap<>();
    private static final AtomicBoolean busy = new AtomicBoolean();

    static boolean is(String value) { return value.startsWith(PREFIX); }

    /** @return the last downloaded picture of {@code source}, or null if none yet. */
    static Bitmap bitmap(Context c, String source) {
        Bitmap b = cached.get(source);
        File f = imageFile(c, source);
        if (b == null && f.exists()) {
            b = BitmapFactory.decodeFile(f.getPath());
            if (b != null) cached.put(source, b);
        }
        return b;
    }

    /** Shows the credit of wallpaper {@code value} in {@code view}, or hides it for wallpapers that need none. */
    static void bindCredit(TextView view, String value) {
        if (view == null) return;
        String credit = is(value) ? meta(view.getContext(), value.substring(PREFIX.length()))[1] : "";
        view.setText(credit);
        view.setVisibility(credit.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /**
     * Fetches today's picture for each daily wallpaper in use (Home's and the screensaver's) off the main thread,
     * then runs {@code onChanged} on the main thread if any changed. Cheap when nothing is due, so it can run every minute.
     */
    static void refreshInUse(Context c, Runnable onChanged) {
        Prefs p = Prefs.get(c);
        Set<String> due = new LinkedHashSet<>();
        for (String key : new String[]{Prefs.WALLPAPER, Prefs.AMBIENT_WALLPAPER}) {
            String v = p.str(key);
            if (!is(v)) continue;
            String source = v.substring(PREFIX.length());
            Long tried = lastTry.get(source);
            if (!isToday(c, source) && (tried == null || System.currentTimeMillis() - tried > RETRY_MS)) due.add(source);
        }
        if (due.isEmpty() || !busy.compareAndSet(false, true)) return;
        Context app = c.getApplicationContext();
        Weather.IO.execute(() -> {
            boolean changed = false;
            try {
                for (String source : due) changed |= download(app, source);
            } finally {
                busy.set(false);
            }
            if (changed) new Handler(Looper.getMainLooper()).post(onChanged);
        });
    }

    /**
     * Makes {@code source} the {@code key} wallpaper, downloading today's picture first if needed.
     * Call off the main thread. @return false if there is no picture (offline on first use).
     */
    static boolean use(Context c, String source, String key) {
        if (!isToday(c, source)) download(c, source);
        Bitmap b = bitmap(c, source);
        if (b == null) return false;
        Prefs p = Prefs.get(c);
        if (Prefs.WALLPAPER.equals(key)) p.set(Prefs.WALLPAPER_SEED, ThemeController.seedFor(c, b));
        p.set(key, PREFIX + source);
        return true;
    }

    /** Downloads today's picture of {@code source}. @return true if a new picture was saved. */
    private static boolean download(Context c, String source) {
        lastTry.put(source, System.currentTimeMillis());
        File tmp = new File(c.getCacheDir(), "daily-download");
        try {
            String[] picture = WIKIMEDIA.equals(source) ? wikimedia() : nasa(); // url, credit
            OnlineWallpapers.write(tmp, OnlineWallpapers.fetch(picture[0]));
            imageFile(c, source).getParentFile().mkdirs();
            Bitmap b = CustomWallpaper.saveScaled(c, Uri.fromFile(tmp), imageFile(c, source));
            if (b == null) return false;
            OnlineWallpapers.write(metaFile(c, source), (LocalDate.now() + "\n" + picture[1]).getBytes(StandardCharsets.UTF_8));
            cached.put(source, b);
            Prefs p = Prefs.get(c);
            if (p.is(Prefs.WALLPAPER, PREFIX + source)) p.set(Prefs.WALLPAPER_SEED, ThemeController.seedFor(c, b));
            return true;
        } catch (IOException | JSONException | RuntimeException e) {
            return false; // keep yesterday's picture; retried within the hour
        } finally {
            tmp.delete();
        }
    }

    /** @return today's Commons Picture of the Day: a 1920 px rendition and "artist · licence · Wikimedia Commons". */
    private static String[] wikimedia() throws IOException, JSONException {
        LocalDate d = LocalDate.now();
        String day = String.format(java.util.Locale.ROOT, "%d/%02d/%02d", d.getYear(), d.getMonthValue(), d.getDayOfMonth());
        JSONObject image = new JSONObject(new String(OnlineWallpapers.fetch(WIKIMEDIA_FEED + day), StandardCharsets.UTF_8))
                .getJSONObject("image");
        JSONObject original = image.getJSONObject("image");
        String url = original.getInt("width") > WIDTH // Commons renders thumbnails at standard widths such as 1920
                ? image.getJSONObject("thumbnail").getString("source").replaceFirst("/\\d+px-", "/" + WIDTH + "px-")
                : original.getString("source");
        String artist = image.has("artist") ? image.getJSONObject("artist").optString("text").trim() : "";
        String license = image.has("license") ? image.getJSONObject("license").optString("type") : "";
        return new String[]{url.split("\\?")[0], join(artist, license, "Wikimedia Commons")};
    }

    /** @return NASA's newest Image of the Day: a 1920 px rendition and "NASA · title". */
    private static String[] nasa() throws IOException {
        Matcher m = NASA_ITEM.matcher(new String(OnlineWallpapers.fetch(NASA_FEED), StandardCharsets.UTF_8));
        if (!m.find()) throw new IOException("no image in feed");
        String title = Html.fromHtml(m.group(1), Html.FROM_HTML_MODE_LEGACY).toString().trim();
        return new String[]{m.group(2) + "?w=" + WIDTH, join("NASA", title)};
    }

    private static String join(String... parts) {
        StringBuilder s = new StringBuilder();
        for (String p : parts) if (!p.isEmpty()) s.append(s.length() == 0 ? "" : " · ").append(p);
        return s.toString();
    }

    private static boolean isToday(Context c, String source) {
        return LocalDate.now().toString().equals(meta(c, source)[0]);
    }

    /** @return {date fetched, credit}, both "" before the first download. */
    private static String[] meta(Context c, String source) {
        File f = metaFile(c, source);
        try {
            String[] lines = f.exists() ? new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8).split("\n", 2) : new String[0];
            return new String[]{lines.length > 0 ? lines[0] : "", lines.length > 1 ? lines[1] : ""};
        } catch (IOException e) {
            return new String[]{"", ""};
        }
    }

    private static File imageFile(Context c, String source) { return new File(new File(c.getFilesDir(), "daily"), source + ".jpg"); }

    private static File metaFile(Context c, String source) { return new File(new File(c.getFilesDir(), "daily"), source + ".txt"); }
}
