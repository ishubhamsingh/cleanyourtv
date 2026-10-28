# TV profiles

A theme sets the look; a **profile** sets what belongs to one TV model: the dock apps, the Live TV app, and the
remote's buttons (the gear button and the app keys, by scancode). `tools/theme.sh` lays a profile over a theme, the
profile's keys winning:

```bash
TV_HOST=<tv ip> tools/theme.sh themes/my-glass profiles/sony-bravia-ae2-in
```

| Profile | TV | What it sets |
|---|---|---|
| [`sony-bravia-ae2-in/`](sony-bravia-ae2-in/config.json) | Sony BRAVIA 4K AE2, India, Google TV 14 | Dock (Netflix, YouTube, JioHotstar, Apple TV), no Live TV app, the gear button (646), the six app keys (Sony Pictures Core, Netflix, YouTube, Prime Video, Crunchyroll, SonyLIV) |

## Making one for your TV

1. Copy `sony-bravia-ae2-in/` to a folder named after your TV.
2. Find your remote's scancodes: run `adb shell getevent -lt`, press the button, and read the code after
   `EV_MSC MSC_SCAN` (`000c0xxx`; the key code on the next line is the scancode, e.g. `0x1e5` = 485).
3. Write `"settings_key_scancodes"` for the gear / Settings button and `"app_keys"` as `scan:<code>:<package>`.
4. Push it with your theme as above. The remote keys need the key helper, installed with
   `HOME_KEY_SERVICE=1 launcher/build-and-install.sh`, and are tested with the real remote (injected keys skip it).

Any theme key works in a profile too (see [themes/README.md](../themes/README.md)).
