package com.gathin.tvhome.backlight;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.List;

/**
 * Watches which full-screen app is in front and, in the TV's input app, the input name in its banner. It reads
 * nothing else: no text from other apps, no keys. Sends the box Sync for the chosen inputs, the other mode for
 * everything else.
 */
public class InputWatcher extends AccessibilityService {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Rect bounds = new Rect();
    private final Runnable evaluate = this::evaluate, settled = this::evaluate;
    private Rule rule;
    private Box box;
    private String bannerId;

    @Override
    protected void onServiceConnected() {
        rule = new Rule(BuildConfig.INPUT_APP, BuildConfig.SYNC_INPUTS);
        bannerId = BuildConfig.INPUT_APP + ":id/input_name";
        box = Box.get(this);
        evaluate();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        if (rule == null) return;
        // Content changes come from every app all the time; only the input app's (its banner) matter.
        if (e.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                && (e.getPackageName() == null || !rule.inputApp().contentEquals(e.getPackageName()))) return;
        handler.removeCallbacks(evaluate);
        handler.removeCallbacks(settled);
        handler.postDelayed(evaluate, 300);
        // Going Home, the input's window can still be on top at the first look, and the launcher's own window
        // sends no further event: look again once the switch has settled.
        handler.postDelayed(settled, 1500);
    }

    private void evaluate() {
        String front = frontApp();
        box.front(front);
        String input = rule.lastInput();
        if (input != null) box.prefs().edit().putString(Box.INPUT, input).apply();
        if (!box.prefs().getBoolean(Box.AUTO, true)) return;
        Rule.Mode mode = rule.decide(front);
        if (mode != null) box.want(mode);
    }

    /** The topmost full-screen app window's package (reading the input banner on the way), or null. */
    private String frontApp() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        List<AccessibilityWindowInfo> windows;
        try {
            windows = getWindows();
        } catch (RuntimeException e) {
            return null;
        }
        for (AccessibilityWindowInfo w : windows) {        // topmost first
            if (w.getType() != AccessibilityWindowInfo.TYPE_APPLICATION) continue;
            w.getBoundsInScreen(bounds);
            if (!Rule.fullScreen(bounds.width(), bounds.height(), dm.widthPixels, dm.heightPixels)) continue;
            AccessibilityNodeInfo root = w.getRoot();
            if (root == null || root.getPackageName() == null) continue;
            String pkg = root.getPackageName().toString();
            if (pkg.equals(rule.inputApp())) readBanner(root);
            return pkg;
        }
        return null;
    }

    private void readBanner(AccessibilityNodeInfo root) {
        for (AccessibilityNodeInfo n : root.findAccessibilityNodeInfosByViewId(bannerId)) {
            if (n.getText() != null) rule.sawInput(n.getText().toString());
        }
    }

    @Override
    public void onInterrupt() {
    }
}
