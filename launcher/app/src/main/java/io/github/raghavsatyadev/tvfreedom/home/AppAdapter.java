package io.github.raghavsatyadev.tvfreedom.home;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * App tiles. The favourites (fixed order) come first and fill whole rows, padded with empty spacer slots
 * (null entries) so the other apps, alphabetical, start on a new row. Those favourite rows are the "dock".
 * TV apps come from the Leanback launcher list; installed apps that only have a phone launcher entry are
 * listed too (icon tiles), so sideloaded apps can be opened.
 */
public class AppAdapter extends RecyclerView.Adapter<AppAdapter.Holder> {

    interface TileListener {
        void onTileFocused(int position, View card);

        /** Called for D-pad Up/Down on a tile. Return true when handled. */
        boolean onTileVerticalKey(int position, int keyCode);

        /** Long press OK (or Menu) on a tile: show its menu. */
        void onTileMenu(int position, View card);

        /** OK on the "+" tile at the end of the dock: pick an app to add to the favourites. */
        void onAddTile(View card);
    }

    static final class Entry {
        final String label;
        final ComponentName component;
        final String pkg;
        final Drawable banner;
        final Drawable icon;
        /** Opened with the Leanback launcher category; false for phone-only apps. */
        final boolean leanback;
        /** The "+" tile at the end of the dock, not an app. */
        final boolean add;
        int ambient;

        Entry(String label, ComponentName component, Drawable banner, Drawable icon, boolean leanback) {
            this(label, component, banner, icon, leanback, false);
        }

        private Entry(String label, ComponentName component, Drawable banner, Drawable icon, boolean leanback, boolean add) {
            this.label = label;
            this.component = component;
            this.pkg = component.getPackageName();
            this.banner = banner;
            this.icon = icon;
            this.leanback = leanback;
            this.add = add;
        }
    }

    static final float FOCUS_SCALE = FocusMotion.SCALE;

    private final Context ctx;
    private final TileListener listener;
    private final int columns;
    /** Favourite slots including spacers: a multiple of columns. Set by reload(). */
    private int dockSize;
    /** Dock order, changed from the tile menu (starts from res/values/config.xml, dock_apps). */
    private final Favourites favourites;
    /** Never shown as tiles (res/values/config.xml, hidden_apps). */
    private final Set<String> hidden;
    private final List<Entry> items = new ArrayList<>();
    private final List<String> installedFavourites = new ArrayList<>();
    private final Entry addTile;

    AppAdapter(Context ctx, TileListener listener, int columns) {
        this.ctx = ctx;
        this.listener = listener;
        this.columns = columns;
        this.favourites = new Favourites(ctx);
        this.hidden = new HashSet<>(Arrays.asList(ctx.getResources().getStringArray(R.array.hidden_apps)));
        // Tiles keep their identity across reload(), so a focused tile stays on its app instead of
        // its view being rebound to whatever app now sits in that slot.
        setHasStableIds(true);
        addTile = new Entry(ctx.getString(R.string.add_app), new ComponentName(ctx, "add"), null,
                ctx.getDrawable(R.drawable.ic_add), false, true);
    }

    /** Every app tile, favourites first, without the dock's spacers. */
    List<Entry> apps() {
        List<Entry> out = new ArrayList<>();
        for (Entry e : items) if (e != null && !e.add) out.add(e);
        return out;
    }

    void reload() {
        PackageManager pm = ctx.getPackageManager();
        Map<String, Entry> byPkg = new LinkedHashMap<>();
        collect(pm, Intent.CATEGORY_LEANBACK_LAUNCHER, byPkg);
        collect(pm, Intent.CATEGORY_LAUNCHER, byPkg);
        List<Entry> fresh = new ArrayList<>(byPkg.values());
        Collections.sort(fresh, (a, b) -> {
            int fa = favourites.indexOf(a.pkg), fb = favourites.indexOf(b.pkg);
            if (fa >= 0 || fb >= 0) {
                if (fa < 0) return 1;
                if (fb < 0) return -1;
                return Integer.compare(fa, fb);
            }
            return a.label.compareToIgnoreCase(b.label);
        });
        int favCount = 0;
        while (favCount < fresh.size() && favourites.contains(fresh.get(favCount).pkg)) favCount++;
        installedFavourites.clear();
        for (Entry e : fresh.subList(0, favCount)) installedFavourites.add(e.pkg);
        // The "+" tile closes the dock while some app is not a favourite yet.
        boolean canAdd = favCount < fresh.size();
        dockSize = (favCount + (canAdd ? 1 : 0) + columns - 1) / columns * columns;
        items.clear();
        items.addAll(fresh.subList(0, favCount));
        if (canAdd) items.add(addTile);
        while (items.size() < dockSize) items.add(null);
        items.addAll(fresh.subList(favCount, fresh.size()));
        notifyDataSetChanged();
    }

    /**
     * One tile per app. Leanback entries come first and win; phone launcher entries are added only for
     * installed apps (not system apps, whose phone screens are usually useless on a TV).
     */
    private void collect(PackageManager pm, String category, Map<String, Entry> byPkg) {
        boolean leanback = Intent.CATEGORY_LEANBACK_LAUNCHER.equals(category);
        for (ResolveInfo ri : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category), 0)) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (byPkg.containsKey(pkg) || pkg.equals(ctx.getPackageName()) || hidden.contains(pkg)) continue;
            if (!leanback && (ri.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
            Drawable banner = ri.activityInfo.loadBanner(pm);
            if (banner == null) banner = ri.activityInfo.applicationInfo.loadBanner(pm);
            Drawable icon = ri.loadIcon(pm);
            String label = String.valueOf(ri.loadLabel(pm));
            byPkg.put(pkg, new Entry(label, new ComponentName(pkg, ri.activityInfo.name), banner, icon, leanback));
        }
    }

    Favourites favourites() {
        return favourites;
    }

    /** Installed favourites in dock order. */
    List<String> installedFavourites() {
        return installedFavourites;
    }

    /** The app at a position, or null for a spacer. */
    Entry entryAt(int position) {
        return position >= 0 && position < items.size() ? items.get(position) : null;
    }

    /** Favourite slots including spacers (a multiple of the column count). */
    int dockSize() {
        return dockSize;
    }

    boolean isSpacer(int position) {
        return position >= 0 && position < items.size() && items.get(position) == null;
    }

    /** The nearest real tile to position, looking forward first when forward is true. */
    int nearestTile(int position, boolean forward) {
        for (int step = 0; step < items.size(); step++) {
            int a = forward ? position + step : position - step, b = forward ? position - step : position + step;
            if (a >= 0 && a < items.size() && items.get(a) != null) return a;
            if (b >= 0 && b < items.size() && items.get(b) != null) return b;
        }
        return 0;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false);
        return new Holder(v, listener);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Entry e = items.get(position);
        h.card.setVisibility(e == null ? View.INVISIBLE : View.VISIBLE);
        h.card.setFocusable(e != null);
        if (e == null) {
            h.name.setVisibility(position < dockSize ? View.GONE : View.INVISIBLE);
            h.card.setOnClickListener(null);
            h.card.setOnLongClickListener(null);
            return;
        }
        h.name.setText(e.label);
        h.name.setVisibility(position < dockSize ? View.GONE : View.VISIBLE);   // the dock has no labels (nor room for them)
        boolean focused = h.card.isFocused();   // a rebind after reload() can hit the focused tile
        h.name.setAlpha(focused ? 1f : 0f);
        h.motion.snap(focused);
        if (e.banner != null) {
            h.banner.setImageDrawable(e.banner);
            h.banner.setVisibility(View.VISIBLE);
            h.icon.setVisibility(View.GONE);
            h.label.setVisibility(View.GONE);
        } else {
            h.banner.setImageDrawable(null);
            h.banner.setVisibility(View.GONE);
            h.icon.setImageDrawable(e.icon);
            h.icon.setVisibility(View.VISIBLE);
            h.label.setText(e.label);
            h.label.setVisibility(View.VISIBLE);
        }
        h.icon.setImageTintList(null);
        if (e.add) {
            h.icon.setImageTintList(ColorStateList.valueOf(0xB3FFFFFF));
            h.card.setOnClickListener(v -> {
                if (listener != null) listener.onAddTile(v);
            });
            h.card.setOnLongClickListener(View::performClick);
            return;
        }
        h.card.setOnClickListener(v -> launch(e));
        h.card.setOnLongClickListener(v -> {
            int pos = h.getBindingAdapterPosition();
            if (listener != null && pos != RecyclerView.NO_POSITION) listener.onTileMenu(pos, v);
            return true;
        });
    }

    private void launch(Entry e) {
        Intent i = new Intent(Intent.ACTION_MAIN)
                .addCategory(e.leanback ? Intent.CATEGORY_LEANBACK_LAUNCHER : Intent.CATEGORY_LAUNCHER)
                .setComponent(e.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            ctx.startActivity(i);
        } catch (Exception ex) {
            Toast.makeText(ctx, ctx.getString(R.string.cant_open, e.label), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public long getItemId(int position) {
        Entry e = items.get(position);
        return e == null ? -1 - position : e.component.flattenToShortString().hashCode();
    }

    /** Stable key for the app at a position (its component), or null. */
    String keyAt(int position) {
        return position >= 0 && position < items.size() && items.get(position) != null
                ? items.get(position).component.flattenToShortString() : null;
    }

    /** Key of the tile for a package, or null when it has none. */
    String keyOfPackage(String pkg) {
        for (Entry e : items) if (e != null && !e.add && e.pkg.equals(pkg)) return e.component.flattenToShortString();
        return null;
    }

    /** Position of the app with this key, or -1 when it is gone. */
    int positionOf(String key) {
        if (key == null) return -1;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i) != null && items.get(i).component.flattenToShortString().equals(key)) return i;
        }
        return -1;
    }

    String labelAt(int position) {
        return position >= 0 && position < items.size() && items.get(position) != null ? items.get(position).label : "";
    }

    /** A vivid colour sampled from the app's banner (or icon), cached per entry. */
    int ambientColorAt(int position) {
        if (position < 0 || position >= items.size() || items.get(position) == null) return 0xFF444444;
        Entry e = items.get(position);
        if (e.ambient == 0) e.ambient = vividColor(e.banner != null ? e.banner : e.icon);
        return e.ambient;
    }

    private static int vividColor(Drawable live) {
        if (live == null) return 0xFF444444;
        // Draw a private copy: changing the bounds of the drawable an ImageView shows would blank the tile.
        Drawable d = live.getConstantState() != null ? live.getConstantState().newDrawable().mutate() : live;
        Rect saved = new Rect(d.getBounds());
        int n = 12;
        Bitmap bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        d.setBounds(0, 0, n, n);
        d.draw(c);
        d.setBounds(saved);
        float[] hsv = new float[3];
        int best = 0xFF444444;
        float bestScore = -1f;
        long r = 0, g = 0, b = 0;
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                int p = bmp.getPixel(x, y);
                r += Color.red(p);
                g += Color.green(p);
                b += Color.blue(p);
                Color.colorToHSV(p, hsv);
                float score = hsv[1] * Math.min(1f, hsv[2] * 1.4f);   // saturated and not too dark
                if (score > bestScore) {
                    bestScore = score;
                    best = p;
                }
            }
        }
        bmp.recycle();
        if (bestScore < 0.25f) {   // nearly monochrome banner (e.g. white): use its average, lifted
            int avg = Color.rgb((int) (r / (n * n)), (int) (g / (n * n)), (int) (b / (n * n)));
            Color.colorToHSV(avg, hsv);
            hsv[2] = Math.max(hsv[2], 0.55f);
            return Color.HSVToColor(hsv);
        }
        Color.colorToHSV(best, hsv);
        hsv[1] = Math.min(1f, hsv[1] * 1.1f);
        hsv[2] = Math.max(0.5f, hsv[2]);
        return Color.HSVToColor(hsv);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final View card;
        final ImageView banner;
        final ImageView icon;
        final TextView label;
        final TextView name;
        final FocusMotion motion;

        Holder(View v, TileListener listener) {
            super(v);
            card = v.findViewById(R.id.card);
            banner = v.findViewById(R.id.banner);
            icon = v.findViewById(R.id.icon);
            label = v.findViewById(R.id.label);
            name = v.findViewById(R.id.name);

            final float density = v.getResources().getDisplayMetrics().density;
            motion = new FocusMotion(card);
            Flex.use(name, 560);

            card.setOnKeyListener((view, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_MENU) return view.performLongClick();
                if (keyCode != KeyEvent.KEYCODE_DPAD_UP && keyCode != KeyEvent.KEYCODE_DPAD_DOWN) return false;
                int pos = getBindingAdapterPosition();
                return listener != null && pos != RecyclerView.NO_POSITION && listener.onTileVerticalKey(pos, keyCode);
            });

            card.setOnFocusChangeListener((view, focused) -> {
                motion.set(focused);
                if (focused) Flex.settle(name);
                name.animate().alpha(focused ? 1f : 0f)
                        .translationY(focused ? density * 4f : -density * 6f)
                        .setDuration(220).start();
                int pos = getBindingAdapterPosition();
                if (focused && listener != null && pos != RecyclerView.NO_POSITION) {
                    listener.onTileFocused(pos, view);
                }
            });
        }
    }
}
