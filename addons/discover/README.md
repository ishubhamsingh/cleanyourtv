# Discover

Search, trending and recommendations on the home screen, while the launcher itself stays offline.

Discover is an extension with no icon and no screen of its own. It asks [TMDB](https://www.themoviedb.org) what
is trending, what is new and what streams where in your country, keeps the answers on the TV, and serves them to
the launcher through a content provider. The launcher draws everything (its `discover` key):

- **Rows under the dock:** For you, Trending on your apps, Popular on the app you use most, and New on your apps.
  Only titles on a streaming app installed on the TV are shown. Focusing a poster fills the cinematic shelf with
  its artwork, name, genre, year and app.
- **Search:** a search pill in the top bar. Your apps match as you type; movies and shows follow, the ones on
  your apps first, with where the others stream.
- **OK** opens the title in its app. Where the app takes a search from another app, it lands on that title.
  **Hold OK** for *More like this* and *Not interested*.
- **For you** learns from what you open, like and hide, on the TV only. Opens count for three weeks or so, likes
  three times as much; a hidden title never comes back.

It cannot show Google TV's *Continue watching*: the apps share that with Google's own home screen only.

## Permissions

| Permission | Why |
|---|---|
| `INTERNET` | TMDB: trending, new releases, recommendations, search, where-to-watch and the artwork. |
| `com.gathin.tvhome.permission.DISCOVER` (declares it) | Signature level: only the launcher built with the same signing key can read the provider. |

What is sent: your search words and TMDB ids of titles, with your TMDB key. No account, no telemetry, no ads.
No background service: the extension starts when the launcher asks it something, and rebuilds its rows at most
every four hours, or after a like or a hide. Everything it learns (opens, likes, hidden titles, how much each
app is used) stays in its own storage on the TV (backups are off).

## Build and install

You need your own free TMDB key: [themoviedb.org](https://www.themoviedb.org/signup), then
*Settings → API*, and copy the *API Read Access Token*.

```bash
cd addons/discover
cp discover.properties.example discover.properties   # git ignores it: put your key and country in it
./gradlew assembleRelease
TV_HOST=<tv ip> ../../tools/venv/bin/python -I ../../tools/b64push.py app/build/outputs/apk/release/app-release.apk /data/local/tmp/discover.apk
TV_HOST=<tv ip> ../../tools/venv/bin/python -I ../../tools/tv.py 'pm install -r /data/local/tmp/discover.apk; rm -f /data/local/tmp/discover.apk'
```

Build it on the same machine as the launcher: both are signed with that machine's debug key, which is what the
signature permission checks. Then set `"discover": true` in a theme (or `discover` in `config.xml`) and press Home.

## Tested on

Sony BRAVIA 4K AE2, Google TV 14 (UKN4.250422.001), India.
