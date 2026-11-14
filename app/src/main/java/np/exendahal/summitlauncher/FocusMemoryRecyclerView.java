package np.exendahal.summitlauncher;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

/**
 * RecyclerView with row focus memory and keyline scrolling (PRD 7.3):
 * entering the row from outside focuses the item last focused in it, and horizontal rows keep the
 * focused item near a fixed keyline once the row has scrolled.
 */
public class FocusMemoryRecyclerView extends RecyclerView {
    private int lastPosition = 0;

    public FocusMemoryRecyclerView(@NonNull Context c, @Nullable AttributeSet a) {
        super(c, a);
        setItemAnimator(null); // avoid focus jumps while items change
    }

    /** Uses a horizontal keyline layout manager. */
    public void horizontal() {
        int keyline = Ui.px(getContext(), R.dimen.row_keyline);
        setLayoutManager(new LinearLayoutManager(getContext(), HORIZONTAL, false) {
            @Override
            public boolean requestChildRectangleOnScreen(@NonNull RecyclerView parent, @NonNull View child,
                                                         @NonNull Rect rect, boolean immediate, boolean focusedChildVisible) {
                int pos = getPosition(child);
                int width = parent.getWidth() - parent.getPaddingRight();
                boolean atStart = parent.computeHorizontalScrollOffset() == 0;
                int dx;
                if (pos == 0) dx = child.getLeft() - parent.getPaddingLeft();
                else if (atStart && child.getRight() <= width) dx = 0; // first items are not offset
                else dx = child.getLeft() - keyline;
                if (dx == 0) return false;
                if (immediate) parent.scrollBy(dx, 0); else parent.smoothScrollBy(dx, 0);
                return true;
            }
        });
    }

    @Override
    public void requestChildFocus(View child, View focused) {
        super.requestChildFocus(child, focused);
        int p = getChildAdapterPosition(child);
        if (p != NO_POSITION) lastPosition = p;
    }

    @Override
    public void addFocusables(ArrayList<View> views, int direction, int mode) {
        if (!hasFocus()) {
            ViewHolder vh = findViewHolderForAdapterPosition(lastPosition);
            if (vh != null && vh.itemView.isFocusable()) {
                views.add(vh.itemView);
                return;
            }
        }
        super.addFocusables(views, direction, mode);
    }

    /** @return position of the remembered item. */
    public int lastPosition() { return lastPosition; }

    /** Forgets the remembered item and scrolls back to the start. */
    public void resetToStart() {
        lastPosition = 0;
        scrollToPosition(0);
    }

    /** Focuses the item at {@code position}, scrolling to it first if needed. */
    public void focusPosition(int position) {
        lastPosition = position;
        ViewHolder vh = findViewHolderForAdapterPosition(position);
        if (vh != null) { vh.itemView.requestFocus(); return; }
        scrollToPosition(position);
        post(() -> {
            ViewHolder again = findViewHolderForAdapterPosition(position);
            if (again != null) again.itemView.requestFocus();
        });
    }
}
