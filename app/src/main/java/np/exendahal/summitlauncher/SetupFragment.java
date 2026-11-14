package np.exendahal.summitlauncher;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.transition.Transition;
import androidx.transition.TransitionManager;

import com.google.android.material.R.attr;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.transition.MaterialFadeThrough;
import com.google.android.material.transition.MaterialSharedAxis;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * First-run setup (PRD 6.1, extended): TV source, favourite apps, wallpaper, look, language and scores, as
 * Settings-style lists beside a live Home preview. Each choice is saved the moment it is made, so language and
 * theme apply immediately and re-running setup is pre-filled.
 */
public class SetupFragment extends Screen {
    /** Steps in order; the scores step only appears when scores are enabled. */
    private static final String[] STEP_IDS = BuildConfig.SCORES_ENABLED
            ? new String[]{"source", "favourites", "wallpaper", "look", "language", "scores"}
            : new String[]{"source", "favourites", "wallpaper", "look", "language"};
    private static final int STEPS = STEP_IDS.length;
    /** Current step (1-based); static so it survives the recreate caused by language/theme changes. */
    static int step = 1;

    private static final String[][] SCORE_IDS = {{"npl"}, {"pl"}, {"laliga"}, {"ucl"}, {"ipl"}, {"nba"}, {"f1"}, {"atp", "wta"}};
    private static final String[] SCORE_NAMES = {"NPL", "Premier League", "LaLiga", "Champions League", "IPL", "NBA", null, null};

    private View root;
    private LinearLayout list;
    private TextView title, subtitle;
    private MaterialButton back, next;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup parent, @Nullable Bundle b) {
        root = inf.inflate(R.layout.fragment_setup, parent, false);
        list = root.findViewById(R.id.list);
        title = root.findViewById(R.id.title);
        subtitle = root.findViewById(R.id.subtitle);
        back = root.findViewById(R.id.back);
        next = root.findViewById(R.id.next);
        back.setOnClickListener(v -> go(-1));
        next.setOnClickListener(v -> go(1));
        Ui.scaleText(root, prefs().decimal(Prefs.TEXT_SCALE));
        render();
        root.post(this::focusSelected);
        return root;
    }

    @Override
    public boolean onBack() {
        if (step > 1) go(-1);
        return true; // step 1: Back does nothing (PRD 7.2)
    }

    private void go(int dir) {
        if (step + dir < 1) return;
        if (step + dir > STEPS) { finish(); return; }
        step += dir;
        String motion = Ui.motion(requireContext());
        if (!"off".equals(motion)) {
            Transition t = "full".equals(motion) ? new MaterialSharedAxis(MaterialSharedAxis.X, dir > 0) : new MaterialFadeThrough();
            TransitionManager.beginDelayedTransition(root.findViewById(R.id.body), t);
        }
        render();
        ((ScrollView) root.findViewById(R.id.scroll)).scrollTo(0, 0);
        root.post(this::focusSelected);
    }

    private void finish() {
        Prefs p = prefs();
        p.set(Prefs.SETUP_COMPLETED, "true");
        step = 1;
        if (AppsRepo.usageGranted(requireContext()) || p.bool(Prefs.USAGE_ASKED)) {
            host().show(HomeActivity.HOME, true);
            return;
        }
        p.set(Prefs.USAGE_ASKED, "true"); // one-time card (PRD 6.1)
        HomeActivity a = host();
        new MaterialAlertDialogBuilder(a).setTitle(R.string.usage_title).setMessage(R.string.usage_body)
                .setPositiveButton(R.string.allow, (d, w) -> {
                    if (!TvSource.open(a, Settings.ACTION_USAGE_ACCESS_SETTINGS)) {
                        Toast.makeText(a, R.string.usage_unavailable, Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton(R.string.skip, null)
                .setOnDismissListener(d -> a.show(HomeActivity.HOME, true))
                .show();
    }

    // ---- Rendering ----

    private void render() {
        ((TextView) root.findViewById(R.id.step_label)).setText(Ui.digits(prefs(), getString(R.string.step_of, step, STEPS)));
        renderDots();
        back.setVisibility(step == 1 ? View.GONE : View.VISIBLE);
        next.setText(step == STEPS ? R.string.finish : R.string.next);
        list.removeAllViews();
        switch (STEP_IDS[step - 1]) {
            case "source": stepSource(); break;
            case "favourites": stepFavourites(); break;
            case "wallpaper": stepWallpaper(); break;
            case "look": stepLook(); break;
            case "language": stepLanguage(); break;
            default: stepScores(); break;
        }
        Ui.scaleText(list, prefs().decimal(Prefs.TEXT_SCALE));
        FrameLayout preview = root.findViewById(R.id.preview);
        preview.post(() -> Preview.home(preview));
    }

    /** Re-renders the step after a choice, keeping focus on the same row. */
    private void rerender() {
        int index = -1;
        for (int i = 0; i < list.getChildCount(); i++) if (list.getChildAt(i).hasFocus()) index = i;
        render();
        int focus = index;
        root.post(() -> { if (focus >= 0 && focus < list.getChildCount()) list.getChildAt(focus).requestFocus(); });
    }

    /** Focus starts on the chosen row, else the first row (PRD 6.1). */
    private void focusSelected() {
        View first = null;
        for (int i = 0; i < list.getChildCount(); i++) {
            View v = list.getChildAt(i);
            if (!v.isFocusable()) continue;
            if (v.isSelected()) { v.requestFocus(); return; }
            if (first == null) first = v;
        }
        if (first != null) first.requestFocus();
    }

    private void renderDots() {
        Context c = requireContext();
        LinearLayout dots = root.findViewById(R.id.dots);
        dots.removeAllViews();
        int size = Ui.px(c, R.dimen.tile_gap) / 2;
        for (int i = 1; i <= STEPS; i++) {
            View dot = new View(c);
            GradientDrawable d = new GradientDrawable();
            d.setCornerRadius(size);
            d.setColor(Ui.color(c, i <= step ? attr.colorPrimary : attr.colorOutline));
            dot.setBackground(d);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(i == step ? size * 8 / 3 : size, size);
            lp.setMarginStart(size / 2);
            dots.addView(dot, lp); // current dot is a wider pill
        }
    }

    /** A single-choice row; the chosen one is where focus starts. */
    private void radio(String text, String sub, boolean on, Runnable onClick) {
        addRow(PrefRow.create(requireContext(), text, sub, null, false, on ? PrefRow.Mark.RADIO_ON : PrefRow.Mark.RADIO_OFF, null, onClick), on);
    }

    private void addRow(View row, boolean selected) {
        row.setSelected(selected);
        row.setNextFocusRightId(R.id.next); // Right always reaches Continue; Back is one more press left
        list.addView(row);
    }

    private void header(int text) { list.addView(PrefRow.header(requireContext(), getString(text))); }

    // ---- TV source ----

    /** First asks how TV is watched (input or app), then lists only the choices for that answer. */
    private void stepSource() {
        title.setText(R.string.s2_title);
        subtitle.setText(R.string.s2_sub);
        Context c = requireContext();
        Prefs p = prefs();
        boolean inputMode = p.is(Prefs.LIVE_TYPE, "input");
        radio(getString(R.string.mode_input), getString(R.string.mode_input_sub), inputMode, () -> setMode("input"));
        radio(getString(R.string.mode_app), getString(R.string.mode_app_sub), !inputMode, () -> setMode("app"));
        header(inputMode ? R.string.mode_input : R.string.mode_app);
        if (inputMode) inputRows(c, p); else appRows(c, p);
    }

    private void setMode(String mode) {
        Prefs p = prefs();
        p.set(Prefs.LIVE_TYPE, mode);
        if ("app".equals(mode) && AppsRepo.find(requireContext(), p.str(Prefs.LIVE_APP)) == null) {
            List<AppsRepo.App> apps = TvSource.liveApps(requireContext());
            if (!apps.isEmpty()) p.set(Prefs.LIVE_APP, apps.get(0).pkg); // pre-select the first live-TV app
        }
        rerender();
    }

    private void inputRows(Context c, Prefs p) {
        TvInputManager m = (TvInputManager) c.getSystemService(Context.TV_INPUT_SERVICE);
        List<TvInputInfo> inputs = new ArrayList<>();
        for (TvInputInfo i : TvSource.inputs(c)) if (i.getParentId() == null) inputs.add(i);

        // Pre-select the first HDMI input reporting a signal; else HDMI 1, marked "Recommended" (PRD 6.1).
        TvInputInfo recommended = null;
        for (TvInputInfo i : inputs) {
            if (i.getType() == TvInputInfo.TYPE_HDMI && connected(m, i)) { recommended = i; break; }
        }
        boolean signalKnown = recommended != null;
        if (recommended == null) {
            for (TvInputInfo i : inputs) if (i.getType() == TvInputInfo.TYPE_HDMI) { recommended = i; break; }
        }
        if (p.str(Prefs.LIVE_INPUT).isEmpty() && recommended != null) p.set(Prefs.LIVE_INPUT, recommended.getId());
        TvInputInfo current = TvSource.currentInput(c);

        for (TvInputInfo i : inputs) {
            String name = i.getType() == TvInputInfo.TYPE_TUNER ? getString(R.string.antenna) : TvSource.label(c, i);
            String sub = getString(connected(m, i) ? R.string.signal_yes : R.string.signal_no);
            if (!signalKnown && i == recommended) sub = getString(R.string.recommended);
            radio(name, sub, current != null && current.getId().equals(i.getId()), () -> {
                p.set(Prefs.LIVE_INPUT, i.getId());
                rerender();
            });
        }
    }

    private void appRows(Context c, Prefs p) {
        for (AppsRepo.App a : TvSource.liveApps(c)) {
            radio(a.label, null, a.pkg.equals(p.str(Prefs.LIVE_APP)), () -> {
                p.set(Prefs.LIVE_APP, a.pkg);
                rerender();
            });
        }
    }

    private static boolean connected(TvInputManager m, TvInputInfo i) {
        try {
            return m != null && m.getInputState(i.getId()) == TvInputManager.INPUT_STATE_CONNECTED;
        } catch (RuntimeException e) {
            return false;
        }
    }

    // ---- Favourite apps ----

    private void stepFavourites() {
        title.setText(R.string.s_fav_title);
        subtitle.setText(R.string.s_fav_sub);
        Context c = requireContext();
        PackageManager pm = c.getPackageManager();
        Set<String> favourites = new HashSet<>();
        for (AppsRepo.App a : AppsRepo.favourites(c)) favourites.add(a.pkg);
        for (AppsRepo.App a : AppsRepo.all(c)) {
            boolean fav = favourites.contains(a.pkg);
            addRow(PrefRow.create(c, a.label, null, a.icon(pm), false, fav ? PrefRow.Mark.CHECK_ON : PrefRow.Mark.CHECK_OFF,
                    null, () -> {
                        AppsRepo.setFavourite(c, a.pkg, !fav);
                        rerender();
                    }), false);
        }
    }

    // ---- Wallpaper ----

    private void stepWallpaper() {
        title.setText(R.string.s_wall_title);
        subtitle.setText(R.string.s_wall_sub);
        String[] names = getResources().getStringArray(R.array.e_wallpaper);
        for (int i = 0; i < ThemeController.WALLPAPERS.length; i++) {
            String id = ThemeController.WALLPAPERS[i];
            radio(names[i], null, prefs().is(Prefs.WALLPAPER, id), () -> {
                prefs().set(Prefs.WALLPAPER, id);
                rerender();
                host().bindWallpaper();
                host().onSettingChanged(Prefs.WALLPAPER);
            });
        }
    }

    // ---- Look: mode and colour ----

    private void stepLook() {
        title.setText(R.string.s4_title);
        subtitle.setText(R.string.s4_sub);
        Context c = requireContext();
        Prefs p = prefs();
        header(R.string.opt_mode);
        String[] modes = {"auto", "dark", "light"};
        String[] modeNames = getResources().getStringArray(R.array.e_theme_mode);
        for (int i = 0; i < modes.length; i++) {
            String v = modes[i];
            radio(modeNames[i], i == 0 ? getString(R.string.recommended) : null, p.is(Prefs.THEME_MODE, v),
                    () -> setLook(Prefs.THEME_MODE, v, null));
        }

        header(R.string.opt_colour);
        radio(getResources().getStringArray(R.array.e_theme_colour)[0], null, p.is(Prefs.THEME_COLOUR, "wallpaper"),
                () -> setLook(Prefs.THEME_COLOUR, "wallpaper", null));
        String[] colourNames = getResources().getStringArray(R.array.e_custom_colour);
        for (int i = 0; i < ThemeController.CUSTOM.length; i++) {
            String id = ThemeController.CUSTOM[i];
            boolean on = p.is(Prefs.THEME_COLOUR, "custom") && p.is(Prefs.THEME_CUSTOM, id);
            GradientDrawable swatch = new GradientDrawable();
            swatch.setShape(GradientDrawable.OVAL);
            swatch.setColor(c.getColor(ThemeController.CUSTOM_SWATCH[i]));
            addRow(PrefRow.create(c, colourNames[i], null, swatch, false, on ? PrefRow.Mark.RADIO_ON : PrefRow.Mark.RADIO_OFF,
                    null, () -> setLook(Prefs.THEME_COLOUR, "custom", id)), on);
        }
    }

    /** Saves a look choice ({@code custom} is the colour id for Colour › Custom) and applies it shortly after. */
    private void setLook(String key, String value, String custom) {
        prefs().set(key, value);
        if (custom != null) prefs().set(Prefs.THEME_CUSTOM, custom);
        rerender();
        host().onSettingChanged(key); // the theme rebuilds the screen; step and focus are restored
    }

    // ---- Language ----

    private void stepLanguage() {
        title.setText(R.string.s1_title);
        subtitle.setText(R.string.s1_sub);
        String current = prefs().str(Prefs.LANGUAGE);
        String[] names = getResources().getStringArray(R.array.languages);
        for (int i = 0; i < SettingsFragment.LANG_CODES.length; i++) {
            String code = SettingsFragment.LANG_CODES[i];
            String[] parts = names[i].split("\\s*/\\s*");
            radio(parts[0], parts.length > 1 ? parts[1] : null, code.equals(current), () -> {
                prefs().set(Prefs.LANGUAGE, code);
                rerender();
                host().onSettingChanged(Prefs.LANGUAGE); // applies immediately (activity recreates on locale change)
            });
        }
    }

    // ---- Scores ----

    private void stepScores() {
        title.setText(R.string.s3_title);
        subtitle.setText(R.string.s3_sub);
        Context c = requireContext();
        Set<String> leagues = prefs().leagues();
        for (int i = 0; i < SCORE_IDS.length; i++) {
            String[] ids = SCORE_IDS[i];
            String name = SCORE_NAMES[i] != null ? SCORE_NAMES[i] : getString(i == 6 ? R.string.formula1 : R.string.tennis);
            boolean on = leagues.contains(ids[0]);
            addRow(PrefRow.create(c, name, null, null, false, on ? PrefRow.Mark.CHECK_ON : PrefRow.Mark.CHECK_OFF, null, () -> {
                Set<String> s = prefs().leagues();
                for (String id : ids) { if (on) s.remove(id); else s.add(id); }
                prefs().setLeagues(s);
                rerender();
            }), false);
        }
    }
}
