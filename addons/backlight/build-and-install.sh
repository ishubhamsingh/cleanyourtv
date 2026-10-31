#!/usr/bin/env bash
# Build Backlight, install it on the TV and switch on its input watcher (an accessibility service that only sees
# which app is in front and the TV input's name), keeping any other accessibility services on, and its media
# listener (album colours: the playing cover, no notifications).
#
# Usage: TV_HOST=192.168.1.50 addons/backlight/build-and-install.sh
#        TV_HOST=192.168.1.50 addons/backlight/build-and-install.sh --uninstall
# Needs addons/backlight/backlight.properties (copy backlight.properties.example; README: "Get your box's key").
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
TOOLS="$HERE/../../tools"
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0}"
PY="$TOOLS/venv/bin/python"
[ -x "$PY" ] || PY="$TOOLS/venv/Scripts/python.exe"
[ -x "$PY" ] || { echo "run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt" >&2; exit 1; }
export MSYS_NO_PATHCONV=1
PKG=com.gathin.tvhome.backlight
SVC=$PKG/$PKG.InputWatcher
tv() { "$PY" -I "$TOOLS/tv.py" "$@"; }

if [ "${1:-}" = --uninstall ]; then
  # Take the service out of the enabled list, keeping any others, then remove the app.
  tv "s=\$(settings get secure enabled_accessibility_services); s=\$(echo \":\$s:\" | sed 's#:$SVC:#:#; s#^:##; s#:\$##'); if [ -z \"\$s\" ]; then settings delete secure enabled_accessibility_services; else settings put secure enabled_accessibility_services \$s; fi; pm uninstall $PKG"
  exit 0
fi

[ -f "$HERE/backlight.properties" ] || { echo "no backlight.properties: copy backlight.properties.example and add your box's device ID and local key" >&2; exit 1; }
(cd "$HERE" && ./gradlew assembleRelease -q) || { echo "build failed (JDK 17 and Android SDK 34 needed; see launcher/README)" >&2; exit 1; }
"$PY" -I "$TOOLS/b64push.py" "$HERE/app/build/outputs/apk/release/app-release.apk" /data/local/tmp/backlight.apk >/dev/null
tv "pm install -r /data/local/tmp/backlight.apk; rm -f /data/local/tmp/backlight.apk"
# Add the service to the enabled list without switching off the ones already there. An update restarts the app,
# which drops its service from Android's bound list: re-writing the list binds it again.
tv "s=\$(settings get secure enabled_accessibility_services); case :\$s: in *:$SVC:*) settings put secure enabled_accessibility_services ''; sleep 1; settings put secure enabled_accessibility_services \$s;; *) if [ -z \$s ] || [ \$s = null ]; then s=$SVC; else s=\$s:$SVC; fi; settings put secure enabled_accessibility_services \$s;; esac; settings put secure accessibility_enabled 1"
# Album colours read the playing cover from media sessions, which Android shows only to a notification listener.
tv "cmd notification allow_listener $PKG/$PKG.MediaListener"
echo "Installed. Switch to your sync input and back to Home: the light follows. Open Backlight for the controls."
