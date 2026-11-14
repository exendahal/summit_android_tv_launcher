package np.exendahal.summitlauncher;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Apps grid (PRD 6.5): every TV app, alphabetical, 6 columns, with the context menu.
 * The PRD's Watch/Listen/Tools filters were removed: apps self-declare categories inconsistently
 * (VLC calls itself audio), so the filters mislabelled more than they helped.
 */
public class AppsFragment extends Screen implements TileAdapter.Listener {
    private View root;
    private FocusMemoryRecyclerView grid;
    private TileAdapter adapter;
    private TextView selected;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup parent, @Nullable Bundle b) {
        root = inf.inflate(R.layout.fragment_apps, parent, false);
        grid = root.findViewById(R.id.grid);
        selected = root.findViewById(R.id.selected);
        grid.setLayoutManager(new GridLayoutManager(requireContext(), getResources().getInteger(R.integer.grid_columns)));
        adapter = new TileAdapter(this, true);
        grid.setAdapter(adapter);

        bindHeader(root, R.id.nav_apps, true);
        Ui.scaleText(root, prefs().decimal(Prefs.TEXT_SCALE));
        bind();
        grid.post(() -> { if (!focusNav()) grid.focusPosition(0); });
        onTick();
        return root;
    }

    private void bind() {
        List<TileAdapter.Tile> tiles = new ArrayList<>();
        for (AppsRepo.App a : AppsRepo.all(requireContext())) {
            tiles.add(new TileAdapter.Tile(TileAdapter.TILE, a, a.label, null, 0, a.pkg));
        }
        adapter.submit(tiles);
    }

    @Override
    public void onTick() { bindHeaderClock(); }

    @Override
    public void onPackagesChanged() { bind(); }

    /** Menu key on a tile opens the same menu as holding OK (PRD 7.1). */
    @Override
    public boolean onKey(KeyEvent e) {
        if (e.getKeyCode() != KeyEvent.KEYCODE_MENU) return false;
        View f = grid.getFocusedChild();
        int p = f == null ? RecyclerView.NO_POSITION : grid.getChildAdapterPosition(f);
        if (p == RecyclerView.NO_POSITION) return false;
        if (e.getAction() == KeyEvent.ACTION_DOWN) onLongClick(adapter.get(p), p);
        return true;
    }

    @Override
    public void onClick(TileAdapter.Tile t, int position) { AppsRepo.launch(requireActivity(), t.app); }

    @Override
    public void onLongClick(TileAdapter.Tile t, int position) {
        Dialogs.appMenu(requireActivity(), t.app, false, this::bind, null);
    }

    @Override
    public void onFocus(TileAdapter.Tile t) { selected.setText(getString(R.string.selected_app, t.title)); }
}
