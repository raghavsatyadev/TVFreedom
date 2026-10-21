package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.ImageView;

import java.io.File;

/**
 * A company's look on the home screen, set by the TVFreedom phone app: its logo at the top left and a background colour
 * picked from that logo ({@link Config#brandColor}). A square logo stands left of the clock; a wide one takes the
 * greeting's line above it. A dark logo with see-through parts sits on a light plate, so it shows on the dark background.
 */
final class Branding {

    private static final float WIDE = 1.6f;

    private Branding() {
    }

    /** Show the logo in [square] or [wide] by its shape (a wide one takes the [greeting]'s line), and the colour. */
    static void apply(Context ctx, View root, ImageView square, ImageView wide, View greeting) {
        root.setBackgroundColor(Config.brandColor(ctx));
        Bitmap b = load(LogoProvider.file(ctx));
        boolean isWide = b != null && (float) b.getWidth() / b.getHeight() >= WIDE;
        show(ctx, square, b != null && !isWide ? b : null, 200);
        show(ctx, wide, isWide ? b : null, 480);
        greeting.setVisibility(isWide ? View.GONE : View.VISIBLE);
    }

    /**
     * Keep the clock clear of the buttons: a logo beside the clock, or a long Wi-Fi name on its button, can make them
     * meet. Then the date (and its divider) steps aside; it comes back when there is room again.
     */
    static void fitTopBar(View left, View buttons, View date, View divider) {
        date.setVisibility(View.VISIBLE);
        divider.setVisibility(View.VISIBLE);
        View.OnLayoutChangeListener fit = (v, l, t, r, b, ol, ot, or, ob) -> {
            int gap = (int) (16 * v.getResources().getDisplayMetrics().density);
            boolean shown = date.getVisibility() == View.VISIBLE;
            int need = left.getRight() + (shown ? 0 : date.getWidth() + divider.getWidth() + 2 * gap);
            boolean fits = need + gap <= buttons.getLeft();
            if (fits != shown && (fits || left.getRight() + gap > buttons.getLeft())) {
                int vis = fits ? View.VISIBLE : View.GONE;
                left.post(() -> {
                    date.setVisibility(vis);
                    divider.setVisibility(vis);
                });
            }
        };
        Object old = left.getTag(R.id.leftBlock);
        if (old instanceof View.OnLayoutChangeListener) {
            left.removeOnLayoutChangeListener((View.OnLayoutChangeListener) old);
            buttons.removeOnLayoutChangeListener((View.OnLayoutChangeListener) old);
        }
        left.setTag(R.id.leftBlock, fit);
        left.addOnLayoutChangeListener(fit);
        buttons.addOnLayoutChangeListener(fit);
    }

    /** The screensaver's logo above its clock: a square one taller, a wide one lower and longer; hidden without one. */
    static void applyDream(Context ctx, ImageView view) {
        Bitmap b = load(LogoProvider.file(ctx));
        boolean isWide = b != null && (float) b.getWidth() / b.getHeight() >= WIDE;
        view.getLayoutParams().height = (int) ((isWide ? 80 : 120) * ctx.getResources().getDisplayMetrics().density);
        show(ctx, view, b, isWide ? 560 : 220);
    }

    /** [b] in [view] at the view's height and at most [maxWidthDp] wide, or the view hidden for null. */
    private static void show(Context ctx, ImageView view, Bitmap b, int maxWidthDp) {
        if (b == null) {
            view.setImageDrawable(null);
            view.setVisibility(View.GONE);
            return;
        }
        float density = ctx.getResources().getDisplayMetrics().density;
        view.setMaxWidth((int) (maxWidthDp * density));
        view.setAdjustViewBounds(true);
        view.setScaleType(ImageView.ScaleType.FIT_CENTER);
        view.setImageBitmap(b);
        if (needsPlate(b)) {
            GradientDrawable plate = new GradientDrawable();
            plate.setColor(0xF2FFFFFF);
            plate.setCornerRadius(12 * density);
            view.setBackground(plate);
            int pad = (int) (8 * density);
            view.setPadding(pad, pad / 2, pad, pad / 2);
        } else {
            view.setBackground(null);
            view.setPadding(0, 0, 0, 0);
        }
        view.setVisibility(View.VISIBLE);
    }

    /** The logo, decoded at no more than about 1024 px on its long side; null when there is none. */
    private static Bitmap load(File f) {
        if (!f.isFile() || f.length() == 0) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        if (o.outWidth <= 0 || o.outHeight <= 0) return null;
        int sample = 1;
        while (Math.max(o.outWidth, o.outHeight) / (sample * 2) >= 1024) sample *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        return BitmapFactory.decodeFile(f.getPath(), o);
    }

    /** True for a mostly dark logo with see-through parts: on the dark background it would all but vanish. */
    private static boolean needsPlate(Bitmap b) {
        int step = Math.max(1, Math.max(b.getWidth(), b.getHeight()) / 64);
        int seeThrough = 0, opaque = 0;
        double light = 0;
        for (int y = 0; y < b.getHeight(); y += step) {
            for (int x = 0; x < b.getWidth(); x += step) {
                int c = b.getPixel(x, y);
                if (Color.alpha(c) < 128) {
                    seeThrough++;
                    continue;
                }
                opaque++;
                light += (0.2126 * Color.red(c) + 0.7152 * Color.green(c) + 0.0722 * Color.blue(c)) / 255;
            }
        }
        return opaque > 0 && seeThrough > (seeThrough + opaque) / 20 && light / opaque < 0.4;
    }
}
