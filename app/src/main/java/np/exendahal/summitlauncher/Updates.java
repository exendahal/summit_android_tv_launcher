package np.exendahal.summitlauncher;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.google.android.play.core.appupdate.AppUpdateManagerFactory;
import com.google.android.play.core.install.model.UpdateAvailability;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

/**
 * Tells Settings when a newer version exists: on Google Play for the Play build, on GitHub releases otherwise.
 * Nothing is downloaded or installed here; the user is sent to the store or releases page.
 */
final class Updates {
    private Updates() {}

    /** Shown to the user, and opened in a browser where the TV has one. */
    static final String PAGE = "github.com/exendahal/summit_android_tv_launcher/releases";
    private static final String API = "https://api.github.com/repos/exendahal/summit_android_tv_launcher/releases/latest";

    /** Kept for the life of the process. */
    private static volatile boolean checked;
    /** The newer version's name ("" when Play doesn't say), or null when this build is the latest. */
    private static volatile String newer;

    static boolean checked() { return checked; }

    /** @return the newer version's name ("" if unknown), or null if there is none or no check has succeeded. */
    static String newer() { return newer; }

    /** Asks GitHub for the latest release. Call off the main thread; a failure just leaves it unchecked. */
    static void check() {
        try {
            String tag = new JSONObject(Weather.get(API)).getString("tag_name");
            newer = isNewer(tag, BuildConfig.VERSION_NAME) ? tag : null;
            checked = true;
        } catch (IOException | JSONException e) {
            // offline, rate-limited or no release yet: try again the next time Settings opens
        }
    }

    /** Asks Google Play; {@code done} runs on the main thread after a successful check. A failure (not installed
     *  from Play, no Play account, offline) just leaves it unchecked. */
    static void checkPlay(Context c, Runnable done) {
        AppUpdateManagerFactory.create(c).getAppUpdateInfo().addOnSuccessListener(info -> {
            newer = info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE ? "" : null;
            checked = true;
            done.run();
        });
    }

    /** Opens this app's Play Store page, falling back to the web listing. @return false if nothing could open it. */
    static boolean openPlay(Context c) {
        String id = c.getPackageName();
        for (String url : new String[] {"market://details?id=" + id, "https://play.google.com/store/apps/details?id=" + id}) {
            try {
                c.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return true;
            } catch (RuntimeException e) {
                // no Play Store / browser for this scheme: try the next
            }
        }
        return false;
    }

    /** Compares dotted versions number by number; a leading "v" and any suffix ("-beta") are ignored. */
    static boolean isNewer(String tag, String current) {
        String[] a = tag.replaceFirst("^[vV]", "").split("\\."), b = current.replaceFirst("^[vV]", "").split("\\.");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = number(a, i), y = number(b, i);
            if (x != y) return x > y;
        }
        return false;
    }

    private static int number(String[] parts, int i) {
        if (i >= parts.length) return 0;
        String digits = parts[i].replaceFirst("\\D.*", "");
        try { return digits.isEmpty() ? 0 : Integer.parseInt(digits); } catch (NumberFormatException e) { return 0; }
    }
}
