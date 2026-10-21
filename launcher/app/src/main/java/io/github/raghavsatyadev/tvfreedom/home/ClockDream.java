package io.github.raghavsatyadev.tvfreedom.home;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;
import java.util.Random;

/**
 * Screensaver: the launcher's thin clock and date on black, over a slow ambient glow.
 * Every minute the clock drifts to a new spot and the glow shifts colour, so nothing burns in.
 * Above the clock: the company logo, if TVFreedom sent one. Below the date: weather and the next calendar events ({@link DreamInfo}), refreshed every 10 minutes.
 */
public class ClockDream extends DreamService {

    private static final long MOVE_MS = 60_000L;
    private static final int REFRESH_EVERY_TICKS = 10;
    /** Deep, low-brightness glow colours; the glow is drawn at partial alpha on black. */
    private static final int[] PALETTE = {0xFF2B5CFF, 0xFF00A39A, 0xFF8A3FFC, 0xFFE0661B, 0xFFD6336C};

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private View content;
    private GradientDrawable glow;
    private int colorIndex;
    private int glowColor;
    private TextView weatherView;
    private LinearLayout eventsView;
    private int ticks;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (++ticks % REFRESH_EVERY_TICKS == 0) refreshInfo();
            moveClock();
            shiftGlow();
            handler.postDelayed(this, MOVE_MS);
        }
    };

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);   // any key wakes the TV back to where it was
        setFullscreen(true);
        setScreenBright(false);
        setContentView(R.layout.dream_clock);

        content = findViewById(R.id.dreamContent);
        weatherView = findViewById(R.id.dreamWeather);
        eventsView = findViewById(R.id.dreamEvents);
        Branding.applyDream(this, findViewById(R.id.dreamLogo));
        View ambient = findViewById(R.id.dreamAmbient);
        glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        ambient.setBackground(glow);
        ambient.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                glow.setGradientRadius((r - l) * 0.55f));
        colorIndex = random.nextInt(PALETTE.length);
        setGlow(PALETTE[colorIndex]);
        // Weather and events load after the clock is placed and make it taller: pull it back on screen.
        content.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            View parent = (View) content.getParent();
            if (parent.getHeight() <= 0) return;
            float maxY = Math.max(0, parent.getHeight() - content.getHeight());
            float maxX = Math.max(0, parent.getWidth() - content.getWidth());
            if (content.getY() > maxY) content.setY(maxY * 0.9f);
            if (content.getX() > maxX) content.setX(maxX * 0.9f);
        });
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        content.setAlpha(0f);
        content.post(() -> {
            placeClock();
            content.animate().alpha(1f).setDuration(1200).start();
        });
        refreshInfo();
        handler.postDelayed(tick, MOVE_MS);
    }

    /** Fetch weather and events off the main thread, then show them. */
    private void refreshInfo() {
        new Thread(() -> {
            String weather = DreamInfo.weather(getApplicationContext());
            List<String> events = DreamInfo.events(getApplicationContext());
            handler.post(() -> showInfo(weather, events));
        }, "dream-info").start();
    }

    private void showInfo(String weather, List<String> events) {
        if (weatherView == null) return;
        weatherView.setVisibility(weather == null ? View.GONE : View.VISIBLE);
        weatherView.setText(weather);
        eventsView.removeAllViews();
        for (String e : events) {
            TextView t = new TextView(this);
            t.setText(e);
            t.setTextColor(0x99FFFFFF);
            t.setTextSize(17f);
            t.setTypeface(weatherView.getTypeface());
            t.setPadding(0, 0, 0, (int) (4 * getResources().getDisplayMetrics().density));
            eventsView.addView(t);
        }
    }

    @Override
    public void onDreamingStopped() {
        handler.removeCallbacks(tick);
        super.onDreamingStopped();
    }

    @Override
    public void onDetachedFromWindow() {
        handler.removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    private void moveClock() {
        content.animate().alpha(0f).setDuration(900).withEndAction(() -> {
            placeClock();
            content.animate().alpha(1f).setDuration(1200).start();
        }).start();
    }

    /** A random spot that keeps the whole clock on screen, with the glow centred behind it. */
    private void placeClock() {
        View parent = (View) content.getParent();
        int maxX = Math.max(0, parent.getWidth() - content.getWidth());
        int maxY = Math.max(0, parent.getHeight() - content.getHeight());
        float x = maxX * (0.1f + 0.8f * random.nextFloat());
        float y = maxY * (0.1f + 0.8f * random.nextFloat());
        content.setX(x);
        content.setY(y);
        if (parent.getWidth() > 0 && parent.getHeight() > 0) {
            glow.setGradientCenter((x + content.getWidth() / 2f) / parent.getWidth(),
                    (y + content.getHeight() / 2f) / parent.getHeight());
        }
    }

    private void shiftGlow() {
        int from = glowColor;
        colorIndex = (colorIndex + 1) % PALETTE.length;
        ValueAnimator anim = ValueAnimator.ofObject(new ArgbEvaluator(), from, PALETTE[colorIndex]);
        anim.setDuration(8000);
        anim.addUpdateListener(a -> setGlow((int) a.getAnimatedValue()));
        anim.start();
    }

    private void setGlow(int color) {
        glowColor = color;
        int c = color & 0x00FFFFFF;
        glow.setColors(new int[]{0x40000000 | c, 0x14000000 | c, 0x00000000 | c});
    }
}
