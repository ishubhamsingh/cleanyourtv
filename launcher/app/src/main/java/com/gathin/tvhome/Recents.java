package com.gathin.tvhome;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The app switcher's list, from Android's usage events: the apps that came to the front, the latest first, each
 * once. Android shows no app another app's tasks, so this is what the TV says was in front, not what is still in
 * memory. Pure logic; SwitcherActivity reads the events (UsageStatsManager).
 */
final class Recents {
    private Recents() {
    }

    /** What the switcher shows: the apps, latest first, and whether the first is the one on screen right now. */
    static final class Result {
        final List<String> apps;
        final boolean firstOnScreen;

        Result(List<String> apps, boolean firstOnScreen) {
            this.apps = apps;
            this.firstOnScreen = firstOnScreen;
        }

        /** The tile to focus: the app before the one on screen, as Alt-Tab does; the latest when Home is on screen. */
        int focus() {
            return firstOnScreen && apps.size() > 1 ? 1 : 0;
        }
    }

    /**
     * {@code pkgs}, {@code classes} and {@code times} are the ACTIVITY_RESUMED events in time order. Only
     * {@code launchable} apps count (not this launcher, not system panels); {@code self} is this launcher and
     * {@code switcher} its switcher activity, which resumes as it opens and is not "on screen". An app removed from
     * the list ({@code removed}: package to when) comes back only once it has been in front again since. The first
     * app is on screen when the last thing that came to the front before the switcher was that app, not Home.
     */
    static Result from(String[] pkgs, String[] classes, long[] times, Set<String> launchable, String self,
                       String switcher, int max, Map<String, Long> removed) {
        Set<String> apps = new LinkedHashSet<>();
        String front = null;
        boolean frontKnown = false;
        for (int i = pkgs.length - 1; i >= 0 && apps.size() < max; i--) {
            String pkg = pkgs[i];
            if (pkg == null) continue;
            if (pkg.equals(self) && switcher.equals(classes[i])) continue;
            boolean app = !pkg.equals(self) && launchable.contains(pkg);
            if (!frontKnown) {
                frontKnown = true;
                front = app ? pkg : null;
            }
            Long gone = removed.get(pkg);
            if (app && (gone == null || times[i] > gone)) apps.add(pkg);
        }
        List<String> list = new ArrayList<>(apps);
        return new Result(list, front != null && !list.isEmpty() && list.get(0).equals(front));
    }
}
