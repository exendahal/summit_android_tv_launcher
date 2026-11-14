package np.exendahal.summitlauncher;

import android.app.Activity;
import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Process;
import android.widget.Toast;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Installed TV apps, favourites and recents (PRD 6.5, 8.1, 8.2). */
public final class AppsRepo {
    private AppsRepo() {}

    /** One leanback-launchable activity. Icon and banner load lazily on first bind. */
    public static final class App {
        public final String label, pkg;
        public final ComponentName component;
        public final boolean system;
        final ActivityInfo info;
        private Drawable icon, banner;
        private boolean bannerLoaded;

        App(ResolveInfo ri, PackageManager pm) {
            info = ri.activityInfo;
            label = ri.loadLabel(pm).toString();
            pkg = info.packageName;
            component = new ComponentName(pkg, info.name);
            system = (info.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        }

        public Drawable icon(PackageManager pm) {
            if (icon == null) icon = info.loadIcon(pm);
            return icon;
        }

        /** @return activity banner, then application banner, else null (PRD 8.1). */
        public Drawable banner(PackageManager pm) {
            if (!bannerLoaded) { banner = info.loadBanner(pm); bannerLoaded = true; }
            return banner;
        }
    }

    private static List<App> cache;

    /** @return all leanback apps except this launcher, sorted with a locale-aware collator. */
    public static synchronized List<App> all(Context c) {
        if (cache != null) return cache;
        PackageManager pm = c.getPackageManager();
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<App> out = new ArrayList<>();
        for (ResolveInfo ri : pm.queryIntentActivities(i, 0)) {
            if (!ri.activityInfo.packageName.equals(c.getPackageName())) out.add(new App(ri, pm));
        }
        Collator col = Collator.getInstance();
        out.sort((a, b) -> col.compare(a.label, b.label));
        cache = Collections.unmodifiableList(out);
        return cache;
    }

    /** Drops the cache after a package change so the next read re-queries. */
    public static synchronized void invalidate() { cache = null; }

    public static App find(Context c, String pkg) {
        for (App a : all(c)) if (a.pkg.equals(pkg)) return a;
        return null;
    }

    /** Launches the app's leanback activity; shows a message instead of crashing. */
    public static void launch(Activity a, App app) {
        try {
            a.startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                    .setComponent(app.component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(a, R.string.cannot_open, Toast.LENGTH_SHORT).show();
        }
    }

    /** @return favourites in display order; defaults to the first N apps alphabetically (PRD 6.2). */
    public static List<App> favourites(Context c) {
        Prefs p = Prefs.get(c);
        List<String> pkgs = p.favourites();
        List<App> out = new ArrayList<>();
        if (pkgs == null) {
            List<App> all = all(c);
            out.addAll(all.subList(0, Math.min(all.size(), c.getResources().getInteger(R.integer.default_favourites))));
        } else {
            for (String pkg : pkgs) { App a = find(c, pkg); if (a != null) out.add(a); }
        }
        if (p.is(Prefs.FAV_ORDER, "most_used") && usageGranted(c)) {
            Map<String, Long> fg = usage(c, 30);
            out.sort((x, y) -> Long.compare(value(fg, y.pkg), value(fg, x.pkg)));
        }
        return out;
    }

    private static long value(Map<String, Long> m, String k) { Long v = m.get(k); return v == null ? 0 : v; }

    public static boolean isFavourite(Context c, String pkg) {
        for (App a : favourites(c)) if (a.pkg.equals(pkg)) return true;
        return false;
    }

    /** Pins or unpins {@code pkg}. */
    public static void setFavourite(Context c, String pkg, boolean pinned) {
        List<String> list = new ArrayList<>();
        for (App a : favourites(c)) list.add(a.pkg);
        list.remove(pkg);
        if (pinned) list.add(pkg);
        Prefs.get(c).setFavourites(list);
    }

    /** True when the user granted usage access (needed for recents and "Most used"). */
    public static boolean usageGranted(Context c) {
        AppOpsManager ops = (AppOpsManager) c.getSystemService(Context.APP_OPS_SERVICE);
        return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName())
                == AppOpsManager.MODE_ALLOWED;
    }

    /** Recent TV app with last-used time. */
    public static final class Recent {
        public final App app;
        public final long lastUsed;
        Recent(App app, long lastUsed) { this.app = app; this.lastUsed = lastUsed; }
    }

    /** @return up to {@code max} TV apps used in the last 7 days, newest first (PRD 8.2). */
    public static List<Recent> recents(Context c, int max) {
        List<Recent> out = new ArrayList<>();
        if (!usageGranted(c)) return out;
        Map<String, Long> last = new HashMap<>();
        UsageStatsManager um = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
        long now = System.currentTimeMillis();
        for (UsageStats s : um.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 7L * 86_400_000, now)) {
            if (s.getTotalTimeInForeground() > 0 && s.getLastTimeUsed() > value(last, s.getPackageName())) {
                last.put(s.getPackageName(), s.getLastTimeUsed());
            }
        }
        for (App a : all(c)) if (last.containsKey(a.pkg)) out.add(new Recent(a, last.get(a.pkg)));
        out.sort((x, y) -> Long.compare(y.lastUsed, x.lastUsed));
        return out.size() > max ? out.subList(0, max) : out;
    }

    private static Map<String, Long> usage(Context c, int days) {
        Map<String, Long> fg = new HashMap<>();
        UsageStatsManager um = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
        long now = System.currentTimeMillis();
        for (UsageStats s : um.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - days * 86_400_000L, now)) {
            fg.put(s.getPackageName(), value(fg, s.getPackageName()) + s.getTotalTimeInForeground());
        }
        return fg;
    }

    /** @return "Resume · 2 h ago", "Yesterday", "3 days ago" or "Last week". */
    public static String relativeTime(Context c, long when) {
        long min = Math.max(0, (System.currentTimeMillis() - when) / 60_000);
        if (min < 1) return c.getString(R.string.resume_now);
        if (min < 60) return c.getString(R.string.resume_min, min);
        if (min < 24 * 60) return c.getString(R.string.resume_h, min / 60);
        long days = min / (24 * 60);
        if (days == 1) return c.getString(R.string.yesterday);
        if (days < 7) return c.getString(R.string.days_ago, days);
        return c.getString(R.string.last_week);
    }
}
