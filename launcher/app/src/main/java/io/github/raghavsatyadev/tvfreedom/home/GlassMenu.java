package io.github.raghavsatyadev.tvfreedom.home;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.media.tv.TvInputManager;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

/** The glass dropdown used by the Inputs pill and the tile menu: rows with an icon, a title and an optional status. */
final class GlassMenu {

    private final Activity act;
    private final View panel;
    private final LinearLayout rows;
    private final PopupWindow popup;
    private final int minWidth;
    private final float density;

    GlassMenu(Activity act, ViewGroup root, int minWidth) {
        this.act = act;
        this.minWidth = minWidth;
        density = act.getResources().getDisplayMetrics().density;
        panel = act.getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        rows = panel.findViewById(R.id.rows);
        popup = new PopupWindow(panel, minWidth, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));   // needed for Back to dismiss
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);
        popup.setElevation(0f);
    }

    boolean isEmpty() {
        return rows.getChildCount() == 0;
    }

    void addSeparator() {
        View line = new View(act);
        line.setBackgroundColor(0x1FFFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) density);
        int m = (int) (6 * density), h = (int) (12 * density);
        lp.setMargins(h, m, h, m);
        rows.addView(line, lp);
    }

    /** A row with an app's own icon (not tinted). */
    void addAppRow(Drawable icon, String title, Runnable action) {
        ImageView view = addRow(0, title, -1, action).findViewById(R.id.icon);
        view.setImageTintList(null);
        // A copy: the tile's ImageView shows the same drawable and would lose its bounds.
        view.setImageDrawable(icon.getConstantState() != null ? icon.getConstantState().newDrawable() : icon);
    }

    /** A row; state is a TvInputManager.INPUT_STATE_* value, or -1 for no status. */
    View addRow(int iconRes, String title, int state, Runnable action) {
        View row = act.getLayoutInflater().inflate(R.layout.item_input, rows, false);
        ((ImageView) row.findViewById(R.id.icon)).setImageResource(iconRes);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        TextView status = row.findViewById(R.id.status);
        View dot = row.findViewById(R.id.dot);
        // This TV reports "standby" for every port whether or not something is plugged in, so only
        // definite states are shown.
        if (state == TvInputManager.INPUT_STATE_CONNECTED) {
            status.setText(R.string.connected);
            dot.setVisibility(View.VISIBLE);
        } else if (state == TvInputManager.INPUT_STATE_DISCONNECTED) {
            status.setText(R.string.not_connected);
            dot.setVisibility(View.GONE);
        } else {
            status.setVisibility(View.GONE);
            dot.setVisibility(View.GONE);
        }
        row.setOnClickListener(v -> {
            popup.dismiss();
            action.run();
        });
        rows.addView(row);
        return row;
    }

    /** Drop in below anchor (above it when there is no room), focused on the first row. */
    void showBelow(View anchor) {
        // Fit the widest row: TVs that report a status on every port (Sony) would otherwise squeeze the names out.
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        popup.setWidth(Math.max(minWidth, panel.getMeasuredWidth()));

        // Drop in: fade and unfold from the top edge.
        panel.setAlpha(0f);
        panel.setPivotY(0f);
        panel.setScaleY(0.92f);
        panel.setTranslationY(-8 * density);
        popup.showAsDropDown(anchor, 0, (int) (10 * density), Gravity.START);
        panel.animate().alpha(1f).scaleY(1f).translationY(0f).setDuration(220)
                .setInterpolator(new DecelerateInterpolator(1.6f)).start();
        rows.getChildAt(0).requestFocus();
    }

    /** Rise above anchor (the dock sits at the bottom of the screen); a long list scrolls in the room there. */
    void showAbove(View anchor, ViewGroup root) {
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int width = Math.max(minWidth, panel.getMeasuredWidth());
        int[] at = new int[2];
        anchor.getLocationInWindow(at);
        int gap = (int) (10 * density), top = (int) (48 * density);
        int height = Math.min(panel.getMeasuredHeight(), at[1] - gap - top);
        popup.setWidth(width);
        popup.setHeight(height);
        int x = Math.min(at[0], root.getWidth() - width - top);

        panel.setAlpha(0f);
        panel.setPivotY(height);
        panel.setScaleY(0.92f);
        panel.setTranslationY(8 * density);
        popup.showAtLocation(root, Gravity.TOP | Gravity.START, x, at[1] - gap - height);
        panel.animate().alpha(1f).scaleY(1f).translationY(0f).setDuration(220)
                .setInterpolator(new DecelerateInterpolator(1.6f)).start();
        rows.getChildAt(0).requestFocus();
    }
}
