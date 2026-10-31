# Backlight

Your TV backlight's HDMI sync box, from the TV. **Automatic**: Sync when the TV is on the input that goes through
the box (a console, an Apple TV), and your own look everywhere else, where sync has nothing to follow (Home, the
TV's own apps); it switches by itself 2 to 3 seconds after the input changes, including when a console turns the TV
over to its input over HDMI-CEC. **A controller** in the dashboard's glass for everything the phone app does.
**Album colours**: while music plays, the strip follows the launcher's aurora background. Made for Tuya-based sync
boxes, the ones set up in the **Smart Life** or **Tuya Smart** app; tested with an LDD11 MAX. For the
[Clean Your TV](../../README.md) launcher it declares a [module card](../../docs/modules.md); on any other Android TV
home it is an ordinary app.

![The Backlight controller](screenshot.jpg)

## The controller

| Row | What it does |
|---|---|
| **Light**, **Automatic**, **Album colours** | The strip on or off; the input switching on or off; album colours on or off. |
| **Sync** | Low, Medium or High diffusion. Automatic uses the one picked here on the sync input. |
| **Scenes** | Calm and the box's ten scenes (Rainbow, Fire, Fireworks, Star, Rain, Atom, Smooth, Bounce, Kinetic, Breathe). |
| **Calm and colours** | Calm's five whites, warmest to coolest, then eight colours. |
| **Brightness** | ◀ ▶ in 5 % steps; it applies to every mode. |

Whatever you pick here (except Sync) becomes **the look away from the sync input**: pick Fire, and Home and the TV's
apps get Fire from then on. The default is Calm at its warmest. The glow behind the panel takes the light's colour,
and the line under the title says what is on.

## Album colours

Only for music: a music app (Spotify, YouTube Music, Apple Music, Amazon Music, JioSaavn, Wynk, Gaana, Tidal,
Deezer, SoundCloud) counts whether it is in front or behind; any other app only while it plays behind another one,
since video apps play only in front (Netflix and YouTube send a poster or thumbnail as artwork too). Apps can also
say what they play, but on Google TV 14 every one says "unknown".

While music plays away from the sync input, the strip shows what the launcher's
`my-aurora` background shows: the cover made into four colours (one per quarter, made vivid, a little apart in hue
so a cover in one colour still gives four), drifting as clouds on 90-second paths, crossfading over 2 seconds when
the track changes. The box takes one colour at a time, so the strip follows **the screen's overall tint**: the
aurora at 40 points, their hues averaged round the colour wheel, weighted by how vivid each is. Both use the
system's clock, so with the aurora on screen the strip matches it.

A screen colour sent as it is looks washed out on an LED, and a blue with a little green in it shows cyan: each
colour is decoded (gamma 2.2) and its green toned down before it goes to the box. A new colour goes out every
150 ms, on one connection kept open while the music plays. When the music stops, your look comes back.

## What it watches, and how it talks to the box

- **Local only.** The box on your home network (Tuya's local protocol 3.4, TCP 6668), never Tuya's cloud or the
  internet. If the box gets a new address, it is found again from the box's own announcements (UDP 6667).
- **The input.** An accessibility service sees which full-screen app is in front and, in the TV's input app, the
  input name the TV shows for a few seconds after a switch ("HDMI 4"). Side panels (picture settings) and the volume
  bar don't count. It reads nothing else: no other text, no keys.
- **The music.** A notification listener, because only one may see other apps' media sessions: what plays and its
  cover. No notification is read.
- **Gently.** The box has few connection slots and **leaks them when connections come and go quickly** (it then
  refuses everyone, the phone app included, until it is unplugged for a few seconds). So one connection is kept,
  with a heartbeat every 10 s, until a minute passes without a command, and a burst (the brightness bar, the album
  colours) goes over it with only the latest values. A command that gets no answer is tried once more on a fresh
  connection; an Automatic switch again after 10 s and 60 s.

### What the box takes

Watched on the network while tapping the Smart Life app:

| Data point | What | Values |
|---|---|---|
| 20 | Power | `true`, `false` |
| 21 | Mode | `scene`, `white`, `colour` |
| 22 | Brightness, every mode | 10 to 1000 |
| 23 | Calm's white | 0 (warmest) to 1000 (coolest), the app's swatches at 0, 250, 500, 750, 1000 |
| 24 | A colour | `hhhhssssvvvv`: hue 0-360, saturation and value 0-1000, in hex |
| 25 | Sync and scenes | the first byte, then 30 zeros: `80` `81` `82` Sync Low, Medium, High; `86` Rainbow, `87` Fire, `88` Calm, `89` Fireworks, `90` Star, `91` Rain, `92` Atom, `93` Smooth, `94` Bounce, `95` Kinetic, `96` Breathe, `97` Color, `98` to `9D` Music |

A white shows only inside the Calm scene and a colour only inside the Color scene, so those looks send `25` first,
then the mode, then the value; and every look names its mode, or a box left on a colour ignores a new scene.

## Set up

### 1. Get your box's key

The box only obeys commands signed with its *local key*, which Tuya keeps in its cloud. You need it once:

1. Sign up at [platform.tuya.com](https://platform.tuya.com) (free).
2. *Cloud → Development → Create Cloud Project*: industry **Smart Home**, development method **Smart Home**, and the
   **data center of your Smart Life account** (India, Central Europe, Western America, ...). Keep the default API
   services and *Authorize*.
3. In the project, *Devices → Link App Account → Add App Account*, and scan the QR code with Smart Life
   (*Me → the scan icon at the top right*). Your box appears in the project's device list.
4. From the project's *Overview*, copy the **Access ID** and **Access Secret**, then let tinytuya read the key:
   ```bash
   tools/venv/bin/pip install tinytuya
   tools/venv/bin/python -m tinytuya wizard      # asks for the Access ID, Secret, and region (in, eu, us, cn)
   ```
   `devices.json` now lists your box with its `id` and `key`. The wizard keeps the Secret in `tinytuya.json`; delete
   both files afterwards (or reset the Secret on the Tuya page): nothing here needs the cloud again.

### 2. Fill in backlight.properties

```bash
cp addons/backlight/backlight.properties.example addons/backlight/backlight.properties
```

| Key | What | Default |
|---|---|---|
| `device_id` | The box's `id` from `devices.json` (also *Device Information → Virtual ID* in Smart Life) | |
| `local_key` | The box's `key`, 16 characters | |
| `ip` | Optional: the box's address. Empty: found on the network | |
| `sync_inputs` | Inputs that mean Sync, as the TV names them in its input banner, comma-separated; a whole-word match, so `HDMI 1` never matches `HDMI 10` | `HDMI 4` |
| `input_app` | The TV's app for HDMI inputs | `com.sony.dtv.tvlin` (Sony) |
| `sync_value` | Sync's starting diffusion (the controller changes it) | `82…` (High) |

Git ignores `backlight.properties`: the key never leaves your computer except inside the app you build.

### 3. Build and install

```bash
TV_HOST=<tv ip> addons/backlight/build-and-install.sh
```

It builds the app, installs it, switches on its input watcher (keeping any other accessibility services, the
launcher's Home button for one, on) and grants its media listener. `--uninstall` takes it off again.

### Another box

Another Tuya box may number its scenes differently. Watch its data points while you tap the phone app (needs the
device ID, key and the box's address), then change the codes in `Looks.java`:

```python
import tinytuya
d = tinytuya.Device("<device_id>", "<box ip>", "<local_key>", version=3.4, persist=True)
print(d.status())
while True:
    r = d.receive()
    if r and r.get("dps"): print(r["dps"])
```

Close the phone app first: the box may have only one slot free.

### Another TV

On a TV that isn't a Sony, find its input app and the banner's view id while an HDMI input is on screen:
`adb shell dumpsys window | grep mCurrentFocus` names the app (`input_app`), and
`adb shell uiautomator dump` shows the input name's `resource-id`. The watcher looks for `<input_app>:id/input_name`;
another name needs a one-line change in `InputWatcher.java`.

## Troubleshooting

- **"failed: the box didn't answer", and the phone app can't reach it either:** the box has run out of connection
  slots. Unplug its power for 10 seconds.
- **The phone app is open at home and the TV can't reach the box:** close the phone app; the box may take only one
  local connection at a time.

## Permissions

| Permission | Why |
|---|---|
| `INTERNET` | Your sync box on the home network: TCP 6668 to set it, UDP 6667 to hear its announcement. Nothing goes to the internet. |
| `CHANGE_WIFI_MULTICAST_STATE` | Wi-Fi drops broadcasts in power save: held only while looking for the box, at most 12 seconds. |
| Accessibility service (switched on by `build-and-install.sh`) | Which full-screen app is in front, and the input name in the TV's input app. Nothing else is read. |
| Notification listener (granted by `build-and-install.sh`) | Album colours: what plays and its cover, from media sessions. No notification is read. |

No background service of its own: the accessibility service and the media listener run only while Android has them
on, and act only when the app in front or the music changes.

## Tested on

Sony BRAVIA 4K AE2 (55", Google TV 14) with an LDD11 MAX sync box (Tuya, protocol 3.4), an Apple TV and a PS5 on
HDMI 4 through the box. The Outfit typeface is under the SIL Open Font License (`FONT-LICENSE-OFL.txt`).
