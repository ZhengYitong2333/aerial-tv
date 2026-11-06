#!/usr/bin/env python3
"""Exercise the real installer/restorer with an isolated, stateful fake Android shell."""
import copy
import json
import os
from pathlib import Path
import shlex
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
PREFIX = 'aerial_restore_v2_'
PKG = 'app.orchard.tv'


class RestoreTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='aerial-restore-test-')
        self.addCleanup(self.temp.cleanup)
        folder = Path(self.temp.name)
        self.file = folder / 'tv.json'
        self.original = {
            'home': 'vendor.launcher/.CustomHome', 'factory_home': 'vendor.factory/.Home',
            'installed': True, 'global': {}, 'commands': [],
            'secure': {'screensaver_components': 'owner.dream/.CustomDream', 'screensaver_enabled': '1',
                       'screensaver_default_component': 'vendor.dream/.Default',
                       'enabled_accessibility_services': 'vendor.voice/.Assistant',
                       'enabled_notification_listeners': 'owner.notifications/.Listener'},
            'appops': {'WRITE_SETTINGS': 'ignore', 'SYSTEM_ALERT_WINDOW': 'foreground',
                       'GET_USAGE_STATS': 'deny', 'REQUEST_INSTALL_PACKAGES': 'default'},
            'grants': {'ACCESS_FINE_LOCATION': True, 'WRITE_SECURE_SETTINGS': False},
        }
        self.save(copy.deepcopy(self.original))
        adb = folder / 'adb'
        adb.write_text('#!/bin/sh\nexec ' + shlex.quote(sys.executable) + ' ' +
                       shlex.quote(str(Path(__file__).with_name('fake_adb.py'))) + ' "$@"\n')
        adb.chmod(0o755)
        apk = folder / 'test.apk'
        apk.write_bytes(b'not a real APK; fake adb never opens it')
        self.env = dict(os.environ, PATH=str(folder) + os.pathsep + os.environ['PATH'],
                        TV='simulated-tv', APK_FILE=str(apk), BUILD='0', AERIAL_TEST_STATE=str(self.file))

    def load(self):
        return json.loads(self.file.read_text())

    def save(self, state):
        self.file.write_text(json.dumps(state))

    def run_script(self, name, *args, expected=0):
        result = subprocess.run(['bash', str(ROOT / 'tools' / name), *args], env=self.env,
                                capture_output=True, text=True, timeout=30)
        if expected == 0:
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        else:
            self.assertNotEqual(0, result.returncode, result.stdout + result.stderr)
        return result

    def install(self):
        self.run_script('install.sh')
        self.assertEqual(PKG + '/.MainActivity', self.load()['home'])

    def test_android9_restores_original_home_custom_dream_and_appops(self):
        self.install()
        state = self.load()
        state['secure']['enabled_accessibility_services'] += ':' + PKG + '/' + PKG + '.guard.HomeGuardService'
        self.save(state)
        self.run_script('restore.sh')
        state = self.load()
        self.assertEqual(self.original['home'], state['home'])
        self.assertEqual(self.original['secure']['screensaver_components'], state['secure']['screensaver_components'])
        self.assertEqual(self.original['appops'], state['appops'])
        self.assertEqual('vendor.voice/.Assistant', state['secure']['enabled_accessibility_services'])
        self.assertEqual('owner.notifications/.Listener', state['secure']['enabled_notification_listeners'])
        self.assertEqual(self.original['grants'], state['grants'])
        self.assertFalse(any(k.startswith(PREFIX) for k in state['global']))

    def test_reinstall_preserves_first_snapshot(self):
        self.install()
        first = self.load()['global'].copy()
        self.install()
        self.assertEqual(first, self.load()['global'])
        self.run_script('restore.sh')
        self.assertEqual(self.original['home'], self.load()['home'])

    def test_successful_but_ineffective_home_command_is_a_failure(self):
        self.install()
        state = self.load(); state['inert_home'] = True; self.save(state)
        result = self.run_script('restore.sh', expected=1)
        self.assertIn('Restore incomplete', result.stderr)
        self.assertEqual('1', self.load()['global'][PREFIX + 'complete'])
        self.assertFalse(any(c[:3] == ['shell', 'input', 'keyevent'] for c in self.load()['commands']))

    def test_legacy_role_failure_does_not_report_success(self):
        state = self.load(); state['home'] = PKG + '/.MainActivity'; self.save(state)
        result = self.run_script('restore.sh', expected=1)
        self.assertIn('No pre-install snapshot', result.stderr)
        self.assertIn('Restore incomplete', result.stderr)

    def test_legacy_install_accepts_verified_explicit_home(self):
        state = self.load(); state['home'] = PKG + '/.MainActivity'; self.save(state)
        self.env['RESTORE_HOME'] = self.original['home']
        self.run_script('restore.sh')
        self.assertEqual(self.original['home'], self.load()['home'])

    def test_android_role_support_still_restores_custom_component(self):
        state = self.load(); state['role_supported'] = True; self.save(state)
        self.install()
        self.run_script('restore.sh')
        self.assertEqual(self.original['home'], self.load()['home'])

    def test_absent_screensaver_is_deleted_on_restore(self):
        state = self.load(); del state['secure']['screensaver_components']; self.save(state)
        self.install()
        self.run_script('restore.sh')
        self.assertNotIn('screensaver_components', self.load()['secure'])

    def test_owners_new_home_and_screensaver_are_not_replaced(self):
        self.install()
        state = self.load(); state['home'] = 'owner.new/.Home'
        state['secure']['screensaver_components'] = 'owner.new/.Dream'; self.save(state)
        self.run_script('restore.sh')
        self.assertEqual('owner.new/.Home', self.load()['home'])
        self.assertEqual('owner.new/.Dream', self.load()['secure']['screensaver_components'])

    def test_permission_restore_failure_keeps_snapshot(self):
        self.install()
        state = self.load(); state['fail_restore_op'] = 'WRITE_SETTINGS'; self.save(state)
        self.run_script('restore.sh', expected=1)
        self.assertEqual('1', self.load()['global'][PREFIX + 'complete'])

    def test_unauthorized_restore_does_not_change_settings(self):
        state = self.load(); state['unauthorized'] = True; self.save(state)
        self.run_script('restore.sh', expected=1)
        self.assertEqual(state['secure'], self.load()['secure'])
        self.assertEqual({}, self.load()['global'])

    def test_failed_install_discards_unused_snapshot(self):
        state = self.load(); state['install_failure'] = True; self.save(state)
        self.run_script('install.sh', expected=1)
        self.assertFalse(any(k.startswith(PREFIX) for k in self.load()['global']))
        self.assertEqual(self.original['home'], self.load()['home'])

    def test_full_and_short_component_names_compare_as_the_same_home(self):
        state = self.load(); state['home'] = PKG + '/.MainActivity'; state['full_component'] = True
        self.save(state); self.env['RESTORE_HOME'] = self.original['home']
        self.run_script('restore.sh')
        self.assertEqual(self.original['home'], self.load()['home'])

    def test_first_install_restores_default_appops_and_revokes_new_permissions(self):
        state = self.load(); state['installed'] = False; state['appops'] = {}
        state['grants']['ACCESS_FINE_LOCATION'] = False; self.save(state)
        self.install()
        self.run_script('restore.sh')
        self.assertTrue(all(mode == 'default' for mode in self.load()['appops'].values()))
        self.assertFalse(self.load()['grants']['ACCESS_FINE_LOCATION'])
        self.assertFalse(self.load()['grants']['WRITE_SECURE_SETTINGS'])

    def test_guard_self_revival_permission_stays_revoked_even_if_previously_granted(self):
        state = self.load(); state['grants']['WRITE_SECURE_SETTINGS'] = True; self.save(state)
        self.install()
        self.run_script('restore.sh')
        self.assertFalse(self.load()['grants']['WRITE_SECURE_SETTINGS'])

    def test_explicit_uninstall_restores_home_and_then_removes_aerial(self):
        self.install()
        self.run_script('restore.sh', '--uninstall')
        self.assertFalse(self.load()['installed'])
        self.assertEqual(self.original['home'], self.load()['home'])

    def test_removed_app_does_not_block_restoring_the_saved_screensaver(self):
        self.install()
        state = self.load(); state['installed'] = False; state['home'] = self.original['home']; self.save(state)
        self.run_script('restore.sh')
        self.assertEqual(self.original['secure']['screensaver_components'], self.load()['secure']['screensaver_components'])

    def test_hidden_home_resolver_is_not_reported_as_a_restored_launcher(self):
        state = self.load(); state['home'] = 'vendor.framework/.ResolverActivity'; self.save(state)
        self.run_script('restore.sh', expected=1)

    def test_saved_home_is_restored_from_a_hidden_resolver(self):
        self.install()
        state = self.load(); state['home'] = 'vendor.framework/.ResolverActivity'; self.save(state)
        self.run_script('restore.sh')
        self.assertEqual(self.original['home'], self.load()['home'])

    def test_previous_aerial_dream_is_not_mistaken_for_the_owners_original(self):
        state = self.load(); state['secure']['screensaver_components'] = PKG + '/.dream.AerialDream'; self.save(state)
        self.install()
        self.assertEqual('@unknown', self.load()['global'][PREFIX + 'screensaver'])
        result = self.run_script('restore.sh')
        self.assertIn('not a known owner preference', result.stderr)
        self.assertEqual('vendor.dream/.Default', self.load()['secure']['screensaver_components'])

    def test_silently_ignored_snapshot_write_stops_before_installation(self):
        state = self.load(); state['ignore_snapshot_writes'] = True; self.save(state)
        result = self.run_script('install.sh', expected=1)
        self.assertIn('Could not verify saved restore state', result.stderr)
        self.assertFalse(any(c[0] == 'install' for c in self.load()['commands']))
        self.assertEqual(self.original['home'], self.load()['home'])

    def test_silently_ignored_marker_cleanup_does_not_leave_a_partial_snapshot(self):
        self.install()
        snapshot = self.load()['global'].copy()
        state = self.load(); state['ignore_marker_delete'] = True; self.save(state)
        self.run_script('restore.sh', expected=1)
        self.assertEqual(snapshot, self.load()['global'])
        self.assertEqual(self.original['home'], self.load()['home'])


if __name__ == '__main__':
    unittest.main()
