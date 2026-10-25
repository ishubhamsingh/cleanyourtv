package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Outline;
import android.graphics.Rect;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Discover under the dock: one block in the app grid (AppAdapter's SHELF entry) with a strip of tabs, one per Discover
 * row (For you, Trending on your apps, ...), over a single row of posters, so the apps below are only two presses
 * from the dock. Focusing a tab shows its posters. Posters are as wide as the app tiles and line up with their
 * columns; the focused poster stays at the row's left edge, as on Google TV, and each tab remembers where it was.
 * Down from the dock lands on the posters; Up from them goes to the tabs, and Up again back to the dock
 * (Listener.onShelfExit); Down from the posters goes into the grid.
 */
final class Shelf {

    interface Listener {
        /**
         * A poster took focus, or a tab (then `poster` is the tab and `item` the poster that tab would focus); `row`
         * is the block's row view, for placing the top shelf above it.
         */
        void onPosterFocused(Discover.Item item, View poster, View row);
        void onPosterClicked(Discover.Item item);
        void onPosterLongPress(Discover.Item item, View poster);
        /** Up from the tabs (up = true) or Down from the posters. */
        void onShelfExit(boolean up);
    }

    private final Context ctx;
    private final Listener listener;
    private final boolean animations;
    private List<Discover.Row> rows = new ArrayList<>();
    /** Focused poster per row id, kept while the rows refresh. */
    private final Map<String, Integer> lastIndex = new HashMap<>();
    /** The row (tab) shown, by id, kept while the rows refresh. */
    private String selected;
    private LinearLayout block, rowView, tabs;
    private RecyclerView list;
    private PosterAdapter adapter;
    private int tileW, tileH, cellW;

    Shelf(Context ctx, Listener listener, boolean animations) {
        this.ctx = ctx;
        this.listener = listener;
        this.animations = animations;
    }

    boolean isEmpty() {
        return rows.isEmpty();
    }

    /** Focus is on a tab or a poster: rebuilding the block now would drop it. */
    boolean hasFocus() {
        return block != null && block.hasFocus();
    }

    void setRows(List<Discover.Row> rows) {
        this.rows = rows;
        if (block != null) fill();
    }

    /** Whether these rows hold the same titles as the shown ones (a refresh that changed nothing rebuilds nothing). */
    boolean same(List<Discover.Row> other) {
        return sameRows(rows, other);
    }

    static boolean sameRows(List<Discover.Row> rows, List<Discover.Row> other) {
        if (other.size() != rows.size()) return false;
        for (int i = 0; i < other.size(); i++) {
            Discover.Row a = rows.get(i), b = other.get(i);
            if (!a.id.equals(b.id) || !a.title.equals(b.title) || a.items.size() != b.items.size()) return false;
            for (int k = 0; k < a.items.size(); k++) if (!a.items.get(k).key.equals(b.items.get(k).key)) return false;
        }
        return true;
    }

    /** The index of the selected row: the remembered one if it is still there, else the first. */
    static int selectedIndex(List<Discover.Row> rows, String selected) {
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).id.equals(selected)) return i;
        return 0;
    }

    /** The block's view: created once, filled whenever the rows change. */
    View create(ViewGroup parent, int tileW, int tileH, int cellW) {
        this.tileW = tileW;
        this.tileH = tileH;
        this.cellW = cellW;
        block = new LinearLayout(ctx);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setClipChildren(false);
        block.setClipToPadding(false);
        block.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        block.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        fill();
        return block;
    }

    private void fill() {
        block.removeAllViews();
        if (rows.isEmpty()) return;
        float d = ctx.getResources().getDisplayMetrics().density;
        rowView = new LinearLayout(ctx);
        rowView.setOrientation(LinearLayout.VERTICAL);
        rowView.setClipChildren(false);
        rowView.setClipToPadding(false);

        tabs = new LinearLayout(ctx);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setClipChildren(false);
        tabs.setPadding((cellW - tileW) / 2 - Math.round(12 * d), Math.round(10 * d), 0, Math.round(4 * d));
        int current = selectedIndex(rows, selected);
        selected = rows.get(current).id;
        for (int i = 0; i < rows.size(); i++) {
            final int index = i;
            TextView tab = new TextView(ctx);
            tab.setText(rows.get(i).title);
            tab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            tab.setTypeface(androidx.core.content.res.ResourcesCompat.getFont(ctx, R.font.outfit_medium));
            tab.setTextColor(ctx.getColorStateList(R.color.shelf_tab_text));
            tab.setBackgroundResource(R.drawable.shelf_tab_bg);
            tab.setPadding(Math.round(14 * d), Math.round(7 * d), Math.round(14 * d), Math.round(7 * d));
            tab.setFocusable(true);
            tab.setActivated(i == current);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(Math.round(6 * d));
            tab.setLayoutParams(lp);
            tab.setOnFocusChangeListener((v, focused) -> {
                if (!focused) return;
                select(index);
                Discover.Row row = rows.get(index);
                int at = Math.max(0, Math.min(lastIndex.getOrDefault(row.id, 0), row.items.size() - 1));
                if (!row.items.isEmpty()) listener.onPosterFocused(row.items.get(at), v, rowView);
            });
            tab.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    listener.onShelfExit(true);
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    focusPosters();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) return index == 0;
                return keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && index == rows.size() - 1;
            });
            tab.setOnClickListener(v -> focusPosters());
            tabs.addView(tab);
        }
        rowView.addView(tabs);

        list = new RecyclerView(ctx);
        list.setClipChildren(false);
        list.setClipToPadding(false);
        list.setItemAnimator(null);
        list.setHasFixedSize(true);
        list.setLayoutManager(new LinearLayoutManager(ctx, LinearLayoutManager.HORIZONTAL, false) {
            // Focus moves are scrolled by hand (the focused poster to the left edge), not by the default rule.
            @Override
            public boolean requestChildRectangleOnScreen(@NonNull RecyclerView parent, @NonNull View child,
                                                         @NonNull Rect rect, boolean immediate, boolean focusedChildVisible) {
                return false;
            }
        });
        adapter = new PosterAdapter(ctx, tileW, tileH, cellW, animations, new PosterAdapter.Listener() {
            @Override
            public void onFocused(Discover.Item item, View poster, int position) {
                lastIndex.put(selected, position);
                View item0 = (View) poster.getParent();
                int dx = item0.getLeft() - list.getPaddingLeft();
                if (dx != 0) {
                    if (animations) list.smoothScrollBy(dx, 0, new DecelerateInterpolator(1.6f));
                    else list.scrollBy(dx, 0);
                }
                listener.onPosterFocused(item, poster, rowView);
            }

            @Override
            public void onClicked(Discover.Item item) {
                listener.onPosterClicked(item);
            }

            @Override
            public void onLongPress(Discover.Item item, View poster) {
                listener.onPosterLongPress(item, poster);
            }

            @Override
            public boolean onKey(int position, int keyCode) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    focusTab();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    listener.onShelfExit(false);
                    return true;
                }
                // The row's ends: nothing past them (the default focus search would leave the row).
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) return position == 0;
                return keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && position == adapter.getItemCount() - 1;
            }
        });
        list.setAdapter(adapter);
        list.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                PosterAdapter.itemHeight(tileH, d)));
        rowView.addView(list);
        block.addView(rowView);
        show(current);
    }

    /** Shows a tab's posters, scrolled to where that tab was left. */
    private void select(int index) {
        if (index < 0 || index >= rows.size()) return;
        String id = rows.get(index).id;
        if (id.equals(selected) && adapter.getItemCount() > 0) return;
        selected = id;
        for (int i = 0; i < tabs.getChildCount(); i++) tabs.getChildAt(i).setActivated(i == index);
        show(index);
    }

    private void show(int index) {
        Discover.Row row = rows.get(index);
        adapter.setItems(row.items);
        int at = Math.max(0, Math.min(lastIndex.getOrDefault(row.id, 0), row.items.size() - 1));
        ((LinearLayoutManager) list.getLayoutManager()).scrollToPositionWithOffset(at, 0);
    }

    private void focusTab() {
        if (tabs == null) return;
        tabs.getChildAt(selectedIndex(rows, selected)).requestFocus();
    }

    /** Focuses the selected tab's remembered poster. */
    private void focusPosters() {
        if (list == null || rows.isEmpty()) return;
        Discover.Row row = rows.get(selectedIndex(rows, selected));
        int at = Math.max(0, Math.min(lastIndex.getOrDefault(row.id, 0), row.items.size() - 1));
        RecyclerView.ViewHolder vh = list.findViewHolderForAdapterPosition(at);
        if (vh != null) {
            vh.itemView.findViewById(R.id.poster).requestFocus();
            return;
        }
        ((LinearLayoutManager) list.getLayoutManager()).scrollToPositionWithOffset(at, 0);
        list.post(() -> {
            RecyclerView.ViewHolder h = list.findViewHolderForAdapterPosition(at);
            if (h != null) h.itemView.findViewById(R.id.poster).requestFocus();
        });
    }

    /** Focus into the block: its posters (the block has one row; `index` is kept for the callers). */
    void focusRow(int index) {
        focusPosters();
    }

    int rowCount() {
        return rows.isEmpty() ? 0 : 1;
    }

    /** Posters: Discover titles, or (in search) apps. Sized like the app tiles. */
    static final class PosterAdapter extends RecyclerView.Adapter<PosterAdapter.Holder> {
        interface Listener {
            void onFocused(Discover.Item item, View poster, int position);
            void onClicked(Discover.Item item);
            void onLongPress(Discover.Item item, View poster);
            /** A D-pad key on a poster; true when handled. */
            boolean onKey(int position, int keyCode);
        }

        static float FOCUS_SCALE = 1.10f;

        /** The poster with the name line under it and the item's padding (item_poster.xml). */
        static int itemHeight(int tileH, float density) {
            return tileH + Math.round((8 + 8 + 7 + 22) * density);
        }

        private final Context ctx;
        private final int tileW, tileH, cellW;
        private final boolean animations;
        private final Listener listener;
        private List<Discover.Item> items = new ArrayList<>();

        PosterAdapter(Context ctx, int tileW, int tileH, int cellW, boolean animations, Listener listener) {
            this.ctx = ctx;
            this.tileW = tileW;
            this.tileH = tileH;
            this.cellW = cellW;
            this.animations = animations;
            this.listener = listener;
            setHasStableIds(true);
        }

        void setItems(List<Discover.Item> items) {
            this.items = items;
            notifyDataSetChanged();
        }

        Discover.Item at(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return items.get(position).key.hashCode();
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(ctx).inflate(R.layout.item_poster, parent, false);
            v.setLayoutParams(new RecyclerView.LayoutParams(cellW, ViewGroup.LayoutParams.WRAP_CONTENT));
            View poster = v.findViewById(R.id.poster);
            ViewGroup.LayoutParams lp = poster.getLayoutParams();
            lp.width = tileW;
            lp.height = tileH;
            poster.setLayoutParams(lp);
            TextView app = v.findViewById(R.id.posterApp);
            ViewGroup.MarginLayoutParams alp = (ViewGroup.MarginLayoutParams) app.getLayoutParams();
            float d = ctx.getResources().getDisplayMetrics().density;
            alp.width = tileW;
            alp.topMargin = tileH + Math.round(7 * d);
            app.setLayoutParams(alp);
            return new Holder(v, this);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            Discover.Item it = items.get(position);
            h.title.setText(it.title);
            h.app.setText(it.playable() ? it.app : it.elsewhere.isEmpty() ? "" : it.elsewhere);
            h.poster.setAlpha(it.playable() || it.appArt != null ? 1f : 0.55f);
            boolean focused = h.poster.isFocused();
            h.poster.setScaleX(focused && animations ? FOCUS_SCALE : 1f);
            h.poster.setScaleY(focused && animations ? FOCUS_SCALE : 1f);
            h.app.setAlpha(focused ? 1f : 0f);
            if (it.appArt != null) {
                h.art.setTag(R.id.discoverImage, null);
                h.art.setImageDrawable(it.appArt);
                h.title.setVisibility(View.GONE);
            } else {
                h.title.setVisibility(View.VISIBLE);
                Discover.get(ctx).load(h.art, Discover.image("w780", it.backdrop.isEmpty() ? it.poster : it.backdrop),
                        tileW * 2);
            }
        }

        static final class Holder extends RecyclerView.ViewHolder {
            final View poster;
            final ImageView art;
            final TextView title, app;

            Holder(View v, PosterAdapter a) {
                super(v);
                poster = v.findViewById(R.id.poster);
                art = v.findViewById(R.id.art);
                title = v.findViewById(R.id.posterTitle);
                app = v.findViewById(R.id.posterApp);
                float d = v.getResources().getDisplayMetrics().density;
                poster.setOutlineProvider(new ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, Outline outline) {
                        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), AppAdapter.cornerRadius(view.getWidth(), d));
                    }
                });
                poster.setClipToOutline(true);
                poster.setOnClickListener(x -> {
                    int pos = getBindingAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) a.listener.onClicked(a.items.get(pos));
                });
                poster.setOnLongClickListener(x -> {
                    int pos = getBindingAdapterPosition();
                    if (pos != RecyclerView.NO_POSITION) a.listener.onLongPress(a.items.get(pos), poster);
                    return true;
                });
                poster.setOnKeyListener((x, keyCode, event) -> {
                    if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                    if (keyCode != KeyEvent.KEYCODE_DPAD_UP && keyCode != KeyEvent.KEYCODE_DPAD_DOWN
                            && keyCode != KeyEvent.KEYCODE_DPAD_LEFT && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
                    int pos = getBindingAdapterPosition();
                    return pos != RecyclerView.NO_POSITION && a.listener.onKey(pos, keyCode);
                });
                poster.setOnFocusChangeListener((x, focused) -> {
                    float s = focused && a.animations ? FOCUS_SCALE : 1f;
                    poster.animate().cancel();
                    if (a.animations) {
                        poster.animate().scaleX(s).scaleY(s).translationZ(focused ? 8 * d : 0)
                                .setDuration(focused ? 220 : 170).setInterpolator(new DecelerateInterpolator(1.5f)).start();
                        app.animate().alpha(focused ? 1f : 0f).setDuration(200).start();
                    } else {
                        app.setAlpha(focused ? 1f : 0f);
                    }
                    int pos = getBindingAdapterPosition();
                    if (focused && pos != RecyclerView.NO_POSITION) a.listener.onFocused(a.items.get(pos), poster, pos);
                });
            }
        }
    }
}
