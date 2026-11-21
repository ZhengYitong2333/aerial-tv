#!/usr/bin/env bash
# Give Home back to the TV's own launcher.
#
#   TV=<tv-ip> tools/restore.sh              # stop taking over Home, keep Aerial installed
#   TV=<tv-ip> tools/restore.sh --uninstall  # and remove Aerial (and Aerial Lite)
# If you disabled the stock launcher by hand, also: adb shell pm enable <its package>
set -euo pipefail
cd "$(dirname "$0")/.."
: "${TV:?set TV to the TV IP address}"
PKG=app.orchard.tv
GUARD="$PKG/$PKG.guard.HomeGuardService"
case "${1:-}" in ""|--uninstall) ;; *) echo "Usage: TV=<ip> tools/restore.sh [--uninstall]" >&2; exit 1 ;; esac
case "$TV" in *:*) SERIAL="$TV" ;; *) SERIAL="$TV:5555" ;; esac
export ANDROID_SERIAL="$SERIAL"
adb connect "$SERIAL" >/dev/null
if [ "$(adb get-state 2>/dev/null)" != device ]; then
  echo "ADB is not connected and authorized; nothing was restored." >&2
  exit 1
fi
source tools/restore-state.sh
if [ -n "${RESTORE_HOME:-}" ] && ! valid_component "$RESTORE_HOME"; then
  echo "RESTORE_HOME must be a verified package/activity component." >&2
  exit 1
fi
AERIAL_INSTALLED=0
if adb shell pm path "$PKG" 2>/dev/null | grep -q '^package:'; then AERIAL_INSTALLED=1; fi
HAS_STATE=$(state_get complete)
RESTORE_ERRORS=0
HOME_BEFORE=$(resolve_home) || HOME_BEFORE=
SAVED_HOME=${RESTORE_HOME:-}
EXPECTED_HOME=
if [ -z "$SAVED_HOME" ] && [ "$HAS_STATE" = 1 ]; then SAVED_HOME=$(state_get home); fi
if [ "$HAS_STATE" != 1 ]; then
  echo "No pre-install snapshot: permissions use legacy defaults; the original custom screensaver is unknown." >&2
fi

# First take away what lets Aerial switch the guard back on, or it would simply do so.
adb shell am broadcast -a "$PKG.CONFIG" -n "$PKG/.guard.ConfigReceiver" --ez keep_adb false --ez own_screensaver false >/dev/null 2>&1 || true
adb shell cmd notification disallow_listener "$PKG/$PKG.guard.RevivalListener" >/dev/null 2>&1 || true
# Android 6 and 7 have no `cmd`: take it out of the setting itself, leaving the other listeners.
LISTENERS=$(adb shell settings get secure enabled_notification_listeners 2>/dev/null | tr -d '\r')
case "$LISTENERS" in
  *"$PKG/$PKG.guard.RevivalListener"*)
    REST=$(printf '%s' "$LISTENERS" | tr ':' '\n' | grep -v "^$PKG/" | paste -sd: - || true)
    if [ -n "$REST" ]; then adb shell settings put secure enabled_notification_listeners "$REST" >/dev/null 2>&1 || true
    else adb shell settings delete secure enabled_notification_listeners >/dev/null 2>&1 || true; fi ;;
esac
adb shell pm revoke "$PKG" android.permission.WRITE_SECURE_SETTINGS >/dev/null 2>&1 || true
if [ "$AERIAL_INSTALLED" = 1 ] && adb shell dumpsys package "$PKG" | grep -q 'android.permission.WRITE_SECURE_SETTINGS: granted=true'; then
  echo "Could not revoke Home Guard's settings permission." >&2
  RESTORE_ERRORS=1
fi
restore_appops
adb shell am force-stop "$PKG"
case "$(adb shell settings get secure screensaver_components | tr -d '\r')" in
  "$PKG"/*)
    if [ "$HAS_STATE" = 1 ]; then
      DREAM=$(state_get screensaver)
      if [ "$DREAM" = @unknown ]; then
        echo "The previous install already used Aerial's screensaver; restoring the firmware default, not a known owner preference." >&2
        DREAM=$(adb shell settings get secure screensaver_default_component | tr -d '\r')
      fi
    else
      DREAM=$(adb shell settings get secure screensaver_default_component | tr -d '\r')
    fi
    case "$DREAM" in
      null) adb shell settings delete secure screensaver_components >/dev/null ;;
      "$PKG"/*) echo "No non-Aerial screensaver fallback is known; snapshot retained." >&2; RESTORE_ERRORS=1 ;;
      *"'"*|*$'\n'*) echo "Invalid saved screensaver." >&2; RESTORE_ERRORS=1 ;;
      *) adb shell settings put secure screensaver_components "'$DREAM'" >/dev/null ;;
    esac ;;
esac

CURRENT=$(adb shell settings get secure enabled_accessibility_services | tr -d '\r')
REST=$(printf '%s' "$CURRENT" | tr ':' '\n' | grep -F -v -x "$GUARD" | grep -F -v -x "$PKG/.guard.HomeGuardService" | paste -sd: - || true)
if [ -z "$REST" ] || [ "$REST" = null ]; then
  adb shell settings delete secure enabled_accessibility_services >/dev/null
else
  adb shell settings put secure enabled_accessibility_services "'$REST'"
fi
# TCL: put back what the TV opened at power-on before install.sh made it Aerial.
BOOT_APP=$(adb shell settings get global aerial_boot_app_componentname | tr -d '\r')
if [ -n "$BOOT_APP" ] && [ "$BOOT_APP" != null ]; then
  BOOT_MODE=$(adb shell settings get global aerial_boot_app_mode | tr -d '\r')
  for pair in "boot_app_componentname:$BOOT_APP" "boot_app_mode:$BOOT_MODE"; do
    key=${pair%%:*} value=${pair#*:}
    case "$value" in none|null|"") adb shell settings put system "$key" "''" >/dev/null ;;
      *) adb shell settings put system "$key" "$value" >/dev/null ;; esac
  done
  adb shell settings delete global aerial_boot_app_componentname >/dev/null
  adb shell settings delete global aerial_boot_app_mode >/dev/null
elif [ "$(adb shell settings get system boot_app_componentname | tr -d '\r')" = "$PKG/.MainActivity" ]; then
  adb shell settings put system boot_app_componentname "''" >/dev/null
fi
# Hisense VIDAA: put back the launcher install.sh replaced in persist.vt.use_home_pkg.
VT_HOME=$(adb shell settings get global aerial_vt_home_pkg | tr -d '\r')
if [ "$VT_HOME" = - ]; then
  # It was empty before (#1679): empty again, which is the firmware's own launcher.
  adb shell "setprop persist.vt.use_home_pkg ''"
  adb shell settings delete global aerial_vt_home_pkg >/dev/null
elif [ -n "$VT_HOME" ] && [ "$VT_HOME" != null ]; then
  adb shell setprop persist.vt.use_home_pkg "$VT_HOME"
  adb shell settings delete global aerial_vt_home_pkg >/dev/null
elif [ "$(adb shell getprop persist.vt.use_home_pkg | tr -d '\r')" = "$PKG" ] &&
     adb shell pm path com.jamdeo.tv.vod 2>/dev/null | grep -q package:; then
  # Set by hand before install.sh kept the old value (1.15 and earlier, issue #40): VIDAA's
  # launcher is 聚好看 on every VIDAA build reported so far.
  adb shell setprop persist.vt.use_home_pkg com.jamdeo.tv.vod
fi
adb shell cmd role remove-role-holder --user 0 android.app.role.HOME "$PKG" >/dev/null 2>&1 || true
# Xiaomi where an older install made Aerial the default Home and the firmware took it: PatchWall then
# runs outside the home stack and the short press of the power key no longer sleeps the TV (#848).
# Home goes back to PatchWall; the fix takes a reboot.
if adb shell pm path com.mitv.tvhome 2>/dev/null | grep -q package: &&
   adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME -c android.intent.category.DEFAULT 2>/dev/null | tail -n 1 | grep -q "^$PKG/"; then
  adb shell cmd package set-home-activity --user 0 com.mitv.tvhome/.MainActivityUserMode >/dev/null 2>&1 &&
    echo "Xiaomi: Home is PatchWall again; restart the TV once."
fi

# Aerial Lite (Android 4.x) takes over only as the home app the owner chose; nothing else to undo.
LITE=app.orchard.tv.lite
LITE_HERE=$(adb shell pm path "$LITE" 2>/dev/null | grep -c package: || true)
# Do not replace a different launcher the owner selected after installing Aerial.
case "$HOME_BEFORE" in
  "$PKG"/*|""|*ResolverActivity|*FallbackHome)
    if valid_component "$SAVED_HOME" && [[ "$SAVED_HOME" != "$PKG/"* ]]; then
      EXPECTED_HOME=$SAVED_HOME
      adb shell cmd role add-role-holder --user 0 android.app.role.HOME "${SAVED_HOME%%/*}" >/dev/null 2>&1 || true
      if ! same_component "$(resolve_home || true)" "$SAVED_HOME"; then
        adb shell cmd package set-home-activity --user 0 "$SAVED_HOME" >/dev/null 2>&1 \
          || adb shell pm set-home-activity "$SAVED_HOME" >/dev/null 2>&1 || true
      fi
    fi ;;
esac
restore_grants

if [ "${1:-}" = --uninstall ]; then
  adb shell pm path "$PKG" 2>/dev/null | grep -q package: && adb uninstall "$PKG"
  [ "$LITE_HERE" = 0 ] || adb uninstall "$LITE"
elif [ "$LITE_HERE" != 0 ]; then
  echo "Aerial Lite stays the home screen while it is installed and chosen. Run with --uninstall, or on the TV: Settings > Apps > Aerial Lite > Clear defaults, then press Home and pick the TV's launcher."
fi
HOME_NOW=$(resolve_home) || HOME_NOW=
if [ -n "$EXPECTED_HOME" ] && ! same_component "$HOME_NOW" "$EXPECTED_HOME"; then
  echo "Restore incomplete: expected $EXPECTED_HOME, but Home resolves to ${HOME_NOW:-an unknown component}. Snapshot retained." >&2
  exit 1
fi
case "$HOME_NOW" in
  "$PKG"/*|""|*FallbackHome|*ResolverActivity)
    echo "Restore incomplete: Home still resolves to ${HOME_NOW:-an unknown component}." >&2
    echo "Snapshot retained. For legacy installs, set RESTORE_HOME to the verified original launcher component." >&2
    exit 1 ;;
esac
if [ "$RESTORE_ERRORS" != 0 ]; then
  echo "Home now: $HOME_NOW; some permissions could not be restored. Snapshot retained." >&2
  exit 1
fi
adb shell input keyevent KEYCODE_HOME
if [ "$HAS_STATE" = 1 ]; then clear_restore_state; fi
echo "Home now: $HOME_NOW"
