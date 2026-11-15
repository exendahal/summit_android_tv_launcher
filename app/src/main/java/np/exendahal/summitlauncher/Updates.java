package np.exendahal.summitlauncher;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

/** Tells Settings when GitHub has a newer release than this build. Nothing is downloaded or installed. */
final class Updates {
    private Updates() {}

    /** Shown to the user, and opened in a browser where the TV has one. */
    static final String PAGE = "github.com/exendahal/summit_android_tv_launcher/releases";
    private static final String API = "https://api.github.com/repos/exendahal/summit_android_tv_launcher/releases/latest";

    /** Tag of the latest release (e.g. "v0.2.0"); null until a check succeeds. Kept for the life of the process. */
    private static volatile String latest;

    static boolean checked() { return latest != null; }

    /** Asks GitHub for the latest release. Call off the main thread; a failure just leaves it unchecked. */
    static void check() {
        try {
            latest = new JSONObject(Weather.get(API)).getString("tag_name");
        } catch (IOException | JSONException e) {
            // offline, rate-limited or no release yet: try again the next time Settings opens
        }
    }

    /** @return the latest release's tag if it is newer than this build, else null. */
    static String newer() {
        String tag = latest;
        return tag != null && isNewer(tag, BuildConfig.VERSION_NAME) ? tag : null;
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
