// Adapted from 0xRampey/material-tv-launcher (Apache-2.0); see launcher/NOTICE.
package io.github.raghavsatyadev.tvfreedom.home;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Material 3 Expressive Now Playing card, top right under the top bar: art, title, artist and a wavy progress line
 * for the media session that is playing (or paused). It needs notification access for {@link MediaListener}, which
 * TVFreedom grants over adb; without it the card stays hidden. It is not focusable: the remote's play/pause key
 * already reaches the session.
 */
final class NowPlaying {

    private final Context ctx;
    private final float density;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final LinearLayout card;
    private final GradientDrawable shape = new GradientDrawable();
    private final ImageView art;
    private final TextView label, title, artist;
    private final WavyProgress progress;
    private final ComponentName listener;
    private MediaSessionManager sessions;
    private MediaController playing;
    private MediaMetadata meta;
    private PlaybackState state;
    private Bitmap shownArt;
    private boolean listening;
    private String labelPkg;
    private CharSequence labelText;
    private Palette palette = Palette.of(Palette.DEFAULT_SEED);

    // Kept from the callbacks: fetching them again from the controller re-parcels the whole bundle (art included).
    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override
        public void onMetadataChanged(MediaMetadata m) {
            meta = m;
            show();
        }

        @Override
        public void onPlaybackStateChanged(PlaybackState s) {
            state = s;
            show();
        }

        @Override
        public void onSessionDestroyed() {
            pick();
        }
    };
    private final MediaSessionManager.OnActiveSessionsChangedListener changed = list -> pick();

    NowPlaying(FrameLayout root) {
        ctx = root.getContext();
        density = ctx.getResources().getDisplayMetrics().density;
        listener = new ComponentName(ctx, MediaListener.class);

        card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(12), dp(12), dp(20), dp(12));
        // A pill at any height: text or font scaling may change it.
        card.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> shape.setCornerRadius((b - t) / 2f));
        card.setBackground(shape);
        card.setVisibility(View.GONE);
        card.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);

        art = new ImageView(ctx);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        GradientDrawable artShape = new GradientDrawable();
        artShape.setCornerRadius(dp(20));
        artShape.setColor(0x33FFFFFF);
        art.setBackground(artShape);
        art.setClipToOutline(true);
        card.addView(art, new LinearLayout.LayoutParams(dp(64), dp(64)));

        LinearLayout words = new LinearLayout(ctx);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setPadding(dp(14), 0, 0, 0);
        label = text(12, 700);
        label.setText(R.string.now_playing);
        label.setLetterSpacing(0.2f);
        label.setAllCaps(true);
        words.addView(label);
        title = text(16, 700);
        title.setPadding(0, dp(3), 0, 0);
        words.addView(title);
        artist = text(14, 500);
        words.addView(artist);
        progress = new WavyProgress(ctx);
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(12));
        pl.topMargin = dp(6);
        words.addView(progress, pl);
        card.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(380), ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.END | Gravity.TOP);
        lp.setMargins(0, dp(124), dp(56), 0);
        root.addView(card, lp);
        style();
    }

    /** Home is shown: follow the media sessions (needs notification access, else the card stays hidden). */
    void start() {
        try {
            if (sessions == null) sessions = ctx.getSystemService(MediaSessionManager.class);
            if (!listening) {
                sessions.addOnActiveSessionsChangedListener(changed, listener, handler);
                listening = true;
            }
            pick();
        } catch (SecurityException | NullPointerException notAllowed) {
            card.setVisibility(View.GONE);
        }
    }

    /** Home is hidden: let go of the sessions. */
    void stop() {
        if (listening) {
            try {
                sessions.removeOnActiveSessionsChangedListener(changed);
            } catch (RuntimeException ignored) {
            }
            listening = false;
        }
        if (playing != null) playing.unregisterCallback(callback);
        playing = null;
        meta = null;
        state = null;
    }

    /** The backdrop's colors changed (a new app got focus). */
    void setPalette(Palette p) {
        palette = p;
        style();
    }

    /** Follow the session that is playing, else the first one with something to show. */
    private void pick() {
        if (!listening) return;
        MediaController best = null;
        MediaMetadata bestMeta = null;
        PlaybackState bestState = null;
        try {
            for (MediaController c : sessions.getActiveSessions(listener)) {
                MediaMetadata m = c.getMetadata();
                if (m == null) continue;
                PlaybackState s = c.getPlaybackState();
                if (best == null || (s != null && s.getState() == PlaybackState.STATE_PLAYING)) {
                    best = c;
                    bestMeta = m;
                    bestState = s;
                }
            }
        } catch (SecurityException e) {
            best = null;
        }
        // Each call hands out a new controller for the same session: keep the one the callback is registered on.
        if (playing != null && best != null && best.getSessionToken().equals(playing.getSessionToken())) {
            best = playing;
        } else {
            if (playing != null) playing.unregisterCallback(callback);
            if (best != null) best.registerCallback(callback, handler);
        }
        playing = best;
        meta = bestMeta;
        state = bestState;
        show();
    }

    private void show() {
        int st = state != null ? state.getState() : PlaybackState.STATE_NONE;
        boolean running = st == PlaybackState.STATE_PLAYING || st == PlaybackState.STATE_BUFFERING;
        boolean active = playing != null && meta != null && (running || st == PlaybackState.STATE_PAUSED);
        if (!active) {
            if (card.getVisibility() == View.VISIBLE) {
                card.animate().alpha(0f).setInterpolator(Motion.DEFAULT_EFFECTS).setDuration(Motion.DEFAULT_EFFECTS_MS)
                        .withEndAction(() -> card.setVisibility(View.GONE)).start();
            }
            return;
        }
        CharSequence t = meta.getText(MediaMetadata.METADATA_KEY_TITLE);
        CharSequence a = meta.getText(MediaMetadata.METADATA_KEY_ARTIST);
        if (TextUtils.isEmpty(a)) a = meta.getText(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
        if (TextUtils.isEmpty(a)) a = appLabel(playing.getPackageName());
        title.setText(t);
        artist.setText(a);
        Bitmap bmp = meta.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (bmp == null) bmp = meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (bmp != shownArt) {
            shownArt = bmp;
            art.setImageBitmap(bmp);
        }
        long duration = meta.getLong(MediaMetadata.METADATA_KEY_DURATION);
        progress.setVisibility(duration > 0 ? View.VISIBLE : View.GONE);
        progress.setState(state.getPosition(), state.getLastPositionUpdateTime(), state.getPlaybackSpeed(), duration, running);
        // Cancel a pending fade-out first: its end action would hide the card again.
        card.animate().cancel();
        if (card.getVisibility() != View.VISIBLE || card.getAlpha() < 1f) {
            card.setAlpha(0f);
            card.setTranslationY(dp(10));
            card.setVisibility(View.VISIBLE);
            card.animate().alpha(1f).translationY(0).setInterpolator(Motion.DEFAULT_SPATIAL)
                    .setDuration(Motion.DEFAULT_SPATIAL_MS).start();
        }
    }

    private void style() {
        shape.setColor((palette.container & 0x00FFFFFF) | 0xD9000000);
        label.setTextColor(palette.onSurfaceVariant);
        title.setTextColor(palette.onSurface);
        artist.setTextColor(palette.onSurfaceVariant);
        progress.setColors(palette.primary, (palette.onSurfaceVariant & 0x00FFFFFF) | 0x55000000);
    }

    /** The app's name stands in for a missing artist; looked up once per app, as sessions report many times a second. */
    private CharSequence appLabel(String pkg) {
        if (!pkg.equals(labelPkg)) {
            labelPkg = pkg;
            try {
                labelText = ctx.getPackageManager().getApplicationLabel(ctx.getPackageManager().getApplicationInfo(pkg, 0));
            } catch (Exception e) {
                labelText = "";
            }
        }
        return labelText;
    }

    private TextView text(int sp, int weight) {
        TextView t = new TextView(ctx);
        t.setTextSize(sp);
        t.setTypeface(Flex.font(ctx, weight));
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setIncludeFontPadding(false);
        return t;
    }

    private int dp(float v) {
        return Math.round(v * density);
    }
}
