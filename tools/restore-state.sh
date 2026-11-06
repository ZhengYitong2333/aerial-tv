#!/usr/bin/env bash
# Shared by install.sh and restore.sh. Store the first pre-install state on the selected TV,
# not against an IP address that DHCP may later assign to a different device.
STATE_PREFIX=aerial_restore_v2_
STATE_OPS=(WRITE_SETTINGS SYSTEM_ALERT_WINDOW REQUEST_INSTALL_PACKAGES GET_USAGE_STATS android:get_usage_stats APP_AUTO_START APP_ASSOC_START AUTO_START ASSOC_START ACCESS_RESTRICTED_SETTINGS)

state_get() { adb shell settings get global "${STATE_PREFIX}$1" | tr -d '\r'; }

state_put() {
  # All saved values are component lists, app-op modes or booleans. Reject shell metacharacters
  # that could break the single-quoted argument reconstructed by adb shell.
  case "$2" in *"'"*|*$'\n'*|*$'\r'*) echo "Cannot safely save restore state: $1" >&2; return 1 ;; esac
  adb shell settings put global "${STATE_PREFIX}$1" "'$2'" >/dev/null
  if [ "$(state_get "$1")" != "$2" ]; then
    echo "Could not verify saved restore state: $1; installation stopped." >&2
    return 1
  fi
}

valid_component() { [[ "$1" =~ ^[a-zA-Z0-9_.\$]+/[a-zA-Z0-9_.\$]+$ ]]; }

same_component() {
  valid_component "$1" && valid_component "$2" || return 1
  local ap=${1%%/*} ac=${1#*/} bp=${2%%/*} bc=${2#*/}
  case "$ac" in .*) ac=$ap$ac ;; esac
  case "$bc" in .*) bc=$bp$bc ;; esac
  [ "$ap/$ac" = "$bp/$bc" ]
}

# With CATEGORY_DEFAULT, as Home itself is resolved: on XGIMI m7642 (Android 9) a query without it
# lists Settings' FallbackHome too, and Android drops the default Home over the changed set (#767).
resolve_home() {
  local value
  value=$(adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME -c android.intent.category.DEFAULT 2>/dev/null | tail -n 1 | tr -d '\r') || value=
  if ! valid_component "$value"; then
    value=$(adb shell pm resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME -c android.intent.category.DEFAULT 2>/dev/null | tail -n 1 | tr -d '\r') || value=
  fi
  valid_component "$value" && printf '%s\n' "$value"
}

snapshot_state() {
  [ "$(state_get complete)" != 1 ] || return 0
  local home screensaver dump op mode output granted key
  home=$(resolve_home) || home=@unknown
  case "$home" in "$PKG"/*|*ResolverActivity|*FallbackHome) home=@unknown ;; esac
  state_put home "$home"
  screensaver=$(adb shell settings get secure screensaver_components | tr -d '\r')
  # An older install may already own the dream; that is not a record of the owner's old choice.
  case "$screensaver" in "$PKG"/*) screensaver=@unknown ;; esac
  state_put screensaver "$screensaver"
  dump=$(adb shell dumpsys package "$PKG" 2>/dev/null) || dump=
  for key in ACCESS_FINE_LOCATION; do
    granted=false
    if printf '%s\n' "$dump" | grep -q "android.permission.$key: granted=true"; then granted=true; fi
    state_put "grant_$key" "$granted"
  done
  for op in "${STATE_OPS[@]}"; do
    output=$(adb shell appops get "$PKG" "$op" 2>&1 | tr -d '\r') || true
    mode=$(printf '%s\n' "$output" | sed -nE 's/.*: (allow|ignore|deny|default|foreground)([;[:space:]].*)?$/\1/p' | head -n 1)
    if [ -z "$mode" ]; then
      case "$output" in *'No operations'*|*'Unknown package'*|*'No UID'*) mode=default ;; *) mode=@unsupported ;; esac
    fi
    state_put "op_${op//:/_}" "$mode"
  done
  # Commit the marker last. A partial snapshot is never used as a complete backup.
  state_put complete 1
  SNAPSHOT_CREATED=1
  echo "Saved the pre-install Home, screensaver and permission state on the TV."
}

restore_appops() {
  [ "$AERIAL_INSTALLED" = 1 ] || return 0
  local op mode
  for op in "${STATE_OPS[@]}"; do
    if [ "$HAS_STATE" = 1 ]; then mode=$(state_get "op_${op//:/_}"); else mode=default; fi
    case "$mode" in
      @unsupported|null) continue ;;
      allow|ignore|deny|default|foreground) ;;
      *) echo "Invalid saved app-op mode: $op" >&2; RESTORE_ERRORS=1; continue ;;
    esac
    if ! adb shell appops set "$PKG" "$op" "$mode" >/dev/null 2>&1; then
      # Legacy installations did not save which operations the firmware supports.
      if [ "$HAS_STATE" = 1 ]; then echo "Could not restore app-op $op." >&2; RESTORE_ERRORS=1; fi
    fi
  done
}

restore_grants() {
  [ "$AERIAL_INSTALLED" = 1 ] || return 0
  local key mode
  # WRITE_SECURE_SETTINGS and the notification listener intentionally stay revoked: restoring
  # those would let an existing Aerial turn Home Guard back on after this undo operation.
  for key in ACCESS_FINE_LOCATION; do
    if [ "$HAS_STATE" = 1 ]; then mode=$(state_get "grant_$key"); else mode=false; fi
    case "$mode" in
      true) adb shell pm grant "$PKG" "android.permission.$key" >/dev/null 2>&1 || RESTORE_ERRORS=1 ;;
      false)
        if ! adb shell pm revoke "$PKG" "android.permission.$key" >/dev/null 2>&1; then
          if adb shell dumpsys package "$PKG" | grep -q "android.permission.$key: granted=true"; then
            echo "Could not revoke android.permission.$key." >&2; RESTORE_ERRORS=1
          fi
        fi ;;
      *) echo "Invalid saved permission: $key" >&2; RESTORE_ERRORS=1 ;;
    esac
  done
}

clear_restore_state() {
  local key op
  # Invalidate first, so a cleanup interruption cannot leave an apparently complete partial record.
  adb shell settings delete global "${STATE_PREFIX}complete" >/dev/null || return 1
  if [ "$(state_get complete)" != null ]; then
    echo "Home was restored, but the snapshot marker could not be cleared." >&2
    return 1
  fi
  for key in home screensaver grant_ACCESS_FINE_LOCATION; do
    adb shell settings delete global "${STATE_PREFIX}$key" >/dev/null || return 1
  done
  for op in "${STATE_OPS[@]}"; do
    adb shell settings delete global "${STATE_PREFIX}op_${op//:/_}" >/dev/null || return 1
  done
}
