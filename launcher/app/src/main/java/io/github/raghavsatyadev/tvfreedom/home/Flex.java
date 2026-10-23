// Adapted from 0xRampey/material-tv-launcher (Apache-2.0); see launcher/NOTICE.
package io.github.raghavsatyadev.tvfreedom.home;

import android.animation.ValueAnimator;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.widget.TextView;

import java.util.Calendar;
import java.util.Date;

/**
 * Material 3 Expressive type with Google Sans Flex (OFL, assets/fonts): a home clock whose weight follows the time of
 * day and swells as each new minute lands, and a tile name that settles into its weight on focus.
 */
final class Flex {

    private static final String FONT = "fonts/GoogleSansFlex.ttf";
    private static final long TICK_MS = 15_000;
    private static Typeface base;

    private Flex() {
    }

    /**
     * Put Google Sans Flex on a view, at a weight. The font is loaded once: each weight is a variation of it set on the
     * view, and those share the one copy of the font data (a Typeface per weight would load the 4 MB font again).
     */
    static void use(TextView view, int weight) {
        if (base == null) {
            try {
                base = Typeface.createFromAsset(view.getContext().getAssets(), FONT);
            } catch (RuntimeException e) {
                base = Typeface.create("sans-serif-light", Typeface.NORMAL);
            }
        }
        view.setTypeface(base);
        weight(view, weight);
    }

    /** Change the weight of a view already set up with {@link #use}. */
    static void weight(TextView view, int weight) {
        int w = Math.max(100, Math.min(1000, weight));
        view.setFontVariationSettings("'wght' " + w + ", 'opsz' 48, 'ROND' 0");
    }

    /** Clock weight through the day: 250 at night, rising to 430 by mid-morning, easing back after sunset. */
    static int clockWeight() {
        Calendar cal = Calendar.getInstance();
        float h = cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE) / 60f;
        float[][] keys = {{0, 250}, {5, 250}, {9, 430}, {17, 430}, {21, 300}, {24, 250}};
        for (int i = 0; i < keys.length - 1; i++) {
            if (h >= keys[i][0] && h <= keys[i + 1][0]) {
                float t = (h - keys[i][0]) / (keys[i + 1][0] - keys[i][0]);
                return Math.round(keys[i][1] + (keys[i + 1][1] - keys[i][1]) * t);
            }
        }
        return 300;
    }

    /** Expressive type: a focused tile's name lands a little lighter and settles into its full weight. */
    static void settle(TextView name) {
        ValueAnimator a = ValueAnimator.ofInt(420, 560).setDuration(Motion.DEFAULT_SPATIAL_MS);
        a.setInterpolator(Motion.DEFAULT_EFFECTS);
        a.addUpdateListener(an -> weight(name, (int) an.getAnimatedValue()));
        a.start();
    }

    /** The home clock: a plain TextView that this ticks, so each change of minute can be animated. */
    static final class Clock {
        private final TextView view;
        private final float density;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Runnable tick = this::tick;

        Clock(TextView view) {
            this.view = view;
            density = view.getResources().getDisplayMetrics().density;
            use(view, clockWeight());
        }

        void start() {
            handler.removeCallbacks(tick);
            tick();
        }

        void stop() {
            handler.removeCallbacks(tick);
        }

        private void tick() {
            String time = DateFormat.format(DateFormat.is24HourFormat(view.getContext()) ? "HH:mm" : "h:mm",
                    new Date()).toString();
            if (view.getText().length() == 0) {
                view.setText(time);
                weight(view, clockWeight());
            } else if (!time.contentEquals(view.getText())) {
                change(time);
            }
            handler.postDelayed(tick, TICK_MS);
        }

        /** A new minute: the old time lifts away, the new one settles in with a short weight swell. */
        private void change(String time) {
            int base = clockWeight();
            view.animate().alpha(0f).translationY(-6 * density)
                    .setInterpolator(Motion.FAST_EFFECTS).setDuration(Motion.FAST_EFFECTS_MS)
                    .withEndAction(() -> {
                        view.setText(time);
                        view.setTranslationY(8 * density);
                        view.animate().alpha(1f).translationY(0).setInterpolator(Motion.DEFAULT_SPATIAL)
                                .setDuration(Motion.DEFAULT_SPATIAL_MS).start();
                        ValueAnimator swell = ValueAnimator.ofInt(base + 320, base).setDuration(700);
                        swell.setInterpolator(Motion.DEFAULT_SPATIAL);
                        swell.addUpdateListener(a -> weight(view, (int) a.getAnimatedValue()));
                        swell.start();
                    }).start();
        }
    }
}
