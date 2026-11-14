package np.exendahal.summitlauncher;

import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import java.time.LocalTime;

/** A top-level launcher screen hosted by {@link HomeActivity}. */
public abstract class Screen extends Fragment {
    /** Set when switching screens from the nav bar, so the new screen focuses its own pill. */
    private static boolean navFocus;
    private View navSelected;
    private TextView headerClock;

    /** Handles a key before default dispatch. @return true if consumed. */
    public boolean onKey(KeyEvent e) { return false; }

    /** Handles Back. @return true if consumed; otherwise the activity goes up one level. */
    public boolean onBack() { return false; }

    /** Called every minute on the minute and after time changes. */
    public void onTick() {}

    /** Called after installed packages change. */
    public void onPackagesChanged() {}

    /**
     * Wires the shared header ({@code header_home.xml}) with {@code selectedId} as the current nav pill.
     * Compact (Apps, Settings) shows a clock in place of the weather chip and search.
     */
    protected void bindHeader(View root, int selectedId, boolean compact) {
        bindNav(root, selectedId);
        root.findViewById(R.id.weather_chip).setVisibility(compact ? View.GONE : View.VISIBLE);
        root.findViewById(R.id.search).setVisibility(compact ? View.GONE : View.VISIBLE);
        headerClock = root.findViewById(R.id.header_clock);
        headerClock.setVisibility(compact ? View.VISIBLE : View.GONE);
        root.findViewById(R.id.gear).setOnClickListener(v -> TvSource.open(requireActivity(), Settings.ACTION_SETTINGS));
        root.findViewById(R.id.search).setOnClickListener(v -> TvSource.voiceSearch(requireActivity()));
        for (int id : new int[]{R.id.weather_chip, R.id.search, R.id.gear}) Ui.focusable(root.findViewById(id), false);
    }

    protected void bindHeaderClock() {
        if (headerClock != null) headerClock.setText(Ui.clock(prefs(), LocalTime.now()));
    }

    private void bindNav(View root, int selectedId) {
        int[] ids = {R.id.nav_home, R.id.nav_apps, R.id.nav_settings};
        String[] screens = {HomeActivity.HOME, HomeActivity.APPS, HomeActivity.SETTINGS};
        for (int i = 0; i < ids.length; i++) {
            View pill = root.findViewById(ids[i]);
            String screen = screens[i];
            pill.setSelected(ids[i] == selectedId);
            if (ids[i] == selectedId) {
                navSelected = pill;
            } else {
                pill.setOnClickListener(v -> {
                    if (HomeActivity.SETTINGS.equals(screen)) SettingsFragment.resetNavigation();
                    navFocus = true;
                    host().show(screen, true);
                });
            }
            Ui.focusable(pill, true);
        }
    }

    /** Focuses this screen's nav pill if it was opened from the nav bar. @return true if it did. */
    protected boolean focusNav() {
        if (!navFocus || navSelected == null) return false;
        navFocus = false;
        navSelected.requestFocus();
        return true;
    }

    protected HomeActivity host() { return (HomeActivity) requireActivity(); }

    protected Prefs prefs() { return Prefs.get(requireContext()); }
}
