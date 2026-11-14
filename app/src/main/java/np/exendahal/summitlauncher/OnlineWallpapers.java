package np.exendahal.summitlauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Wallpapers published in the repository's {@code wallpapers/} folder, so new ones reach TVs without growing the
 * app. The list is refreshed at most once a day; a wallpaper's image is downloaded when it is first picked and kept,
 * so it keeps working offline. Prefs store a chosen one as {@link #PREFIX} + id.
 */
final class OnlineWallpapers {
    private OnlineWallpapers() {}

    static final String PREFIX = "online:";

    /** HEAD is the repository's default branch. */
    private static final String BASE = "https://raw.githubusercontent.com/exendahal/summit_android_tv_launcher/HEAD/wallpapers/";
    private static final long MAX_AGE_MS = 24 * 60 * 60 * 1000L;
    private static final int MAX_BYTES = 8 * 1024 * 1024;

    static final class Item {
        final String id, name, image, thumb;
        private final JSONObject names;

        Item(JSONObject o) throws JSONException {
            id = o.getString("id");
            name = o.getString("name");
            image = o.getString("image");
            thumb = o.getString("thumb");
            names = o.optJSONObject("names");
        }

        /**
         * @param language the app's language code (Prefs.LANGUAGE, e.g. "zh-Hant"); Locale codes are not used
         *                 because Android reports some as legacy codes ("iw" for Hebrew)
         * @return the name in that language when the entry has one, else the English name
         */
        String name(String language) {
            String local = names == null ? null : names.optString(language, null);
            return local == null || local.isEmpty() ? name : local;
        }
    }

    private static volatile String cachedId;
    private static volatile Bitmap cachedBitmap;

    /** @return the last downloaded list (empty before the first refresh). Reads a small local file. */
    static List<Item> list(Context c) {
        File index = indexFile(c);
        if (!index.exists()) return Collections.emptyList();
        try {
            return parse(new String(Files.readAllBytes(index.toPath()), StandardCharsets.UTF_8));
        } catch (IOException | JSONException e) {
            return Collections.emptyList();
        }
    }

    static Item find(Context c, String id) {
        for (Item i : list(c)) if (i.id.equals(id)) return i;
        return null;
    }

    /** Downloads the list if the cached copy is over a day old. Call off the main thread. @return true if it changed. */
    static boolean refresh(Context c) {
        File index = indexFile(c);
        if (index.exists() && System.currentTimeMillis() - index.lastModified() < MAX_AGE_MS) return false;
        try {
            byte[] fresh = fetch(BASE + "index.json");
            parse(new String(fresh, StandardCharsets.UTF_8)); // never cache a broken list
            byte[] old = index.exists() ? Files.readAllBytes(index.toPath()) : null;
            write(index, fresh);
            return old == null || !Arrays.equals(old, fresh);
        } catch (IOException | JSONException e) {
            return false;
        }
    }

    /** @return the thumbnail, downloading it once into the cache. Call off the main thread. */
    static Bitmap thumbnail(Context c, Item item) {
        File f = new File(new File(c.getCacheDir(), "online-thumbs"), item.id);
        try {
            if (!f.exists()) write(f, fetch(BASE + item.thumb));
            return BitmapFactory.decodeFile(f.getPath());
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Downloads {@code item} if needed and makes it the wallpaper, with the theme colour taken from the picture.
     * Call off the main thread. @return false if it could not be downloaded.
     */
    static boolean use(Context c, Item item) {
        File target = imageFile(c, item.id);
        Bitmap b = target.exists() ? BitmapFactory.decodeFile(target.getPath()) : null;
        if (b == null) {
            File download = new File(c.getCacheDir(), "online-download");
            try {
                write(download, fetch(BASE + item.image));
                b = CustomWallpaper.saveScaled(c, Uri.fromFile(download), target);
            } catch (IOException e) {
                b = null;
            } finally {
                download.delete();
            }
        }
        if (b == null) return false;
        cachedId = item.id;
        cachedBitmap = b;
        Prefs p = Prefs.get(c);
        p.set(Prefs.WALLPAPER_SEED, ThemeController.seedFor(c, b));
        p.set(Prefs.WALLPAPER, PREFIX + item.id);
        return true;
    }

    /** @return the downloaded wallpaper {@code id}, or null if it is not on this TV. */
    static Bitmap bitmap(Context c, String id) {
        if (id.equals(cachedId) && cachedBitmap != null) return cachedBitmap;
        File f = imageFile(c, id);
        Bitmap b = f.exists() ? BitmapFactory.decodeFile(f.getPath()) : null;
        if (b != null) {
            cachedId = id;
            cachedBitmap = b;
        }
        return b;
    }

    private static List<Item> parse(String json) throws JSONException {
        JSONArray all = new JSONObject(json).getJSONArray("wallpapers");
        List<Item> items = new ArrayList<>();
        for (int i = 0; i < all.length(); i++) items.add(new Item(all.getJSONObject(i)));
        return items;
    }

    private static byte[] fetch(String url) throws IOException {
        HttpURLConnection http = (HttpURLConnection) new URL(url).openConnection();
        http.setConnectTimeout(10_000);
        http.setReadTimeout(20_000);
        try {
            if (http.getResponseCode() != HttpURLConnection.HTTP_OK) throw new IOException("HTTP " + http.getResponseCode());
            try (InputStream in = http.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[16 * 1024];
                for (int n; (n = in.read(buf)) > 0; ) {
                    out.write(buf, 0, n);
                    if (out.size() > MAX_BYTES) throw new IOException("too large");
                }
                return out.toByteArray();
            }
        } finally {
            http.disconnect();
        }
    }

    private static void write(File f, byte[] data) throws IOException {
        File dir = f.getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) throw new IOException("cannot create " + dir);
        File tmp = new File(f.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) { out.write(data); }
        if (!tmp.renameTo(f)) throw new IOException("cannot write " + f);
    }

    private static File indexFile(Context c) { return new File(new File(c.getFilesDir(), "online"), "index.json"); }

    private static File imageFile(Context c, String id) { return new File(new File(c.getFilesDir(), "online"), id + ".jpg"); }
}
