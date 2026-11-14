package np.exendahal.summitlauncher;

import android.content.Context;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * The Settings preview: Home or the screensaver, built from their own layouts at full screen size and scaled into
 * the preview box, so the preview cannot drift from the real screens. Home shows blank placeholder apps.
 */
final class Preview {
    private Preview() {}

    private static final int RECENTS = 4, FAVOURITES = 6;

    private static final TileAdapter.Listener NONE = new TileAdapter.Listener() {
        @Override public void onClick(TileAdapter.Tile t, int position) {}
        @Override public void onLongClick(TileAdapter.Tile t, int position) {}
    };

    /** Shows Home over the box's own {@code pv_wallpaper} and {@code pv_tint} layers. */
    static void home(FrameLayout box) {
        if (box.getWidth() == 0) return;
        ThemeController.bindWallpaper(box.getContext(), box.findViewById(R.id.pv_wallpaper), box.findViewById(R.id.pv_tint), null);
        place(box, buildHome(box.getContext(), box));
    }

    /** Shows the screensaver; its weather line follows once loaded (usually from cache). */
    static void screensaver(FrameLayout box) {
        if (box.getWidth() == 0) return;
        Context c = box.getContext();
        View dream = LayoutInflater.from(c).inflate(R.layout.dream, box, false);
        ThemeController.bindWallpaper(c, dream.findViewById(R.id.wallpaper), null, null);
        Ui.scaleText(dream, Prefs.get(c).decimal(Prefs.TEXT_SCALE));
        AmbientDreamService.bind(c, dream, null, null);
        place(box, dream);
        Context app = c.getApplicationContext();
        Weather.IO.execute(() -> {
            String line = AmbientDreamService.weatherLine(app, Weather.load(app));
            dream.post(() -> { if (dream.isAttachedToWindow()) AmbientDreamService.bind(c, dream, line, null); });
        });
    }

    /** Replaces the box's preview with {@code screen}, laid out at screen size and scaled to the box's width. */
    private static void place(FrameLayout box, View screen) {
        DisplayMetrics m = box.getResources().getDisplayMetrics();
        int screenW = Math.max(m.widthPixels, m.heightPixels), screenH = Math.min(m.widthPixels, m.heightPixels);
        float scale = (float) box.getWidth() / screenW;

        ViewGroup.LayoutParams lp = box.getLayoutParams();
        int height = Math.round(screenH * scale);
        if (lp.height != height) {
            lp.height = height;
            box.setLayoutParams(lp);
        }

        View old = box.findViewWithTag(Preview.class);
        if (old != null) box.removeView(old);
        screen.setTag(Preview.class);
        screen.setPivotX(0);
        screen.setPivotY(0);
        screen.setScaleX(scale);
        screen.setScaleY(scale);
        box.addView(screen, new FrameLayout.LayoutParams(screenW, screenH));
    }

    private static View buildHome(Context c, ViewGroup parent) {
        Prefs p = Prefs.get(c);
        LocalDate today = LocalDate.now();
        View home = LayoutInflater.from(c).inflate(R.layout.fragment_home, parent, false);

        home.findViewById(R.id.nav_home).setSelected(true);
        home.findViewById(R.id.weather_chip).setVisibility(p.bool(Prefs.WEATHER_SHOW) ? View.VISIBLE : View.GONE);
        ((ImageView) home.findViewById(R.id.weather_icon)).setImageResource(R.drawable.ic_partly_cloudy_day);
        Ui.text(home.findViewById(R.id.clock), Ui.clock(p, LocalTime.now()));
        Ui.text(home.findViewById(R.id.bs_date), Ui.bsDate(c, p, today));
        Ui.text(home.findViewById(R.id.ad_date), Ui.adDate(c, p, today));
        Ui.text(home.findViewById(R.id.health_line), p.is(Prefs.HEALTH_LINE, "home_ambient") ? Ui.healthTip(c, today) : null);

        List<TileAdapter.Tile> recents = new ArrayList<>();
        if (p.is(Prefs.WATCH_TILE, "first")) {
            recents.add(new TileAdapter.Tile(TileAdapter.WATCH, null, c.getString(R.string.watch_tv), TvSource.caption(c), 0, "watch"));
        }
        if (p.bool(Prefs.CONTINUE_ROW)) {
            for (int i = 0; i < RECENTS; i++) recents.add(new TileAdapter.Tile(TileAdapter.CONTINUE, null, "", null, 0, "r" + i));
        }
        int recentsVisible = recents.isEmpty() ? View.GONE : View.VISIBLE;
        home.findViewById(R.id.continue_title).setVisibility(recentsVisible);
        row(home, R.id.continue_row, recents).setVisibility(recentsVisible);

        List<TileAdapter.Tile> favourites = new ArrayList<>();
        for (int i = 0; i < FAVOURITES; i++) favourites.add(new TileAdapter.Tile(TileAdapter.TILE, null, "", null, 0, "f" + i));
        favourites.add(new TileAdapter.Tile(TileAdapter.ACTION, null, c.getString(R.string.all_apps), null, R.drawable.ic_apps, "all"));
        row(home, R.id.fav_row, favourites);

        Ui.scaleText(home, p.decimal(Prefs.TEXT_SCALE));
        return home;
    }

    private static View row(View home, int id, List<TileAdapter.Tile> tiles) {
        FocusMemoryRecyclerView row = home.findViewById(id);
        row.horizontal();
        TileAdapter adapter = new TileAdapter(NONE, false);
        row.setAdapter(adapter);
        adapter.submit(tiles);
        return row;
    }
}
