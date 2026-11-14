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

    private static final String FILE = "wallpaper.jpg";
    /** Newest pictures listed in the picker; older ones are not paged in. */
    private static final int MAX_PICTURES = 60;
    private static final int THUMB_W = 320;

    private static volatile Bitmap cached;

    /** Permission needed to list the TV's pictures. */
    public static String permission() {
        return Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_IMAGES : Manifest.permission.READ_EXTERNAL_STORAGE;
    }

    public static boolean canRead(Context c) {
        return c.checkSelfPermission(permission()) == PackageManager.PERMISSION_GRANTED;
    }

    /** @return the saved wallpaper, or null if none (or unreadable). */
    public static Bitmap bitmap(Context c) {
        Bitmap b = cached;
        if (b == null && file(c).exists()) cached = b = BitmapFactory.decodeFile(file(c).getPath());
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

    /** Copies picture {@code id} into app storage as the wallpaper. Call off the main thread. @return success. */
    public static boolean save(Context c, long id) {
        DisplayMetrics m = c.getResources().getDisplayMetrics();
        int sw = Math.max(m.widthPixels, m.heightPixels), sh = Math.min(m.widthPixels, m.heightPixels);
        Bitmap b = decode(c, uri(id), sw, sh, Bitmap.Config.ARGB_8888);
        if (b == null) return false;
        float scale = Math.max((float) sw / b.getWidth(), (float) sh / b.getHeight()); // cover the screen
        if (scale < 1f) b = replace(b, Bitmap.createScaledBitmap(b, Math.round(b.getWidth() * scale), Math.round(b.getHeight() * scale), true));
        File tmp = new File(c.getFilesDir(), FILE + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            if (!b.compress(Bitmap.CompressFormat.JPEG, 90, out)) return false;
        } catch (IOException e) {
            return false;
        }
        if (!tmp.renameTo(file(c))) return false;
        cached = b;
        Prefs.get(c).set(Prefs.WALLPAPER_SEED, ThemeController.seedFor(c, b));
        return true;
    }

    private static File file(Context c) { return new File(c.getFilesDir(), FILE); }

    private static Uri uri(long id) { return ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id); }

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
