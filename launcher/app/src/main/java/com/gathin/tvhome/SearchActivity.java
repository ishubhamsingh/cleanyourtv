package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Discover search, opened from the search pill on Home: the TV's own apps as you type, and movies and shows from the
 * Discover extension (TMDB) a moment after you stop, the ones on your apps first. OK opens a title in its app; hold
 * OK for More like this and Not interested. Back closes it.
 */
public class SearchActivity extends Activity {
    private static final int COLUMNS = 4;
    private static final long DEBOUNCE_MS = 450;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Discover discover;
    private EditText query;
    private TextView infoTitle, infoLine, infoOverview, status, appsHeader, titlesHeader;
    private RecyclerView apps, results;
    private Shelf.PosterAdapter appsAdapter, resultsAdapter;
    private List<Discover.Item> installed;
    private String asked = "";

    private final Runnable runSearch = this::searchTitles;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);
        discover = Discover.get(this);
        query = findViewById(R.id.query);
        infoTitle = findViewById(R.id.infoTitle);
        infoLine = findViewById(R.id.infoLine);
        infoOverview = findViewById(R.id.infoOverview);
        status = findViewById(R.id.status);
        appsHeader = findViewById(R.id.appsHeader);
        titlesHeader = findViewById(R.id.titlesHeader);
        apps = findViewById(R.id.apps);
        results = findViewById(R.id.results);

        float d = getResources().getDisplayMetrics().density;
        int inner = getResources().getDisplayMetrics().widthPixels - Math.round(2 * 56 * d);
        int cell = inner / COLUMNS;
        int tileW = AppAdapter.tileWidthPx(inner, COLUMNS, d), tileH = AppAdapter.tileHeightPx(tileW);

        appsAdapter = new Shelf.PosterAdapter(this, tileW, tileH, cell, true, listener(true));
        apps.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        apps.setAdapter(appsAdapter);
        apps.setItemAnimator(null);
        resultsAdapter = new Shelf.PosterAdapter(this, tileW, tileH, cell, true, listener(false));
        results.setLayoutManager(new GridLayoutManager(this, COLUMNS));
        results.setAdapter(resultsAdapter);
        results.setItemAnimator(null);

        query.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                showApps(s.toString());
                handler.removeCallbacks(runSearch);
                if (s.toString().trim().length() >= 2) handler.postDelayed(runSearch, DEBOUNCE_MS);
            }
        });
        query.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE
                    || event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                handler.removeCallbacks(runSearch);
                searchTitles();
                hideKeyboard();
                focusFirstResult();
                return true;
            }
            return false;
        });
        query.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                hideKeyboard();
                return focusFirstResult();
            }
            return false;
        });
        String preset = getIntent().getStringExtra("query");
        if (preset != null) query.setText(preset);
        query.requestFocus();
    }

    private Shelf.PosterAdapter.Listener listener(boolean appRow) {
        return new Shelf.PosterAdapter.Listener() {
            @Override
            public void onFocused(Discover.Item item, View poster, int position) {
                infoTitle.setText(item.title);
                infoLine.setText(item.appArt != null ? getString(R.string.discover_apps) : item.line());
                infoOverview.setText(item.overview == null ? "" : item.overview);
            }

            @Override
            public void onClicked(Discover.Item item) {
                if (item.appLaunch != null) {
                    try {
                        startActivity(item.appLaunch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                        Frecency.record(SearchActivity.this, item.pkg);
                        LastOpened.record(SearchActivity.this, item.pkg);
                    } catch (RuntimeException e) {
                        Toast.makeText(SearchActivity.this, getString(R.string.cant_open, item.title), Toast.LENGTH_SHORT).show();
                    }
                    return;
                }
                if (!item.playable()) {
                    Toast.makeText(SearchActivity.this, item.elsewhere.isEmpty() ? getString(R.string.discover_not_on_apps)
                            : getString(R.string.discover_streams_on, item.elsewhere), Toast.LENGTH_LONG).show();
                    return;
                }
                if (discover.open(SearchActivity.this, item)) {
                    Frecency.record(SearchActivity.this, item.pkg);
                    LastOpened.record(SearchActivity.this, item.pkg);
                }
            }

            @Override
            public void onLongPress(Discover.Item item, View poster) {
                if (item.appArt == null) showMenu(item, poster);
            }

            @Override
            public boolean onKey(int position, int keyCode) {
                int count = appRow ? appsAdapter.getItemCount() : resultsAdapter.getItemCount();
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) return appRow ? position == 0 : position % COLUMNS == 0;
                if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    return position == count - 1 || !appRow && position % COLUMNS == COLUMNS - 1;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP && (appRow || position < COLUMNS)) {
                    // Up from the first results row: the app row if there is one, else the field.
                    if (!appRow && apps.getVisibility() == View.VISIBLE) focusIn(apps, 0);
                    else query.requestFocus();
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && appRow) {
                    if (resultsAdapter.getItemCount() > 0) focusIn(results, 0);
                    return true;
                }
                return false;
            }
        };
    }

    // MARK: - Apps (on the TV, instant)

    private void showApps(String text) {
        String q = text.trim().toLowerCase(Locale.getDefault());
        List<Discover.Item> found = new ArrayList<>();
        if (!q.isEmpty()) {
            for (Discover.Item it : installedApps()) {
                if (it.title.toLowerCase(Locale.getDefault()).contains(q)) found.add(it);
                if (found.size() >= 12) break;
            }
        }
        appsAdapter.setItems(found);
        int vis = found.isEmpty() ? View.GONE : View.VISIBLE;
        apps.setVisibility(vis);
        appsHeader.setVisibility(vis);
    }

    /** The TV's apps with a TV entry point, once per search screen. */
    private List<Discover.Item> installedApps() {
        if (installed != null) return installed;
        installed = new ArrayList<>();
        PackageManager pm = getPackageManager();
        Set<String> seen = new HashSet<>();
        List<ResolveInfo> list = pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0);
        for (ResolveInfo ri : list) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || !seen.add(pkg)) continue;
            Discover.Item it = new Discover.Item();
            it.key = "app:" + pkg;
            it.pkg = pkg;
            it.title = String.valueOf(ri.loadLabel(pm));
            it.app = it.title;
            Drawable banner = ri.activityInfo.loadBanner(pm);
            it.appArt = banner != null ? banner : ri.loadIcon(pm);
            Intent launch = pm.getLeanbackLaunchIntentForPackage(pkg);
            it.appLaunch = launch != null ? launch : pm.getLaunchIntentForPackage(pkg);
            it.meta = "";
            it.elsewhere = "";
            it.backdrop = "";
            it.poster = "";
            if (it.appLaunch != null) installed.add(it);
        }
        return installed;
    }

    // MARK: - Movies and shows (Discover extension)

    private void searchTitles() {
        String q = query.getText().toString().trim();
        if (q.length() < 2 || q.equals(asked)) return;
        asked = q;
        status.setText(R.string.discover_searching);
        discover.search(q, items -> {
            if (isDestroyed()) return;
            resultsAdapter.setItems(items);
            titlesHeader.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
            status.setText(items.isEmpty() ? getString(R.string.discover_no_results, q) : "");
        });
    }

    // MARK: - Focus

    private boolean focusFirstResult() {
        if (apps.getVisibility() == View.VISIBLE && appsAdapter.getItemCount() > 0) return focusIn(apps, 0);
        if (resultsAdapter.getItemCount() > 0) return focusIn(results, 0);
        return false;
    }

    private boolean focusIn(RecyclerView list, int position) {
        RecyclerView.ViewHolder vh = list.findViewHolderForAdapterPosition(position);
        if (vh != null) return vh.itemView.findViewById(R.id.poster).requestFocus();
        list.scrollToPosition(position);
        list.post(() -> {
            RecyclerView.ViewHolder h = list.findViewHolderForAdapterPosition(position);
            if (h != null) h.itemView.findViewById(R.id.poster).requestFocus();
        });
        return true;
    }

    private void hideKeyboard() {
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        if (imm != null) imm.hideSoftInputFromWindow(query.getWindowToken(), 0);
    }

    /** Hold OK on a title: open it, more like this, not interested (the same as on Home). */
    private void showMenu(Discover.Item item, View poster) {
        float d = getResources().getDisplayMetrics().density;
        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, (ViewGroup) poster.getRootView(), false);
        LinearLayout rows = panel.findViewById(R.id.rows);
        PopupWindow popup = new PopupWindow(panel, Math.round(240 * d), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        if (item.playable()) addRow(rows, popup, getString(R.string.discover_open_in, item.app), () -> {
            if (discover.open(this, item)) Frecency.record(this, item.pkg);
        });
        addRow(rows, popup, getString(item.liked ? R.string.discover_liked : R.string.discover_like), () -> {
            discover.like(item);
            Toast.makeText(this, R.string.discover_liked, Toast.LENGTH_SHORT).show();
        });
        addRow(rows, popup, getString(R.string.discover_hide), () -> {
            discover.hide(item);
            List<Discover.Item> kept = new ArrayList<>();
            for (int i = 0; i < resultsAdapter.getItemCount(); i++) {
                if (!resultsAdapter.at(i).key.equals(item.key)) kept.add(resultsAdapter.at(i));
            }
            resultsAdapter.setItems(kept);
            Toast.makeText(this, getString(R.string.discover_hidden, item.title), Toast.LENGTH_SHORT).show();
        });
        popup.showAsDropDown(poster, 0, Math.round(10 * d), Gravity.START);
        rows.getChildAt(0).requestFocus();
    }

    private void addRow(LinearLayout rows, PopupWindow popup, String title, Runnable action) {
        View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        row.findViewById(R.id.icon).setVisibility(View.GONE);
        row.findViewById(R.id.dot).setVisibility(View.GONE);
        row.findViewById(R.id.status).setVisibility(View.GONE);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        // The OK-up after the long press must not activate the first row.
        final boolean[] pressed = {false};
        row.setOnKeyListener((v, code, event) -> {
            if (code != KeyEvent.KEYCODE_DPAD_CENTER && code != KeyEvent.KEYCODE_ENTER) return false;
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (event.getRepeatCount() == 0) pressed[0] = true;
                return true;
            }
            if (pressed[0]) {
                pressed[0] = false;
                popup.dismiss();
                action.run();
            }
            return true;
        });
        row.setOnClickListener(v -> {
            popup.dismiss();
            action.run();
        });
        rows.addView(row);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(runSearch);
    }
}
