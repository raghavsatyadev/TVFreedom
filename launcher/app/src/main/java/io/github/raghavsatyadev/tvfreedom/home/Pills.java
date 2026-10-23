package io.github.raghavsatyadev.tvfreedom.home;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Material 3 Expressive buttons for the top bar: tonal pills in the backdrop's colors. On focus a pill fills with the
 * focused app's primary color, grows a little on the spatial curve and its corners tighten from round to rounded
 * square, the way M3E buttons morph their shape.
 */
final class Pills {

    private static final float SCALE = 1.06f;
    private static final float FOCUSED_RADIUS_DP = 14f;
    private static final ArgbEvaluator ARGB = new ArgbEvaluator();

    private final List<Pill> pills = new ArrayList<>();
    private Palette palette = Palette.of(Palette.DEFAULT_SEED);

    Pills(TextView... views) {
        for (TextView v : views) pills.add(new Pill(v));
    }

    /** The backdrop's colors changed (a new app got focus). */
    void setPalette(Palette p) {
        palette = p;
        for (Pill pill : pills) pill.apply(pill.t);
    }

    private final class Pill {
        final TextView view;
        final GradientDrawable bg = new GradientDrawable();
        final float density;
        float t;
        ValueAnimator anim;

        Pill(TextView view) {
            this.view = view;
            density = view.getResources().getDisplayMetrics().density;
            view.setBackground(bg);
            Flex.use(view, 560);
            view.addOnLayoutChangeListener((v, l, top, r, b, ol, ot, or, ob) -> apply(t));
            view.setOnFocusChangeListener((v, focused) -> animate(focused));
            apply(view.isFocused() ? 1f : 0f);
        }

        void animate(boolean on) {
            if (anim != null) anim.cancel();
            anim = ValueAnimator.ofFloat(t, on ? 1f : 0f);
            anim.setDuration(on ? Motion.FAST_SPATIAL_MS : Motion.DEFAULT_EFFECTS_MS);
            anim.setInterpolator(on ? Motion.FAST_SPATIAL : Motion.DEFAULT_EFFECTS);
            anim.addUpdateListener(a -> apply((float) a.getAnimatedValue()));
            anim.start();
        }

        /** t is 0 at rest and 1 focused; the spatial curve takes it a little past 1 before it settles. */
        void apply(float t) {
            this.t = t;
            float c = Math.max(0f, Math.min(1f, t));
            float scale = 1f + (SCALE - 1f) * t;
            view.setScaleX(scale);
            view.setScaleY(scale);
            float round = view.getHeight() / 2f;
            bg.setCornerRadius(round + (FOCUSED_RADIUS_DP * density - round) * c);
            int rest = (palette.containerHigh & 0x00FFFFFF) | 0xE6000000;
            bg.setColor((int) ARGB.evaluate(c, rest, palette.primary));
            int ink = (int) ARGB.evaluate(c, palette.onSurface, palette.onPrimary);
            view.setTextColor(ink);
            view.setCompoundDrawableTintList(ColorStateList.valueOf(ink));
        }
    }
}
