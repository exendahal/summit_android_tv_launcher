package np.exendahal.summitlauncher;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDialog;
import androidx.appcompat.content.res.AppCompatResources;

import com.google.android.material.R.attr;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Score, weather, app context menu, option picker and reset confirmation dialogs. */
public final class Dialogs {
    private Dialogs() {}

    /** Right-aligned full-height panel dialog (PRD 6.3, 6.4). */
    private static Dialog panel(Context c, View content, int widthDimen) {
        AppCompatDialog d = new AppCompatDialog(c);
        d.supportRequestWindowFeature(Window.FEATURE_NO_TITLE);
        d.setContentView(content);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawableResource(android.R.color.transparent);
            w.setGravity(Gravity.END);
            w.setLayout(Ui.px(c, widthDimen), ViewGroup.LayoutParams.MATCH_PARENT);
            w.setDimAmount(0.62f);
        }
        return d;
    }

    /** Score detail (PRD 6.3). Back or Close returns focus to the chip (dialog dismissal restores it). */
    public static void score(Activity a, Scores.Match m, long updatedAt) {
        Prefs p = Prefs.get(a);
        View v = LayoutInflater.from(a).inflate(R.layout.dialog_score, null);
        ((TextView) v.findViewById(R.id.league)).setText(m.live() ? a.getString(R.string.score_live_suffix, m.leagueName) : m.leagueName);
        ((TextView) v.findViewById(R.id.home_name)).setText(m.homeName);
        ((TextView) v.findViewById(R.id.home_score)).setText(Ui.digits(p, m.homeScore));
        ((TextView) v.findViewById(R.id.away_name)).setText(m.awayName);
        ((TextView) v.findViewById(R.id.away_score)).setText(Ui.digits(p, m.awayScore));
        String status = m.scheduled() ? a.getString(R.string.kick_off, Ui.time(p, m.startTime))
                : m.summary.isEmpty() ? m.clock : m.summary;
        ((TextView) v.findViewById(R.id.status)).setText(Ui.digits(p, status));
        long min = Math.max(0, (System.currentTimeMillis() - updatedAt) / 60_000);
        String updated = a.getResources().getQuantityString(R.plurals.updated_ago, (int) min, (int) min);
        ((TextView) v.findViewById(R.id.venue)).setText(Ui.digits(p, m.venue.isEmpty() ? updated
                : a.getString(R.string.venue_updated, m.venue, updated)));
        Ui.scaleText(v, p.decimal(Prefs.TEXT_SCALE));
        Dialog d = panel(a, v, R.dimen.dialog_score_w);
        Button close = v.findViewById(R.id.close);
        close.setOnClickListener(x -> d.dismiss());
        d.setOnShowListener(x -> close.requestFocus());
        d.show();
    }

    /** Weather detail (PRD 6.4). */
    /**
     * @param onCityChanged called after the user picks another city in the dialog
     */
    public static void weather(HomeActivity a, Weather w, Runnable onCityChanged) {
        Prefs p = Prefs.get(a);
        Locale l = Ui.locale(p);
        View v = LayoutInflater.from(a).inflate(R.layout.dialog_weather, null);
        ((TextView) v.findViewById(R.id.title)).setText(a.getString(R.string.weather_title, Weather.placeName(a)));
        ((ImageView) v.findViewById(R.id.icon)).setImageResource(Weather.icon(w.code, w.isDay));
        ((TextView) v.findViewById(R.id.temp)).setText(Weather.temp(p, w.temp));
        ((TextView) v.findViewById(R.id.cond)).setText(Weather.label(w.code, w.isDay));
        ((TextView) v.findViewById(R.id.hl)).setText(a.getString(R.string.hi_lo_long, Weather.temp(p, w.hi), Weather.temp(p, w.lo)));
        ((TextView) v.findViewById(R.id.wind)).setText(a.getString(R.string.wind_rain, Weather.wind(a, p, w.wind), Weather.rain(a, p, w.rainToday)));
        ((TextView) v.findViewById(R.id.summary)).setText(w.summary(a, l));

        LinearLayout parts = v.findViewById(R.id.parts);
        int[] names = {R.string.part_morning, R.string.part_afternoon, R.string.part_evening, R.string.part_night};
        for (int i = 0; i < 4; i++) {
            Weather.Part part = w.parts[i];
            boolean day = i < 3;
            String note = part.rainProb >= 50 ? a.getString(R.string.rain_pct, part.rainProb) : a.getString(Weather.label(part.code, day));
            parts.addView(card(a, a.getString(names[i]), Weather.icon(part.code, day), Weather.temp(p, part.temp), Ui.digits(p, note)));
        }
        LinearLayout days = v.findViewById(R.id.days);
        for (Weather.Day d : w.next) {
            TextView row = new TextView(a, null, 0, R.style.Himal_Body);
            row.setText(Ui.digits(p, Weather.dayName(d.date, l) + "   " + a.getString(Weather.label(d.code, true)) + " · "
                    + a.getString(R.string.rain_pct, d.rainProb) + "   " + Weather.temp(p, d.hi) + " / " + Weather.temp(p, d.lo)));
            row.setCompoundDrawablesRelativeWithIntrinsicBounds(Weather.icon(d.code, true), 0, 0, 0);
            row.setCompoundDrawablePadding(Ui.px(a, R.dimen.tile_gap) / 2);
            row.setCompoundDrawableTintList(ColorStateList.valueOf(Ui.color(a, attr.colorPrimary)));
            row.setPadding(0, 4, 0, 4);
            days.addView(row);
        }
        TextView mountain = v.findViewById(R.id.mountain);
        Ui.text(mountain, Double.isNaN(w.namche) ? null : a.getString(R.string.mountains_line,
                Weather.temp(p, w.namche), a.getString(Weather.label(w.namcheCode, true)).toLowerCase(l)));
        Ui.scaleText(v, p.decimal(Prefs.TEXT_SCALE));

        Dialog d = panel(a, v, R.dimen.dialog_weather_w);
        Button close = v.findViewById(R.id.close);
        close.setOnClickListener(x -> d.dismiss());
        v.findViewById(R.id.city).setOnClickListener(x -> cityPicker(a, () -> { d.dismiss(); onCityChanged.run(); }));
        v.findViewById(R.id.units).setOnClickListener(x -> {
            d.dismiss();
            SettingsFragment.openAt(SettingsFragment.CAT_WEATHER);
            a.show(HomeActivity.SETTINGS, true);
        });
        d.setOnShowListener(x -> close.requestFocus());
        d.show();
    }

    private static View card(Context c, String label, int icon, String temp, String note) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setBackgroundResource(R.drawable.bg_card);
        int pad = Ui.px(c, R.dimen.tile_gap) / 2;
        box.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        lp.setMarginEnd(pad);
        box.setLayoutParams(lp);
        TextView t1 = new TextView(c, null, 0, R.style.Himal_Caption);
        t1.setText(label);
        ImageView iv = new ImageView(c);
        iv.setImageResource(icon);
        iv.setImageTintList(ColorStateList.valueOf(Ui.color(c, attr.colorPrimary)));
        TextView t2 = new TextView(c, null, 0, R.style.Himal_Title);
        t2.setText(temp);
        TextView t3 = new TextView(c, null, 0, R.style.Himal_Caption);
        t3.setText(note);
        box.addView(t1); box.addView(iv); box.addView(t2); box.addView(t3);
        return box;
    }

    /**
     * App context menu (PRD 6.5): pin/unpin, move (Home favourites only), app info, uninstall (not for system apps).
     *
     * @param changed called after pin state changes
     * @param move    starts move mode, or null when moving is not offered
     */
    public static void appMenu(Activity a, AppsRepo.App app, boolean canMove, Runnable changed, Runnable move) {
        boolean fav = AppsRepo.isFavourite(a, app.pkg);
        int pad = Ui.px(a, R.dimen.tile_gap);
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundResource(R.drawable.bg_dialog);
        box.setPadding(pad * 3 / 2, pad * 2, pad * 3 / 2, pad);

        LinearLayout head = new LinearLayout(a);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(pad / 2, 0, 0, pad * 3 / 2);
        ImageView icon = new ImageView(a);
        icon.setImageDrawable(app.icon(a.getPackageManager()));
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        head.addView(icon, new LinearLayout.LayoutParams(pad * 3, pad * 3));
        TextView name = new TextView(a, null, 0, R.style.Himal_Title);
        name.setText(app.label);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setPadding(pad, 0, 0, 0);
        head.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(head);

        Dialog d = panel(a, box, R.dimen.dialog_app_w);
        View first = menuRow(a, box, d, fav ? R.drawable.ic_star : R.drawable.ic_star_outline, fav ? R.string.unpin : R.string.pin,
                () -> { AppsRepo.setFavourite(a, app.pkg, !fav); changed.run(); });
        if (canMove && move != null) menuRow(a, box, d, R.drawable.ic_open_with, R.string.move, move);
        menuRow(a, box, d, R.drawable.ic_info, R.string.app_info,
                () -> start(a, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + app.pkg))));
        if (!app.system) {
            menuRow(a, box, d, R.drawable.ic_delete, R.string.uninstall,
                    () -> start(a, new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + app.pkg))));
        }
        Ui.scaleText(box, Prefs.get(a).decimal(Prefs.TEXT_SCALE));
        d.setOnShowListener(x -> first.requestFocus());
        d.show();
    }

    /** Adds an app menu action styled like a Settings row; it closes the menu, then runs. @return the row. */
    private static View menuRow(Context c, LinearLayout box, Dialog d, int icon, int label, Runnable action) {
        int pad = Ui.px(c, R.dimen.tile_gap);
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.bg_pref_row);
        row.setFocusable(true);
        row.setClickable(true);
        row.setMinimumHeight(Ui.px(c, R.dimen.pref_row_min_h));
        row.setPadding(pad, 0, pad, 0);
        ImageView img = new ImageView(c);
        img.setImageResource(icon);
        img.setImageTintList(AppCompatResources.getColorStateList(c, R.color.pref_icon));
        img.setDuplicateParentStateEnabled(true);
        row.addView(img, new LinearLayout.LayoutParams(pad * 3 / 2, pad * 3 / 2));
        TextView text = new TextView(c, null, 0, R.style.Himal_BodyLarge);
        text.setText(label);
        text.setTextColor(AppCompatResources.getColorStateList(c, R.color.pref_title));
        text.setDuplicateParentStateEnabled(true);
        text.setPadding(pad, 0, 0, 0);
        row.addView(text);
        row.setOnClickListener(v -> { d.dismiss(); action.run(); });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = pad / 4;
        box.addView(row, lp);
        return row;
    }

    /** Explains the one-time adb grant needed to switch the system screensaver. */
    public static void screensaverSetup(Context c) {
        new MaterialAlertDialogBuilder(c).setTitle(R.string.screensaver_setup_title).setMessage(R.string.screensaver_setup_body)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    /** Reset confirmation with Cancel focused (PRD 6.6). */
    public static void resetConfirm(Context c, Runnable onReset) {
        AlertDialog d = new MaterialAlertDialogBuilder(c).setTitle(R.string.reset_title).setMessage(R.string.reset_body)
                .setPositiveButton(R.string.cancel, null)
                .setNegativeButton(R.string.reset_confirm, (x, w) -> onReset.run())
                .create();
        d.setOnShowListener(x -> d.getButton(AlertDialog.BUTTON_POSITIVE).requestFocus());
        d.show();
    }

    /**
     * Weather location picker: "Use current location" plus search by city name (Open-Meteo geocoding).
     * Results appear as you type; pressing Search on the keyboard searches immediately.
     *
     * @param onChanged called after a place is chosen
     */
    public static void cityPicker(Activity a, Runnable onChanged) {
        Prefs p = Prefs.get(a);
        int pad = Ui.px(a, R.dimen.tile_gap);
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad * 2, pad, pad * 2, 0);

        MaterialButton current = new MaterialButton(a);
        String detected = Weather.detectedCity(a);
        current.setText(detected == null ? a.getString(R.string.use_current) : a.getString(R.string.use_current_named, detected));
        current.setId(View.generateViewId());
        // Full-width, so show focus with a ring; scaling would push its rounded ends past the dialog padding.
        current.setStrokeColor(ColorStateList.valueOf(Ui.color(a, attr.colorOnSurface)));
        current.setOnFocusChangeListener((x, has) -> current.setStrokeWidth(has ? Ui.px(a, R.dimen.focus_ring) : 0));
        box.addView(current);

        LinearLayout results = new LinearLayout(a);
        EditText search = new EditText(a);
        // Text fields consume Up/Down on some TV IMEs, so move focus explicitly.
        search.setOnKeyListener((x, code, e) -> {
            if (e.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (code == KeyEvent.KEYCODE_DPAD_UP) return current.requestFocus();
            if (code == KeyEvent.KEYCODE_DPAD_DOWN && results.getChildCount() > 0) return results.getChildAt(0).requestFocus();
            return false;
        });
        search.setHint(R.string.search_city);
        search.setSingleLine(true);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        box.addView(search);
        TextView status = new TextView(a, null, 0, R.style.Himal_Caption);
        box.addView(status);
        results.setOrientation(LinearLayout.VERTICAL);
        ScrollView sv = new ScrollView(a);
        sv.setClipToPadding(false);
        sv.setPadding(0, pad / 2, 0, pad / 2);
        sv.addView(results);
        box.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.px(a, R.dimen.watch_w)));

        AlertDialog d = new MaterialAlertDialogBuilder(a).setTitle(R.string.opt_location).setView(box)
                .setNegativeButton(R.string.cancel, null).create();
        current.setOnClickListener(x -> { Weather.chooseCurrent(a); d.dismiss(); onChanged.run(); });

        Handler h = new Handler(Looper.getMainLooper());
        int[] generation = {0};
        boolean[] focusResults = {false}; // set when the user presses Search on the keyboard
        Runnable run = () -> {
            String q = search.getText().toString();
            int gen = ++generation[0];
            results.removeAllViews();
            if (q.trim().length() < 2) { status.setText(""); return; }
            status.setText(R.string.searching);
            String lang = p.str(Prefs.LANGUAGE);
            Weather.IO.execute(() -> {
                List<Weather.Place> found;
                boolean failed = false;
                try {
                    found = Weather.search(q, lang);
                } catch (IOException e) {
                    found = new ArrayList<>();
                    failed = true;
                }
                List<Weather.Place> f = found;
                boolean fail = failed;
                h.post(() -> {
                    if (gen != generation[0] || !d.isShowing()) return; // a newer search replaced this one
                    status.setText(fail ? a.getString(R.string.search_failed) : f.isEmpty() ? a.getString(R.string.no_results) : "");
                    for (Weather.Place place : f) {
                        TextView row = new TextView(a, null, 0, R.style.Himal_BodyLarge);
                        row.setText(place.region.isEmpty() ? place.name : place.name + " · " + place.region);
                        row.setBackgroundResource(R.drawable.bg_row);
                        row.setGravity(Gravity.CENTER_VERTICAL);
                        row.setPadding(pad, 0, pad, 0);
                        row.setMinHeight(Ui.px(a, R.dimen.header_h));
                        row.setFocusable(true);
                        row.setClickable(true);
                        row.setOnClickListener(x -> { Weather.choose(a, place); d.dismiss(); onChanged.run(); });
                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                        lp.topMargin = pad / 2;
                        results.addView(row, lp);
                    }
                    if (focusResults[0] && results.getChildCount() > 0) results.getChildAt(0).requestFocus();
                    focusResults[0] = false;
                });
            });
        };
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int af) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { h.removeCallbacks(run); h.postDelayed(run, 500); }
        });
        search.setOnEditorActionListener((x, id, e) -> {
            // Search key: close the keyboard and move focus to the first result when it arrives.
            ((InputMethodManager) a.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(search.getWindowToken(), 0);
            focusResults[0] = true;
            h.removeCallbacks(run);
            run.run();
            return true;
        });
        d.setOnDismissListener(x -> h.removeCallbacksAndMessages(null));
        // Keep the keyboard closed until the user presses OK on the search field.
        if (d.getWindow() != null) d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        d.setOnShowListener(x -> current.requestFocus()); // most common choice; Down reaches the search box
        d.show();
    }

    /** Starts an intent, ignoring devices that lack the target screen. */
    private static void start(Activity a, Intent i) {
        try { a.startActivity(i); } catch (RuntimeException ignored) { }
    }
}
