package np.exendahal.summitlauncher;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.DisplayMetrics;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * A wallpaper picked from the TV's pictures (MediaStore). The pick is copied, scaled to the screen and turned
 * upright, into app storage, so it survives the original being deleted and needs no storage access afterwards.
 */
public final class CustomWallpaper {
    private CustomWallpaper() {}

    /** Newest pictures listed in the picker; older ones are not paged in. */
    private static final int MAX_PICTURES = 60;
    private static final int THUMB_W = 320;

    /** Saved pictures by pref key (Home and screensaver each have their own). */
    private static final java.util.Map<String, Bitmap> cached = new java.util.concurrent.ConcurrentHashMap<>();

    /** Permission needed to list the TV's pictures. */
    public static String permission() {
        return Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_IMAGES : Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    public static boolean canRead(Context c) {
        return c.checkSelfPermission(permission()) == PackageManager.PERMISSION_GRANTED;
    }

    /** @return the picture saved for {@code key} (Prefs.WALLPAPER or AMBIENT_WALLPAPER), or null if none (or unreadable). */
    public static Bitmap bitmap(Context c, String key) {
        Bitmap b = cached.get(key);
        if (b == null && file(c, key).exists()) {
            b = BitmapFactory.decodeFile(file(c, key).getPath());
            if (b != null) cached.put(key, b);
        }
        return b;
    }

    /** @return MediaStore ids of the newest pictures, newest first. */
    public static List<Long> pictures(Context c) {
        List<Long> ids = new ArrayList<>();
        try (Cursor q = c.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                new String[]{MediaStore.Images.Media._ID}, null, null, MediaStore.Images.Media.DATE_ADDED + " DESC")) {
            while (q != null && q.moveToNext() && ids.size() < MAX_PICTURES) ids.add(q.getLong(0));
        } catch (SecurityException e) {
            ids.clear(); // access was revoked
        }
        return ids;
    }

    /** Small preview for the picker grid. Call off the main thread. @return null if unreadable. */
    public static Bitmap thumbnail(Context c, long id) {
        Bitmap b = decode(c, uri(id), THUMB_W, THUMB_W * 9 / 16, Bitmap.Config.RGB_565);
        if (b == null || b.getWidth() <= THUMB_W) return b;
        return replace(b, Bitmap.createScaledBitmap(b, THUMB_W, Math.max(1, b.getHeight() * THUMB_W / b.getWidth()), true));
    }

    /** Whether the TV has a system picker for pictures (many Android TV builds have none). */
    public static boolean canPick(Context c) {
        return new android.content.Intent(android.content.Intent.ACTION_GET_CONTENT).setType("image/*")
                .addCategory(android.content.Intent.CATEGORY_OPENABLE).resolveActivity(c.getPackageManager()) != null;
    }

    /** Copies {@code picture} into app storage as the {@code key} wallpaper. Call off the main thread. @return success. */
    public static boolean save(Context c, String key, Uri picture) {
        Bitmap b = saveScaled(c, picture, file(c, key));
        if (b == null) return false;
        cached.put(key, b);
        if (Prefs.WALLPAPER.equals(key)) Prefs.get(c).set(Prefs.WALLPAPER_SEED, ThemeController.seedFor(c, b));
        return true;
    }

    /**
     * Decodes {@code src}, turns it upright, scales it to cover the screen and writes it to {@code target} as JPEG.
     * Call off the main thread. @return the saved bitmap, or null if unreadable or not writable.
     */
    static Bitmap saveScaled(Context c, Uri src, File target) {
        DisplayMetrics m = c.getResources().getDisplayMetrics();
        int sw = Math.max(m.widthPixels, m.heightPixels), sh = Math.min(m.widthPixels, m.heightPixels);
        Bitmap b = decode(c, src, sw, sh, Bitmap.Config.ARGB_8888);
        if (b == null) return null;
        float scale = Math.max((float) sw / b.getWidth(), (float) sh / b.getHeight()); // cover the screen
        if (scale < 1f) b = replace(b, Bitmap.createScaledBitmap(b, Math.round(b.getWidth() * scale), Math.round(b.getHeight() * scale), true));
        File tmp = new File(target.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            if (!b.compress(Bitmap.CompressFormat.JPEG, 90, out)) return null;
        } catch (IOException e) {
            return null;
        }
        return tmp.renameTo(target) ? b : null;
    }

    /** "wallpaper.jpg" for Home (the name older versions used), "ambient_wallpaper.jpg" for the screensaver. */
    private static File file(Context c, String key) { return new File(c.getFilesDir(), key + ".jpg"); }

    static Uri uri(long id) { return ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id); }

    /** Decodes at the smallest power-of-two size still covering w×h, turned upright by its EXIF orientation. */
    private static Bitmap decode(Context c, Uri uri, int w, int h, Bitmap.Config config) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) { BitmapFactory.decodeStream(in, null, o); }
            int degrees = 0;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                switch (new ExifInterface(in).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    case ExifInterface.ORIENTATION_ROTATE_90: degrees = 90; break;
                    case ExifInterface.ORIENTATION_ROTATE_180: degrees = 180; break;
                    case ExifInterface.ORIENTATION_ROTATE_270: degrees = 270; break;
                    default: break;
                }
            } catch (IOException e) {
                degrees = 0; // no readable EXIF (e.g. PNG)
            }
            int srcW = degrees % 180 == 0 ? o.outWidth : o.outHeight, srcH = degrees % 180 == 0 ? o.outHeight : o.outWidth;
            if (srcW <= 0 || srcH <= 0) return null;
            o.inSampleSize = 1;
            while (srcW / (o.inSampleSize * 2) >= w && srcH / (o.inSampleSize * 2) >= h) o.inSampleSize *= 2;
            o.inJustDecodeBounds = false;
            o.inPreferredConfig = config;
            Bitmap b;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) { b = BitmapFactory.decodeStream(in, null, o); }
            if (b == null || degrees == 0) return b;
            Matrix r = new Matrix();
            r.postRotate(degrees);
            return replace(b, Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), r, true));
        } catch (IOException | SecurityException | OutOfMemoryError e) {
            return null;
        }
    }

    /** Frees {@code old} as soon as its transformed copy exists; full-size photos are large on 1 GB boxes. */
    private static Bitmap replace(Bitmap old, Bitmap copy) {
        if (copy != old) old.recycle();
        return copy;
    }
}
