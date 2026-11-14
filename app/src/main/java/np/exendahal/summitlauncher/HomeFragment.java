package np.exendahal.summitlauncher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.os.Bundle;
import android.provider.Settings;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.R.attr;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/** Home (PRD 6.2): header, clock and dates, weather, scores, Continue and Favourites rows. */
public class HomeFragment extends Screen implements TileAdapter.Listener {
    private View root;
    private TextView clock, bsDate, adDate, healthLine, banner;
    private LinearLayout scoresRow;
    private FocusMemoryRecyclerView continueRow, favRow;
    private TileAdapter continueAdapter, favAdapter;
    private List<String> moveBackup;
    private boolean focusedOnce;
    /** Latest scores; null until the first background load finishes. */
    private Scores.Result scores;
    private Weather weather;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup parent, @Nullable Bundle b) {
        root = inf.inflate(R.layout.fragment_home, parent, false);
        clock = root.findViewById(R.id.clock);
        bsDate = root.findViewById(R.id.bs_date);
        adDate = root.findViewById(R.id.ad_date);
        healthLine = root.findViewById(R.id.health_line);
        banner = root.findViewById(R.id.default_banner);
        scoresRow = root.findViewById(R.id.scores_row);
        continueRow = root.findViewById(R.id.continue_row);
        favRow = root.findViewById(R.id.fav_row);

        bindHeader(root, R.id.nav_home, false);
        root.findViewById(R.id.weather_chip).setOnClickListener(v -> {
            if (weather != null) Dialogs.weather(host(), weather, this::loadWeather);
        });
        banner.setOnClickListener(v -> showDefaultHomeHelp());
        Ui.focusable(banner, false);

        continueRow.horizontal();
        favRow.horizontal();
        continueAdapter = new TileAdapter(this, false);
        favAdapter = new TileAdapter(this, false);
        continueRow.setAdapter(continueAdapter);
        favRow.setAdapter(favAdapter);

        Ui.scaleText(root, prefs().decimal(Prefs.TEXT_SCALE));
        return root;
    }

    @Override
    public void onResume() {
        super.onResume();
        onTick();
        bindRows();
        bindBanner();
        loadWeather();
        loadScores();
        if (!focusedOnce) {
            focusedOnce = true;
            root.post(() -> { if (!focusNav()) focusDefault(); }); // Watch TV first (PRD 7.3)
        }
    }

    @Override
    public void onTick() {
        if (root == null) return;
        Prefs p = prefs();
        LocalDate today = LocalDate.now();
        clock.setText(Ui.clock(p, LocalTime.now()));
        Ui.text(bsDate, Ui.bsDate(requireContext(), p, today));
        Ui.text(adDate, Ui.adDate(requireContext(), p, today));
        Ui.text(healthLine, p.is(Prefs.HEALTH_LINE, "home_ambient") ? Ui.healthTip(requireContext(), today) : null);
        bindScores();
        loadScores(); // refreshes every 60 s while a match is live (PRD 8.4)
    }

    @Override
    public void onPackagesChanged() { bindRows(); }

    // ---- Rows ----

    private void bindRows() {
        Context c = requireContext();
        Prefs p = prefs();
        List<TileAdapter.Tile> cont = new ArrayList<>();
        if (p.is(Prefs.WATCH_TILE, "first")) {
            cont.add(new TileAdapter.Tile(TileAdapter.WATCH, null, getString(R.string.watch_tv), TvSource.caption(c), 0, "watch"));
        }
        if (p.bool(Prefs.CONTINUE_ROW)) {
            if (AppsRepo.usageGranted(c)) {
                for (AppsRepo.Recent r : AppsRepo.recents(c, getResources().getInteger(R.integer.max_recents))) {
                    cont.add(new TileAdapter.Tile(TileAdapter.CONTINUE, r.app, r.app.label,
                            AppsRepo.relativeTime(c, r.lastUsed), 0, r.app.pkg));
                }
            } else {
                cont.add(new TileAdapter.Tile(TileAdapter.CONTINUE, null, getString(R.string.show_recents), null,
                        R.drawable.ic_history, "usage"));
            }
        }
        continueAdapter.submit(cont);
        int vis = cont.isEmpty() ? View.GONE : View.VISIBLE;
        continueRow.setVisibility(vis);
        root.findViewById(R.id.continue_title).setVisibility(vis);

        List<TileAdapter.Tile> fav = new ArrayList<>();
        for (AppsRepo.App a : AppsRepo.favourites(c)) fav.add(new TileAdapter.Tile(TileAdapter.TILE, a, a.label, null, 0, a.pkg));
        fav.add(new TileAdapter.Tile(TileAdapter.ACTION, null, getString(R.string.all_apps), null, R.drawable.ic_apps, "all"));
        favAdapter.submit(fav);
    }

    /** Home key on Home: scroll rows to start and focus Watch TV (PRD 6.2). */
    public void goHome() {
        if (favAdapter.moving() >= 0) cancelMove();
        ((ScrollView) root).scrollTo(0, 0);
        continueRow.resetToStart();
        favRow.resetToStart();
        root.post(this::focusDefault);
    }

    private void focusDefault() {
        if (continueAdapter.getItemCount() > 0) continueRow.focusPosition(0);
        else favRow.focusPosition(0);
    }

    @Override
    public void onClick(TileAdapter.Tile t, int position) {
        if (favAdapter.moving() >= 0) { dropMove(); return; }
        if ("watch".equals(t.id)) TvSource.watch(requireActivity());
        else if ("all".equals(t.id)) host().show(HomeActivity.APPS, true);
        else if ("usage".equals(t.id)) {
            if (!TvSource.open(requireActivity(), Settings.ACTION_USAGE_ACCESS_SETTINGS)) {
                Toast.makeText(requireContext(), R.string.usage_unavailable, Toast.LENGTH_LONG).show();
            }
        } else if (t.app != null) AppsRepo.launch(requireActivity(), t.app);
    }

    @Override
    public void onLongClick(TileAdapter.Tile t, int position) {
        if (t.app == null) return;
        boolean inFav = favAdapter.items().contains(t);
        Dialogs.appMenu(requireActivity(), t.app, inFav, this::bindRows, () -> startMove(position));
    }

    // ---- Move mode (PRD 6.5): left/right reorder, OK drops, Back cancels ----

    private void startMove(int position) {
        moveBackup = currentFavouriteOrder();
        favAdapter.setMoving(position);
        favRow.focusPosition(position);
        Toast.makeText(requireContext(), R.string.move_hint, Toast.LENGTH_SHORT).show();
    }

    private List<String> currentFavouriteOrder() {
        List<String> out = new ArrayList<>();
        for (TileAdapter.Tile t : favAdapter.items()) if (t.app != null) out.add(t.app.pkg);
        return out;
    }

    private void dropMove() {
        favAdapter.setMoving(-1);
        prefs().setFavourites(currentFavouriteOrder());
        prefs().set(Prefs.FAV_ORDER, "manual"); // a manual move implies manual order
    }

    private void cancelMove() {
        prefs().setFavourites(moveBackup);
        favAdapter.setMoving(-1);
        bindRows();
    }

    @Override
    public boolean onKey(KeyEvent e) {
        int moving = favAdapter.moving();
        if (moving < 0) {
            if (e.getAction() == KeyEvent.ACTION_DOWN && e.getKeyCode() == KeyEvent.KEYCODE_MENU) {
                View f = requireActivity().getCurrentFocus();
                RecyclerViewTile hit = tileFor(f);
                if (hit != null) { onLongClick(hit.tile, hit.position); return true; }
            }
            return false;
        }
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;
        int last = favAdapter.getItemCount() - 2; // the All apps tile stays last
        switch (e.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (moving > 0) { favAdapter.swap(moving, moving - 1); favRow.focusPosition(moving - 1); }
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (moving < last) { favAdapter.swap(moving, moving + 1); favRow.focusPosition(moving + 1); }
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                dropMove();
                return true;
            case KeyEvent.KEYCODE_BACK:
                return false; // handled by onBack
            default:
                return true; // stay in move mode
        }
    }

    @Override
    public boolean onBack() {
        if (favAdapter.moving() >= 0) { cancelMove(); return true; }
        return true; // Home consumes Back
    }

    private static final class RecyclerViewTile {
        final TileAdapter.Tile tile; final int position;
        RecyclerViewTile(TileAdapter.Tile t, int p) { tile = t; position = p; }
    }

    private RecyclerViewTile tileFor(View v) {
        for (FocusMemoryRecyclerView row : new FocusMemoryRecyclerView[]{continueRow, favRow}) {
            if (v != null && v.getParent() == row) {
                int p = row.getChildAdapterPosition(v);
                if (p >= 0) return new RecyclerViewTile(((TileAdapter) row.getAdapter()).get(p), p);
            }
        }
        return null;
    }

    // ---- Weather and scores ----

    private void loadWeather() {
        Context app = requireContext().getApplicationContext();
        Weather.IO.execute(() -> {
            Weather w = Weather.load(app);
            root.post(() -> { if (isAdded()) { weather = w; bindWeather(); } });
        });
    }

    private void bindWeather() {
        Prefs p = prefs();
        boolean show = p.bool(Prefs.WEATHER_SHOW);
        root.findViewById(R.id.weather_chip).setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) return;
        ImageView icon = root.findViewById(R.id.weather_icon);
        TextView temp = root.findViewById(R.id.weather_temp), text = root.findViewById(R.id.weather_text),
                hl = root.findViewById(R.id.weather_hl);
        Weather w = weather;
        temp.setVisibility(w == null ? View.GONE : View.VISIBLE);
        hl.setVisibility(w == null ? View.GONE : View.VISIBLE);
        if (w == null) {
            icon.setImageResource(R.drawable.ic_cloud);
            text.setText(R.string.weather_unavailable);
            return;
        }
        icon.setImageResource(Weather.icon(w.code, w.isDay));
        temp.setText(Weather.temp(p, w.temp));
        text.setText(Weather.label(w.code, w.isDay));
        hl.setText(getString(R.string.hi_lo, Weather.temp(p, w.hi), Weather.temp(p, w.lo)));
    }

    /** Loads scores off the main thread; Scores caches, so calling this every minute is cheap. */
    private void loadScores() {
        if (!BuildConfig.SCORES_ENABLED) return;
        Context app = requireContext().getApplicationContext();
        Weather.IO.execute(() -> {
            Scores.Result r = Scores.load(app);
            root.post(() -> { if (isAdded()) { scores = r; bindScores(); } });
        });
    }

    private void bindScores() {
        if (!BuildConfig.SCORES_ENABLED) { scoresRow.setVisibility(View.GONE); return; }
        Prefs p = prefs();
        scoresRow.removeAllViews();
        if (p.leagues().isEmpty()) { scoresRow.setVisibility(View.GONE); return; }
        Scores.Result r = scores;
        if (r == null) { scoresRow.setVisibility(View.GONE); return; }
        boolean stale = Scores.isStale(r.updatedAt, getResources().getInteger(R.integer.scores_stale_min));
        if (stale && p.is(Prefs.SCORE_STALE, "hide")) { scoresRow.setVisibility(View.GONE); return; }
        scoresRow.setVisibility(View.VISIBLE);
        float scale = p.decimal(Prefs.TEXT_SCALE);
        if (stale) {
            scoresRow.addView(chip(null, getString(R.string.scores_unavailable), r.updatedAt));
        } else {
            List<Scores.Match> matches = Scores.forHome(r.matches, p.leagues(), p.bool(Prefs.SCORE_LIVE_FIRST),
                    p.integer(Prefs.SCORE_MAX), System.currentTimeMillis());
            // Nothing live, about to start or just finished: no scores row at all.
            if (matches.isEmpty()) { scoresRow.setVisibility(View.GONE); return; }
            for (Scores.Match m : matches) scoresRow.addView(chip(m, null, r.updatedAt));
        }
        TextView caption = new TextView(requireContext(), null, 0, R.style.Himal_Caption);
        long min = (System.currentTimeMillis() - r.updatedAt) / 60_000;
        caption.setSingleLine(true);
        caption.setText(Ui.digits(p, min < 1 ? getString(R.string.updated_now)
                : getResources().getQuantityString(R.plurals.updated_ago, (int) min, (int) min)));
        caption.setPadding(Ui.px(requireContext(), R.dimen.tile_gap) / 2, 0, 0, 0);
        caption.setEllipsize(android.text.TextUtils.TruncateAt.END);
        scoresRow.addView(caption, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Ui.scaleText(scoresRow, scale);
    }

    /** One glass score chip: [LIVE] league score status (PRD 6.2). */
    private View chip(Scores.Match m, String message, long updatedAt) {
        Context c = requireContext();
        Prefs p = prefs();
        LinearLayout chip = new LinearLayout(c);
        chip.setGravity(android.view.Gravity.CENTER_VERTICAL);
        chip.setBackgroundResource(R.drawable.bg_chip);
        int pad = Ui.px(c, R.dimen.tile_gap) * 2 / 3;
        chip.setPadding(pad, pad / 2, pad, pad / 2);
        chip.setFocusable(true);
        chip.setClickable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(pad);
        chip.setLayoutParams(lp);

        if (m != null && m.live()) {
            TextView live = new TextView(c, null, 0, R.style.Himal_Body);
            live.setText(R.string.live);
            live.setTextColor(Ui.color(c, attr.colorOnPrimary));
            live.setTypeface(Typeface.DEFAULT_BOLD);
            live.setBackgroundResource(R.drawable.bg_live);
            live.setPadding(pad / 2, 0, pad / 2, 0);
            chip.addView(live);
        }
        TextView t = new TextView(c, null, 0, R.style.Himal_Body);
        SpannableStringBuilder sb = new SpannableStringBuilder();
        int muted = Ui.color(c, R.attr.hMuted);
        if (m == null) {
            sb.append(message);
        } else {
            append(sb, m.leagueName + "  ", new ForegroundColorSpan(muted));
            append(sb, Ui.digits(p, Scores.chipText(m)), new StyleSpan(Typeface.BOLD));
            String status = Scores.statusText(m, getString(R.string.ft), Ui.time(p, m.startTime));
            if (!status.isEmpty()) append(sb, "  " + Ui.digits(p, status), new ForegroundColorSpan(muted));
            chip.setOnClickListener(v -> Dialogs.score(requireActivity(), m, updatedAt));
        }
        t.setText(sb);
        t.setTextColor(Ui.color(c, attr.colorOnSurface));
        t.setPadding(m != null && m.live() ? pad / 2 : 0, 0, 0, 0);
        chip.addView(t);
        chip.setContentDescription(getString(R.string.score_cd, sb.toString()));
        Ui.focusable(chip, false);
        return chip;
    }

    private static void append(SpannableStringBuilder sb, String s, Object span) {
        int start = sb.length();
        sb.append(s);
        sb.setSpan(span, start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    // ---- Default home banner (PRD 2) ----

    private void bindBanner() {
        Context c = requireContext();
        ResolveInfo ri = c.getPackageManager().resolveActivity(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
        boolean isDefault = ri != null && ri.activityInfo != null && c.getPackageName().equals(ri.activityInfo.packageName);
        banner.setVisibility(isDefault || prefs().bool(Prefs.BANNER_DISMISSED) ? View.GONE : View.VISIBLE);
    }

    private void showDefaultHomeHelp() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.not_default_title)
                .setMessage(R.string.not_default_help)
                .setPositiveButton(R.string.open_settings, (d, w) -> {
                    if (!TvSource.open(requireActivity(), Settings.ACTION_HOME_SETTINGS)) {
                        TvSource.open(requireActivity(), Settings.ACTION_SETTINGS);
                    }
                })
                .setNegativeButton(R.string.dismiss, (d, w) -> {
                    prefs().set(Prefs.BANNER_DISMISSED, "true");
                    bindBanner();
                    focusDefault();
                })
                .show();
    }
}
