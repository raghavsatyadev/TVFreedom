package io.github.raghavsatyadev.tvfreedom.home;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Material 3 Expressive backdrop (after material-tv-launcher): the screen takes a dark scheme seeded from the focused
 * app's banner, and one scalloped shape in the top corner morphs to a new outline for each app. Seeds are worked out
 * on a background thread and cached per package, so moving focus only cross-fades colors.
 */
final class Tint {

    // Material 3 Expressive shapes (MaterialShapes): Cookie 9, Cookie 12, Clover 4-leaf, Clover 8-leaf, Sunny,
    // Soft burst, Cookie 6, Cookie 7, Cookie 4.
    private static final int[] LOBES = {9, 12, 4, 8, 14, 10, 6, 7, 4};
    private static final float[] DEPTHS = {0.08f, 0.06f, 0.26f, 0.16f, 0.05f, 0.11f, 0.12f, 0.10f, 0.16f};
    private static final float[] SHARP = {1f, 1f, 0.55f, 0.65f, 1f, 2.4f, 1f, 1f, 1f};

    private final View root;
    private final GradientDrawable backdrop;
    private final CookieDrawable cookie;
    private final Map<String, Integer> seeds = new ConcurrentHashMap<>();
    private final Map<Integer, Palette> palettes = new ConcurrentHashMap<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private Palette palette;
    private int idleSeed = Palette.DEFAULT_SEED;
    private String focusedPkg;
    private ValueAnimator colors;
    private ValueAnimator morph;

    Tint(FrameLayout root, float density) {
        this.root = root;
        palette = paletteOf(idleSeed);
        backdrop = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{palette.surfaceBright, palette.surface});
        cookie = new CookieDrawable(palette.primaryContainer, LOBES[0], DEPTHS[0]);
        ImageView shape = new ImageView(root.getContext());
        shape.setImageDrawable(cookie);
        shape.setAlpha(0.55f);
        shape.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        // Its own GPU layer: a color change or morph step redraws only this layer.
        shape.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        int size = (int) (560 * density);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size, Gravity.END | Gravity.TOP);
        lp.setMargins(0, -(int) (150 * density), -(int) (120 * density), 0);
        root.addView(shape, 0, lp);
    }

    /**
     * The colors with no app focused. A brand color from TVFreedom seeds them; black (no brand) keeps the Material
     * baseline. Call after {@link Branding#apply}, which paints the root with the plain brand color.
     */
    void attach(int brandColor) {
        idleSeed = (brandColor & 0x00FFFFFF) == 0 ? Palette.DEFAULT_SEED : brandColor;
        root.setBackground(backdrop);
        if (focusedPkg == null) retint(paletteOf(idleSeed));
    }

    /** Work out every app's seed ahead of time, so the first focus on each one costs nothing. */
    void warm(List<AppAdapter.Entry> apps) {
        for (AppAdapter.Entry e : apps) {
            if (seeds.containsKey(e.pkg)) continue;
            Drawable art = e.banner != null ? e.banner : e.icon;
            worker.execute(() -> seedOf(e.pkg, art));
        }
    }

    /** A tile got focus: morph the shape and fade to that app's colors (once its seed is known). */
    void focus(String pkg, Drawable art) {
        if (pkg == null || pkg.equals(focusedPkg)) return;
        focusedPkg = pkg;
        morphTo(pkg);
        Integer seed = seeds.get(pkg);
        if (seed != null) {
            retint(paletteOf(seed));
            return;
        }
        worker.execute(() -> {
            int s = seedOf(pkg, art);
            Palette p = paletteOf(s);
            main.post(() -> {
                if (pkg.equals(focusedPkg)) retint(p);
            });
        });
    }

    /** Focus left the tiles (top bar, menus): back to the idle colors. */
    void blur() {
        if (focusedPkg == null) return;
        focusedPkg = null;
        retint(paletteOf(idleSeed));
    }

    private int seedOf(String pkg, Drawable art) {
        Integer seed = seeds.get(pkg);
        if (seed == null) {
            try {
                seed = Palette.seedFrom(art);
            } catch (RuntimeException e) {
                seed = Palette.DEFAULT_SEED;
            }
            seeds.put(pkg, seed);
        }
        return seed;
    }

    private Palette paletteOf(int seed) {
        Palette p = palettes.get(seed);
        if (p == null) {
            p = Palette.of(seed);
            palettes.put(seed, p);
        }
        return p;
    }

    private void morphTo(String pkg) {
        int k = Math.abs(pkg.hashCode() % LOBES.length);
        cookie.morphTo(LOBES[k], DEPTHS[k], SHARP[k]);
        if (morph != null) morph.cancel();
        morph = ValueAnimator.ofFloat(0f, 1f).setDuration(Motion.DEFAULT_SPATIAL_MS);
        morph.setInterpolator(Motion.DEFAULT_SPATIAL);
        morph.addUpdateListener(a -> cookie.setProgress((float) a.getAnimatedValue()));
        morph.start();
    }

    /** Cross-fade the backdrop and the shape from the current palette to the next. */
    private void retint(Palette next) {
        if (next.seed == palette.seed) return;
        final Palette from = palette;
        palette = next;
        if (colors != null) colors.cancel();
        ArgbEvaluator argb = new ArgbEvaluator();
        colors = ValueAnimator.ofFloat(0f, 1f).setDuration(Motion.DEFAULT_EFFECTS_MS * 2);
        colors.setInterpolator(Motion.DEFAULT_EFFECTS);
        colors.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            backdrop.setColors(new int[]{
                    (int) argb.evaluate(t, from.surfaceBright, next.surfaceBright),
                    (int) argb.evaluate(t, from.surface, next.surface)});
            cookie.setColor((int) argb.evaluate(t, from.primaryContainer, next.primaryContainer));
            FocusMotion.setRingColor((int) argb.evaluate(t, from.primary, next.primary));
        });
        colors.start();
    }
}
