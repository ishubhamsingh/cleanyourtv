---
name: setup-tv
description: Set up this launcher on an Android TV / Google TV end to end - connect over ADB, check the TV can carry it (RAM, Android version), debloat safely, build and install the launcher, pick a theme and TV profile, grant Now playing and Screen time, and optionally set up Discover (TMDB, Watchmode, TMDB account) and Weather. Use when someone asks to set up, install or configure Clean Your TV (dashboard edition) on their TV.
---

# Set up Clean Your TV (dashboard edition) on a TV

Work on the real TV, one step at a time, and check each step with a screenshot
(`TV_HOST=<ip> tools/venv/bin/python -I tools/tv.py --screenshot shots/NN.png`). Ask before anything that depends on
the person's preferences. **[PROMPT.md](../../../PROMPT.md) holds the safety rules for debloating: read it before
step 4 and follow it there; nothing below overrides it.** Never commit `discover.properties`, keys, IPs or
screenshots with names or cities in them.

## 0. Before you start

Ask for: the TV's make and model, and its IP (Settings → Network) if they know it. Tell them to enable
*Developer options* (Settings → System → About → press *Android TV OS build* 7 times) and turn on
**USB debugging** and **Wireless debugging**. Make the tools' venv:
`python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt`.

Then ask one checklist question up front, so you skip what they don't want instead of asking at each step:
- **Discover** (posters of what's on their streaming apps): needs free TMDB and, optionally, Watchmode accounts
  (about 10 minutes of sign-ups). No: skip step 8 entirely; nothing of it shows on the TV.
- **Weather** card (no account). No: skip step 9.
- **Dashboard cards**: Now playing, Weather, System, Screen time - all, some or none (none keeps the clean header
  and dock). Note what they pick for step 7.
- Their **screen size** in inches, for step 7's columns check.

## 1. Connect

- Port 5555 first: `TV_HOST=<ip> tools/venv/bin/python -I tools/tv.py --probe`.
- Refused (Google TV 14+): `adb mdns services` shows `_adb-tls-connect`. Ask them for the code and port under
  *Wireless debugging → Pair device with pairing code*, then `adb pair <ip>:<port> <code>`,
  `adb connect <ip>:<connect port>`, `adb tcpip 5555`. Port 5555 closes on every reboot; repeat `adb tcpip 5555`.
- Tell them when the "Allow debugging?" prompt will appear on the TV.

## 2. Check the TV can carry it

```bash
TV_HOST=<ip> tools/venv/bin/python -I tools/tv.py 'getprop ro.build.version.sdk; getprop ro.product.model; grep MemTotal /proc/meminfo; wm size'
```

| Check | Then |
|---|---|
| SDK below 26 (Android 7 or older) | **Stop.** The launcher needs Android 8. Say so; don't lower `minSdk`. |
| MemTotal under 1 GB (< 1,000,000 kB) | **Warn clearly:** the dashboard, Discover and the aurora background are too heavy here. Recommend upstream's plain look: skip step 7's dashboard themes (use `themes/glass`), skip Discover. Let them decide. |
| 1 to 2 GB | Fine, but recommend `themes/my-glass` (wallpapers) over `my-aurora` (animated), and say Discover's posters use some memory. |
| 2 GB or more | Everything. |
| Samsung Tizen, LG webOS, Fire TV | Not supported: stop and say so. |

Note the screen size: `wm size` 3840x2160 with an override of 1920x1080 is normal.

## 3. Back up

Package list, disabled list, `getprop` and settings into `backup/` (git-ignored), as PROMPT.md asks.

## 4. Debloat (PROMPT.md rules)

- Look for their model in `examples/` (`ls examples | grep -i <brand>`). If a list matches, read its README and
  `debloat.txt` with them; otherwise build one as PROMPT.md describes.
- One checklist question for everything the family might use (streaming apps, assistant, Chromecast, the updater).
- Disable in small batches: `TV_HOST=<ip> tools/disable.sh <list>`; screenshot Home, an HDMI input and the remote
  after each. Disable, never uninstall. Undo is `tools/restore.sh <list>`.
- Keep the Home guard: the stock launcher goes only after ours is Home (step 5 does this).

## 5. Build and install the launcher

Needs JDK 17+ and the Android SDK (platform 34); the script finds them and says what's missing.

```bash
TV_HOST=<ip> HOME_KEY_SERVICE=1 launcher/build-and-install.sh
```

`HOME_KEY_SERVICE=1` installs the key helper the remote's app and gear buttons need. Confirm
"Home now resolves to com.gathin.tvhome/.MainActivity", press Home and screenshot.

## 6. Grant Now playing and Screen time

Both are read-only. Explain that to them before running:

```bash
TV_HOST=<ip> tools/venv/bin/python -I tools/tv.py 'cmd notification allow_listener com.gathin.tvhome/com.gathin.tvhome.NowPlayingListener; appops set com.gathin.tvhome GET_USAGE_STATS allow'
```

Now playing reads media sessions (never notifications); Screen time reads app usage, kept on the TV.

## 7. Theme and TV profile

- Ask: landscapes (`themes/my-glass`) or colours from the album art (`themes/my-aurora`)? (Under 2 GB: my-glass.)
- **Profile** (dock, Live TV app, remote buttons): if `profiles/` has their TV, use it. Otherwise make one
  (`profiles/README.md`): ask them to press each app button and the gear button while you watch
  `adb shell getevent -lt` (the scancode is the `EV_MSC MSC_SCAN` value's low bits, e.g. `000c01e5` → 485), and
  write `app_keys` (`scan:<code>:<package>`) and `settings_key_scancodes`. Ask which apps go in the dock.
- **Cards they didn't want:** put `"dashboard_cards": [...]` (from `"now_playing"`, `"weather"`, `"system"`,
  `"screen_time"`; `[]` for none) in their profile. They can change it later on the TV: hold OK on the gear →
  Launcher settings → Dashboard cards.
- **Columns:** the themes use `"columns": "auto"`, which works the screen size out from the panel's reported density.
  Check it holds for this TV: `adb shell dumpsys display | grep -m1 -o 'xDpi=[0-9.]*'` with the resolution from
  `real W x H`; diagonal = sqrt((W/xdpi)^2 + (H/xdpi)^2). If that is within ~3 inches of what they told you, leave
  auto. If it's off, or the dpi is exactly 120/160/213/240/320/480/640 (the launcher then falls back to 6), put
  `"columns"` in their profile instead: 4 under 40 inches, 5 to 50, 6 to 65, 7 above.
- Push: `TV_HOST=<ip> tools/theme.sh themes/my-glass profiles/<their-tv>`.
- Remote buttons: ask them to press each **real** button; injected keys skip the helper.

## 8. Discover (optional): TMDB and Watchmode

Only if they said yes in step 0 (without the add-on the launcher shows no posters and no search, and leaves no gap).
They can also hide it later without uninstalling: Launcher settings → Discover: Off. Walk them through it; they type their keys
themselves, and you write them only into `addons/discover/discover.properties` (git-ignored, `chmod 600`).

1. **TMDB key (free):**
   1. Sign up at https://www.themoviedb.org/signup and confirm the email.
   2. Avatar → *Settings* → *API* → *Create* → *Developer*; accept the terms.
   3. The form: type *Personal*, app name e.g. "My TV", URL can be `http://localhost`, a short summary
      ("home screen recommendations on my TV").
   4. Copy the **API Key** (v3, 32 characters), not the long Read Access Token.
2. **Watchmode key (free, optional):** sign up at https://api.watchmode.com → the key is on the dashboard. Without it
   OK opens the app's home instead of the title. Free tier: 2,500 lookups a month (about 2 per title, cached a month).
3. Ask their **country** (two letters, e.g. IN, US, GB) and preferred language (e.g. en-IN). Write:
   ```properties
   tmdb_key=...
   watchmode_key=...
   region=IN
   language=en-IN
   ```
4. **Optional, TMDB account (cloud backup of their taste):** with their key in `K`:
   ```bash
   curl -s "https://api.themoviedb.org/3/authentication/token/new?api_key=$K"
   ```
   Give them `https://www.themoviedb.org/authenticate/<request_token>` to open and **Approve**, wait for them, then
   ```bash
   curl -s -X POST -H "Content-Type: application/json" -d '{"request_token":"<token>"}' "https://api.themoviedb.org/3/authentication/session/new?api_key=$K"
   curl -s "https://api.themoviedb.org/3/account?api_key=$K&session_id=<session_id>"
   ```
   Add `tmdb_session=` and `tmdb_account=` (the account `id`). Tell them it can edit their ratings; they can revoke it
   in TMDB settings.
5. Build and install (same machine as the launcher: same signing key):
   ```bash
   (cd addons/discover && ./gradlew assembleRelease)
   TV_HOST=<ip> tools/venv/bin/python -I tools/tv.py --push addons/discover/app/build/outputs/apk/release/app-release.apk /data/local/tmp/discover.apk
   TV_HOST=<ip> tools/venv/bin/python -I tools/tv.py 'pm install -r /data/local/tmp/discover.apk; rm -f /data/local/tmp/discover.apk'
   ```
6. Press Home; the rows fill within a minute. Check `git status` shows no `discover.properties`.

## 9. Weather (optional)

Only if they said yes in step 0 and kept the Weather card.

Build and install `addons/weather` the same way (`./gradlew assembleRelease`, push, `pm install -r`). Ask them to open
**Weather** on the TV once and pick their city. The dashboard card appears on its own (Open-Meteo, no key).

## 10. Check, then close up

- Screenshot Home: cards, dock, Discover; scroll down to All apps and back (the cards fold and unfold).
- Play music on Spotify or YouTube Music: the Now playing card fills; leave the remote for 3 minutes (themes set
  `home_idle_clock_minutes`) and the clock screensaver shows the track.
- **Close ADB:** ask them to turn off USB debugging and Wireless debugging (Developer options), as PROMPT.md asks.
  Tell them how to undo: `tools/restore.sh <list>` and `tools/theme.sh --reset`.
- Summarise what was done, what was skipped and why, and where their keys live.
