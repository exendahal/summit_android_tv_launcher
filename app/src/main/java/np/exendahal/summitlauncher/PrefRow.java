package np.exendahal.summitlauncher;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.content.res.AppCompatResources;

/** One Android TV settings row, shared by Settings and first-run setup: flat when idle, inverse highlight when focused. */
final class PrefRow {
    private PrefRow() {}

    enum Mark { NONE, CHEVRON, RADIO_ON, RADIO_OFF, CHECK_ON, CHECK_OFF }

    /**
     * @param icon     leading picture after any radio or checkbox (a symbol, app icon or colour swatch), or null
     * @param tintIcon true for symbols, which follow the row's text colour; false for app icons and swatches
     */
    static View create(Context c, String title, String summary, Drawable icon, boolean tintIcon, Mark mark,
                       String tag, Runnable onClick) {
        int pad = Ui.px(c, R.dimen.tile_gap);
        int padH = Ui.px(c, R.dimen.pref_row_pad_h);
        int iconSize = Ui.px(c, R.dimen.pref_icon);
        ColorStateList iconTint = AppCompatResources.getColorStateList(c, R.color.pref_icon);

        LinearLayout row = new LinearLayout(c);
        row.setTag(tag);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.bg_pref_row);
        row.setFocusable(true);
        row.setClickable(true);
        row.setMinimumHeight(Ui.px(c, R.dimen.pref_row_min_h));
        row.setPadding(padH, pad / 2, padH, pad / 2);
        row.setOnClickListener(v -> onClick.run());

        int check = mark == Mark.RADIO_ON ? R.drawable.ic_radio_button_checked
                : mark == Mark.RADIO_OFF ? R.drawable.ic_radio_button_unchecked
                : mark == Mark.CHECK_ON ? R.drawable.ic_check_box
                : mark == Mark.CHECK_OFF ? R.drawable.ic_check_box_outline : 0;
        if (check != 0) {
            boolean on = mark == Mark.RADIO_ON || mark == Mark.CHECK_ON;
            row.addView(lead(c, AppCompatResources.getDrawable(c, check),
                    on ? AppCompatResources.getColorStateList(c, R.color.pref_radio_on) : iconTint, iconSize, padH));
        }
        if (icon != null) row.addView(lead(c, icon, tintIcon ? iconTint : null, iconSize, padH));

        LinearLayout texts = new LinearLayout(c);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setDuplicateParentStateEnabled(true);
        TextView t = new TextView(c, null, 0, R.style.Himal_BodyLarge);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setText(title);
        t.setTextColor(AppCompatResources.getColorStateList(c, R.color.pref_title));
        t.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START); // right-to-left names (Arabic, Urdu) stay in line
        t.setDuplicateParentStateEnabled(true);
        texts.addView(t);
        if (summary != null) {
            TextView sub = new TextView(c, null, 0, R.style.Himal_Body);
            sub.setText(summary);
            sub.setTextColor(AppCompatResources.getColorStateList(c, R.color.pref_summary));
            sub.setSingleLine(true);
            sub.setEllipsize(TextUtils.TruncateAt.END);
            sub.setDuplicateParentStateEnabled(true);
            texts.addView(sub);
        }
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        if (mark == Mark.CHEVRON) {
            ImageView chevron = new ImageView(c);
            chevron.setImageResource(R.drawable.ic_chevron);
            chevron.setImageTintList(iconTint);
            chevron.setDuplicateParentStateEnabled(true);
            row.addView(chevron, new LinearLayout.LayoutParams(iconSize, iconSize));
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = pad / 6;
        row.setLayoutParams(lp);
        return row;
    }

    /** A small caption above a group of rows. */
    static TextView header(Context c, CharSequence text) {
        TextView h = new TextView(c, null, 0, R.style.Himal_Caption);
        h.setText(text);
        int padH = Ui.px(c, R.dimen.pref_row_pad_h);
        h.setPadding(padH, Ui.px(c, R.dimen.tile_gap), padH, 0);
        return h;
    }

    private static ImageView lead(Context c, Drawable d, ColorStateList tint, int size, int gap) {
        ImageView v = new ImageView(c);
        v.setImageDrawable(d);
        v.setImageTintList(tint);
        v.setDuplicateParentStateEnabled(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.setMarginEnd(gap);
        v.setLayoutParams(lp);
        return v;
    }
}
