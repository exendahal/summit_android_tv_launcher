package np.exendahal.summitlauncher;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.speech.RecognizerIntent;
import android.text.TextUtils;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Watch TV source resolution and launching, plus voice search (PRD 6.9, 6.11). */
public final class TvSource {
    private TvSource() {}

    /**
     * @return physical TV inputs (HDMI, AV, built-in tuner). Software inputs that apps register for
     * previews (Play Movies, VLC) are excluded: hardware inputs are passthrough or carry a "/HW" id.
     */
    public static List<TvInputInfo> inputs(Context c) {
        TvInputManager m = (TvInputManager) c.getSystemService(Context.TV_INPUT_SERVICE);
        if (m == null) return Collections.emptyList();
        List<TvInputInfo> out = new ArrayList<>();
        try {
            for (TvInputInfo i : m.getTvInputList()) {
                if (i.isPassthroughInput() || i.getId().contains("/HW")) out.add(i);
            }
        } catch (RuntimeException ignored) {
            // no TV input framework
        }
        return out;
    }

    /** @return the configured input, else the first HDMI input, else the first input, else null. */
    public static TvInputInfo currentInput(Context c) {
        String id = Prefs.get(c).str(Prefs.LIVE_INPUT);
        List<TvInputInfo> all = inputs(c);
        for (TvInputInfo i : all) if (i.getId().equals(id)) return i;
        for (TvInputInfo i : all) if (i.getType() == TvInputInfo.TYPE_HDMI && i.getParentId() == null) return i;
        for (TvInputInfo i : all) if (i.getType() == TvInputInfo.TYPE_HDMI) return i;
        return all.isEmpty() ? null : all.get(0);
    }

    public static String label(Context c, TvInputInfo i) {
        CharSequence custom = i.loadCustomLabel(c);
        return TextUtils.isEmpty(custom) ? i.loadLabel(c).toString() : custom.toString();
    }

    /**
     * @return apps the user can pick as their live-TV source: every TV app. Apps' self-declared categories
     * are too unreliable to pre-filter (e.g. VLC declares itself audio).
     */
    public static List<AppsRepo.App> liveApps(Context c) { return AppsRepo.all(c); }

    /** Caption under "Watch TV": input label, app name, or "Ask every time". */
    public static String caption(Context c) {
        Prefs p = Prefs.get(c);
        if (p.is(Prefs.LIVE_TYPE, "app")) {
            AppsRepo.App a = AppsRepo.find(c, p.str(Prefs.LIVE_APP));
            return a != null ? a.label : c.getString(R.string.ask_every_time);
        }
        TvInputInfo i = currentInput(c);
        return i == null ? c.getString(R.string.no_tv_input) : label(c, i);
    }

    /** Opens the configured source. Never throws; shows a message when switching is not possible. */
    public static void watch(Activity a) {
        Prefs p = Prefs.get(a);
        if (p.is(Prefs.LIVE_TYPE, "app")) {
            AppsRepo.App app = AppsRepo.find(a, p.str(Prefs.LIVE_APP));
            if (app != null) { AppsRepo.launch(a, app); return; }
            List<AppsRepo.App> apps = liveApps(a);
            CharSequence[] names = new CharSequence[apps.size()];
            for (int i = 0; i < names.length; i++) names[i] = apps.get(i).label;
            new MaterialAlertDialogBuilder(a).setTitle(R.string.choose_live_app)
                    .setItems(names, (d, which) -> AppsRepo.launch(a, apps.get(which))).show();
            return;
        }
        TvInputInfo input = currentInput(a);
        if (input != null) {
            Intent i = new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(input.getId()))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                a.startActivity(i);
                return;
            } catch (ActivityNotFoundException | SecurityException ignored) {
                // fall back to the system TV app below
            }
            Intent tvApp = a.getPackageManager().getLeanbackLaunchIntentForPackage("com.android.tv");
            if (tvApp != null) {
                try { a.startActivity(tvApp); return; } catch (ActivityNotFoundException ignored) { }
            }
        }
        Toast.makeText(a, R.string.tv_switch_failed, Toast.LENGTH_LONG).show();
    }

    /** Voice search via the assistant, then the speech recogniser (PRD 6.11). */
    public static void voiceSearch(Activity a) {
        Intent[] tries = {
                new Intent(Intent.ACTION_ASSIST),
                new Intent(RecognizerIntent.ACTION_WEB_SEARCH),
                new Intent(Intent.ACTION_SEARCH_LONG_PRESS),
        };
        for (Intent i : tries) {
            try {
                a.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            } catch (ActivityNotFoundException | SecurityException ignored) { }
        }
        Toast.makeText(a, R.string.voice_unavailable, Toast.LENGTH_LONG).show();
    }

    /** Starts a system screen by intent action; returns false when the device lacks it. */
    public static boolean open(Activity a, String action) {
        try {
            a.startActivity(new Intent(action));
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }
}
