#!/usr/bin/env python3
"""Stateful ADB replacement for script tests. Never opens a socket or executes shell input."""
import json
import os
import shlex
import sys
from pathlib import Path

STATE = Path(os.environ['AERIAL_TEST_STATE'])
PKG = 'app.orchard.tv'
args = sys.argv[1:]
s = json.loads(STATE.read_text())
s['commands'].append(args)


def reply():
    if args == ['get-state']:
        return (1, 'unauthorized') if s.get('unauthorized') else (0, 'device')
    if args[0] == 'connect':
        return 0, 'connected to simulated TV'
    if args[0] == 'install':
        if s.get('install_failure'):
            return 1, 'INSTALL_FAILED_TEST'
        s['installed'] = True
        return 0, 'Success'
    if args[0] == 'uninstall':
        s['installed'] = False
        if s['home'].startswith(PKG + '/'):
            s['home'] = s['factory_home']
        return 0, 'Success'
    a = args[1:]
    if a[:1] == ['settings']:
        op, namespace, key = a[1:4]
        data = s.setdefault(namespace, {})
        if op == 'get':
            return 0, data.get(key, 'null')
        if op == 'delete':
            if key == 'aerial_restore_v2_complete' and s.get('ignore_marker_delete'):
                return 0, ''
            data.pop(key, None)
        if op == 'put':
            if key.startswith('aerial_restore_v2_') and s.get('ignore_snapshot_writes'):
                return 0, ''
            data[key] = shlex.split(a[4])[0] if a[4].startswith("'") and a[4] != "''" else ('' if a[4] == "''" else a[4])
        return 0, ''
    if a[:2] == ['dumpsys', 'package']:
        if not s['installed']:
            return 1, 'Unknown package: ' + PKG
        return 0, 'versionCode=57\n' + '\n'.join(
            'android.permission.' + p + ': granted=' + str(v).lower() for p, v in s['grants'].items())
    if a[:2] == ['dumpsys', 'notification']:
        return 0, s['secure'].get('enabled_notification_listeners', '')
    if a[:1] == ['appops']:
        op, mode_key = a[1], a[3]
        if mode_key in ('APP_AUTO_START', 'APP_ASSOC_START', 'AUTO_START', 'ASSOC_START'):
            return 1, 'Error: Unknown operation'
        key = 'GET_USAGE_STATS' if mode_key == 'android:get_usage_stats' else mode_key
        if op == 'get':
            if not s['installed']:
                return 1, 'Unknown package: ' + PKG
            return 0, mode_key + ': ' + s['appops'].get(key, 'default')
        if s.get('restoring') and key == s.get('fail_restore_op'):
            return 1, 'simulated app-op failure'
        s['appops'][key] = a[4]
        return 0, ''
    if a[:2] in (['pm', 'grant'], ['pm', 'revoke']):
        s['grants'][a[3].rsplit('.', 1)[-1]] = a[1] == 'grant'
        return 0, ''
    if a[:2] == ['pm', 'path']:
        return (0, 'package:/data/app/Aerial.apk') if s['installed'] and a[2] == PKG else (1, '')
    if a[:2] == ['am', 'broadcast']:
        if 'keep_adb' in a and a[a.index('keep_adb') + 1] == 'false':
            s['restoring'] = True
        return 0, 'Broadcast completed: result=0'
    if a[:2] == ['am', 'start']:
        return 0, 'Status: ok'
    if a[:2] == ['cmd', 'notification']:
        key = 'enabled_notification_listeners'
        component = a[3]
        entries = s['secure'].get(key, '').split(':')
        entries = [v for v in entries if v and v != component]
        if a[2] == 'allow_listener':
            entries.append(component)
        s['secure'][key] = ':'.join(entries)
        return 0, ''
    if a[:2] == ['cmd', 'role']:
        if not s.get('role_supported'):
            return 20, "Can't find service: role"
        if not s.get('inert_home'):
            if a[2] == 'remove-role-holder' and s['home'].startswith(a[-1] + '/'):
                s['home'] = s['factory_home']
            if a[2] == 'add-role-holder':
                s['home'] = a[-1] + ('/.MainActivity' if a[-1] == PKG else '/.Home')
        return 0, ''
    if a[:3] == ['cmd', 'package', 'resolve-activity'] or a[:2] == ['pm', 'resolve-activity']:
        if s.get('full_component'):
            package, name = s['home'].split('/', 1)
            return 0, package + '/' + (package + name if name.startswith('.') else name)
        return 0, s['home']
    if a[:3] == ['cmd', 'package', 'set-home-activity'] or a[:2] == ['pm', 'set-home-activity']:
        if not s.get('inert_home'):
            s['home'] = a[-1]
        return 0, 'Success'
    if a[:1] == ['getprop']:
        return 0, ''
    return 0, ''


code, output = reply()
STATE.write_text(json.dumps(s))
if output:
    print(output)
sys.exit(code)
