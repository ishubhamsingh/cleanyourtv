package com.gathin.tvhome.backlight;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Which mode the light should be in for what is on screen. The TV's input app (Sony: com.sony.dtv.tvlin) names the
 * input in a banner for a few seconds after each switch; the last name it showed is kept, so returning to the input
 * app without a banner (after a settings panel, say) keeps its mode. Any other full-screen app is the TV's own OS.
 */
final class Rule {
    enum Mode { SYNC, OTHER }

    private final String inputApp;
    private final List<Pattern> syncInputs = new ArrayList<>();
    private String lastInput;

    Rule(String inputApp, String syncInputsCsv) {
        this.inputApp = inputApp;
        for (String s : syncInputsCsv.split(",")) {
            String t = s.trim();
            // "HDMI 1" must not match "HDMI 10": the name only as a whole word.
            if (!t.isEmpty()) syncInputs.add(Pattern.compile("(^|\\W)" + Pattern.quote(t.toLowerCase(Locale.ROOT)) + "($|\\W)"));
        }
    }

    String inputApp() {
        return inputApp;
    }

    String lastInput() {
        return lastInput;
    }

    /** The input app's banner was read. */
    void sawInput(String name) {
        if (name != null && !name.trim().isEmpty()) lastInput = name.trim();
    }

    boolean isSync(String inputName) {
        String n = inputName.toLowerCase(Locale.ROOT);
        for (Pattern p : syncInputs) if (p.matcher(n).find()) return true;
        return false;
    }

    /** The mode for the full-screen app in front; null to leave the light as it is (nothing known yet). */
    Mode decide(String frontPackage) {
        if (frontPackage == null) return null;
        if (!frontPackage.equals(inputApp)) return Mode.OTHER;
        if (lastInput == null) return null;
        return isSync(lastInput) ? Mode.SYNC : Mode.OTHER;
    }

    /** A window that covers (nearly) the whole screen: an app, not a side panel or the volume bar. */
    static boolean fullScreen(int width, int height, int screenWidth, int screenHeight) {
        return width >= screenWidth * 0.9 && height >= screenHeight * 0.9;
    }
}
