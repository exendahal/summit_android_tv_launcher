package np.exendahal.summitlauncher;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.imageview.ShapeableImageView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Adapter for every tile row and grid: Watch TV, Continue, Favourites/Apps tiles and action tiles. */
public final class TileAdapter extends RecyclerView.Adapter<TileAdapter.VH> {
    public static final int WATCH = 0, CONTINUE = 1, TILE = 2, ACTION = 3;

    /** One tile. {@code app} is null for Watch TV and action tiles; {@code id} names the action. */
    public static final class Tile {
        public final int type;
        public final AppsRepo.App app;
        public final String title, sub, id;
        public final int icon;

        public Tile(int type, AppsRepo.App app, String title, String sub, int icon, String id) {
            this.type = type; this.app = app; this.title = title; this.sub = sub; this.icon = icon; this.id = id;
        }
    }

    /** Tile callbacks. */
    public interface Listener {
        void onClick(Tile t, int position);
        void onLongClick(Tile t, int position);
        default void onFocus(Tile t) {}
    }

    private final List<Tile> items = new ArrayList<>();
    private final Listener listener;
    private final boolean grid;
    private int moving = -1;

    /** @param grid true for the Apps grid (tiles fill their column). */
    public TileAdapter(Listener listener, boolean grid) {
        this.listener = listener;
        this.grid = grid;
        setHasStableIds(true); // keeps focus on the same tile when the list is refreshed
    }

    @Override
    public long getItemId(int position) {
        Tile t = items.get(position);
        return Objects.hash(t.id, t.app == null ? null : t.app.component);
    }

    public void submit(List<Tile> tiles) {
        items.clear();
        items.addAll(tiles);
        notifyDataSetChanged();
    }

    public List<Tile> items() { return items; }

    public Tile get(int position) { return items.get(position); }

    /** Marks the tile being moved (move mode), or -1. */
    public void setMoving(int position) {
        int old = moving;
        moving = position;
        if (old >= 0) notifyItemChanged(old);
        if (position >= 0) notifyItemChanged(position);
    }

    public int moving() { return moving; }

    /** Swaps two tiles in place (move mode). */
    public void swap(int a, int b) {
        Tile t = items.get(a);
        items.set(a, items.get(b));
        items.set(b, t);
        if (moving == a) moving = b;
        notifyItemMoved(a, b);
    }

    @Override public int getItemViewType(int position) { return items.get(position).type; }

    @Override public int getItemCount() { return items.size(); }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        int layout = type == WATCH ? R.layout.item_watch : type == CONTINUE ? R.layout.item_continue : R.layout.item_tile;
        View v = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        Context c = parent.getContext();
        Prefs p = Prefs.get(c);
        float size = p.is(Prefs.TILE_SIZE, "large") ? 1.15f : p.is(Prefs.TILE_SIZE, "small") ? 0.85f : 1f;
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
        if (grid) {
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = Math.round(Ui.px(c, R.dimen.grid_tile_h) * size);
            lp.setMargins(lp.getMarginEnd() / 2, lp.getMarginEnd() / 2, lp.getMarginEnd() / 2, lp.getMarginEnd() / 2);
            lp.setMarginEnd(lp.leftMargin);
        } else {
            lp.width = Math.round(lp.width * size);
            lp.height = Math.round(lp.height * size);
        }
        MaterialCardView card = (MaterialCardView) v;
        if (p.is(Prefs.TILE_SHAPE, "square")) card.setRadius(Ui.px(c, R.dimen.r_square));
        ShapeableImageView banner = v.findViewById(R.id.banner);
        if (banner != null) banner.setShapeAppearanceModel(banner.getShapeAppearanceModel().withCornerSize(card.getRadius()));
        if (type == WATCH) {
            v.findViewById(R.id.cookie).setBackground(new Ui.CookieDrawable(Ui.color(c, com.google.android.material.R.attr.colorPrimary)));
        }
        Ui.scaleText(v, p.decimal(Prefs.TEXT_SCALE));
        Ui.focusable(v, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        Tile t = items.get(position);
        Context c = h.itemView.getContext();
        PackageManager pm = c.getPackageManager();
        if (h.title != null) h.title.setText(t.title);
        if (h.sub != null) Ui.text(h.sub, t.sub);
        h.itemView.setContentDescription(t.sub == null ? t.title : t.title + ", " + t.sub);

        if (t.type == TILE || t.type == ACTION) {
            Drawable banner = t.app != null ? t.app.banner(pm) : null;
            h.banner.setVisibility(banner != null ? View.VISIBLE : View.GONE);
            h.body.setVisibility(banner != null ? View.GONE : View.VISIBLE);
            if (banner != null) h.banner.setImageDrawable(banner);
            h.name.setText(t.title);
            showName(h, h.itemView.hasFocus(), t.type == ACTION);
            MaterialCardView card = (MaterialCardView) h.itemView;
            card.setCardBackgroundColor(Ui.color(c, t.type == ACTION
                    ? com.google.android.material.R.attr.colorSecondaryContainer
                    : com.google.android.material.R.attr.colorSurfaceContainer));
            card.setAlpha(position == moving ? 0.6f : 1f);
        }
        if (t.type == CONTINUE) {
            // Home › Card opacity: fades the recent cards' fill, never the icon or text.
            int a = Math.round(Prefs.get(c).integer(Prefs.CARD_OPACITY) * 2.55f);
            MaterialCardView card = (MaterialCardView) h.itemView;
            card.setCardBackgroundColor(card.getCardBackgroundColor().withAlpha(a));
        }
        if (h.icon != null && t.type == CONTINUE) {
            // Same cookie badge as the Watch TV tile: app icons are cut to the cookie shape,
            // action tiles get a primary-coloured cookie with the symbol on top.
            if (t.app != null) {
                h.icon.setBackground(null);
                h.icon.setPadding(0, 0, 0, 0);
                h.icon.setImageTintList(null);
                h.icon.setImageDrawable(new Ui.CookieDrawable(t.app.icon(pm)));
            } else {
                int pad = h.icon.getLayoutParams().width / 4;
                h.icon.setBackground(new Ui.CookieDrawable(Ui.color(c, com.google.android.material.R.attr.colorPrimary)));
                h.icon.setPadding(pad, pad, pad, pad);
                h.icon.setImageResource(t.icon);
                h.icon.setImageTintList(android.content.res.ColorStateList.valueOf(
                        Ui.color(c, com.google.android.material.R.attr.colorOnPrimary)));
            }
        } else if (h.icon != null) {
            if (t.app != null) {
                h.icon.setImageDrawable(t.app.icon(pm));
                h.icon.setImageTintList(null);
            } else {
                h.icon.setImageResource(t.icon);
                h.icon.setImageTintList(android.content.res.ColorStateList.valueOf(
                        Ui.color(c, com.google.android.material.R.attr.colorOnSurface)));
            }
        }
        h.itemView.setOnClickListener(v -> {
            int p = h.getBindingAdapterPosition();
            if (p != RecyclerView.NO_POSITION) listener.onClick(items.get(p), p);
        });
        h.itemView.setOnLongClickListener(v -> {
            int p = h.getBindingAdapterPosition();
            if (p != RecyclerView.NO_POSITION) listener.onLongClick(items.get(p), p);
            return true;
        });
        View.OnFocusChangeListener anim = h.focusAnim;
        h.itemView.setOnFocusChangeListener((v, has) -> {
            anim.onFocusChange(v, has);
            int p = h.getBindingAdapterPosition();
            if (h.name != null && p != RecyclerView.NO_POSITION) showName(h, has, items.get(p).type == ACTION);
            if (has && p != RecyclerView.NO_POSITION) listener.onFocus(items.get(p));
        });
    }

    /** Applies Icons & tiles › App names ("true", "focus" or "false"); action tiles always keep their label. */
    private static void showName(VH h, boolean focused, boolean action) {
        String mode = Prefs.get(h.itemView.getContext()).str(Prefs.TILE_LABELS);
        boolean show = action || "true".equals(mode) || ("focus".equals(mode) && focused);
        boolean banner = h.banner.getVisibility() == View.VISIBLE;
        h.name.setVisibility(show && banner ? View.VISIBLE : View.GONE);
        h.title.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    static final class VH extends RecyclerView.ViewHolder {
        final TextView title, sub, name;
        final ImageView icon, banner;
        final View body;
        final View.OnFocusChangeListener focusAnim;

        VH(View v) {
            super(v);
            title = v.findViewById(R.id.title);
            sub = v.findViewById(R.id.sub);
            name = v.findViewById(R.id.name);
            icon = v.findViewById(R.id.icon);
            banner = v.findViewById(R.id.banner);
            body = v.findViewById(R.id.body);
            focusAnim = v.getOnFocusChangeListener();
        }
    }
}
