package io.github.raghavsatyadev.tvfreedom.home;

import android.animation.ValueAnimator;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewOutlineProvider;

/**
 * Material 3 Expressive focus for a tile: it grows on the spatial curve (a small overshoot, then settles), its corners
 * morph from round to rounder, and a ring in the focused app's primary color fades in. Losing focus runs the effects
 * curve, which never overshoots.
 */
final class FocusMotion {

    static final float SCALE = 1.10f;
    static final float RADIUS_DP = 16f;
    static final float FOCUSED_RADIUS_DP = 26f;
    private static final float RING_DP = 3f;

    /** The focused app's accent; {@link Tint} moves it as the backdrop fades between apps. */
    private static int ringColor = Palette.of(Palette.DEFAULT_SEED).primary;
    private static FocusMotion focused;

    private final View card;
    private final float density;
    private final GradientDrawable ring = new GradientDrawable();
    private float radius;
    private ValueAnimator anim;

    FocusMotion(View card) {
        this.card = card;
        density = card.getResources().getDisplayMetrics().density;
        radius = RADIUS_DP * density;
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
            }
        });
        card.setClipToOutline(true);
        ring.setColor(0);
        ring.setAlpha(0);
        card.setForeground(ring);
        apply(0f);
    }

    /** Animate to the focused or the resting look. */
    void set(boolean on) {
        if (on) focused = this;
        else if (focused == this) focused = null;
        float from = (radius / density - RADIUS_DP) / (FOCUSED_RADIUS_DP - RADIUS_DP);
        if (anim != null) anim.cancel();
        anim = ValueAnimator.ofFloat(from, on ? 1f : 0f);
        anim.setDuration(on ? Motion.FAST_SPATIAL_MS : Motion.DEFAULT_EFFECTS_MS);
        anim.setInterpolator(on ? Motion.FAST_SPATIAL : Motion.DEFAULT_EFFECTS);
        anim.addUpdateListener(a -> apply((float) a.getAnimatedValue()));
        anim.start();
    }

    /** Jump straight to a look, with no animation (a rebind of the focused tile). */
    void snap(boolean on) {
        if (anim != null) anim.cancel();
        if (on) focused = this;
        else if (focused == this) focused = null;
        apply(on ? 1f : 0f);
    }

    /** t is 0 at rest and 1 focused; the spatial curve takes it a little past 1 before it settles. */
    private void apply(float t) {
        float scale = 1f + (SCALE - 1f) * t;
        card.setScaleX(scale);
        card.setScaleY(scale);
        float r = (RADIUS_DP + (FOCUSED_RADIUS_DP - RADIUS_DP) * Math.max(0f, t)) * density;
        if (r != radius) {
            radius = r;
            card.invalidateOutline();
        }
        ring.setCornerRadius(radius);
        ring.setStroke(Math.round(RING_DP * density), ringColor);
        ring.setAlpha(Math.round(255 * Math.max(0f, Math.min(1f, t))));
    }

    /** Called by {@link Tint} on each frame of a color change. */
    static void setRingColor(int color) {
        ringColor = color;
        if (focused != null) focused.ring.setStroke(Math.round(RING_DP * focused.density), color);
    }
}
