package app.orchard.tv.system

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.net.Uri
import android.provider.Settings
import app.orchard.tv.MainActivity
import app.orchard.tv.R
import app.orchard.tv.apps.AppEntry
import app.orchard.tv.apps.TileArt
import app.orchard.tv.settings.AerialSettings
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowToast
import org.robolectric.util.ReflectionHelpers

/** The network page Control Center opens first; the rest are tried if it closes at once. */
private fun networkFirst(context: Context) = SystemProbe.networkSettingsIntents(context).first()

// Legacy SDK behavior is the subject of these compatibility tests.
@Suppress("DEPRECATION")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], shadows = [InputManagerShadow::class])
class XgimiSettingsTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val pm = shadowOf(context.packageManager)
    private val service = ComponentName("com.android.newsettings", "com.xgimi.newsettings.SettingService")

    @Before
    fun provideTvInputService() {
        InputManagerShadow.inputs = emptyList()
        val base = (context as ContextWrapper).baseContext
        org.robolectric.shadow.api.Shadow.extract<org.robolectric.shadows.ShadowContextImpl>(base)
            .setSystemService(Context.TV_INPUT_SERVICE, ReflectionHelpers.newInstance(TvInputManager::class.java))
    }

    @Test
    fun settingsSelectsExportedServiceWithoutAnyActivity() {
        pm.addServiceIfNotPresent(service).exported = true
        assertService(SystemProbe.settingsIntent(context), "Settings://com.xgimi.misckey.main/mainPage")
    }

    @Test
    fun networkSelectsExportedServiceWithoutAnyActivity() {
        pm.addServiceIfNotPresent(service).exported = true
        assertService(networkFirst(context), "Settings://com.xgimi.settings.net/wifi_misckey")
    }

    @Test
    fun absentServicePreservesEverySettingsCandidateAndItsPriority() {
        assertLegacyCandidates(settingsCandidates(), SystemProbe::settingsIntent)
    }

    @Test
    fun absentServicePreservesEveryNetworkCandidateAndItsPriority() {
        assertLegacyCandidates(networkCandidates(), ::networkFirst)
    }

    @Test
    fun unexportedServicePreservesActivityFallbacks() {
        pm.addServiceIfNotPresent(service).exported = false
        assertTrue(settingsCandidates().last().filterEquals(SystemProbe.settingsIntent(context)))
        assertTrue(networkCandidates().last().filterEquals(networkFirst(context)))
    }

    @Test
    fun serviceTakesPriorityOverResolvableActivitiesAndStartsAsService() {
        pm.addServiceIfNotPresent(service).exported = true
        val settings = settingsCandidates().first()
        val network = networkCandidates().first()
        pm.addActivityIfNotPresent(settings.component!!).exported = true
        pm.addActivityIfNotPresent(network.component!!).exported = true
        val caller = RecordingContext()
        SystemProbe.startSettingsIntent(caller, SystemProbe.settingsIntent(caller))
        SystemProbe.startSettingsIntent(caller, networkFirst(caller))
        assertEquals(2, caller.services.size)
        assertService(caller.services[0], "Settings://com.xgimi.misckey.main/mainPage")
        assertService(caller.services[1], "Settings://com.xgimi.settings.net/wifi_misckey")
        assertTrue(caller.activities.isEmpty())
    }

    @Test
    fun failedServiceFallsBackToTheOriginalActivityForEachEntry() {
        pm.addServiceIfNotPresent(service).exported = true
        val settings = settingsCandidates().first()
        val network = networkCandidates().first()
        pm.addActivityIfNotPresent(settings.component!!).exported = true
        pm.addActivityIfNotPresent(network.component!!).exported = true
        for (throws in listOf(false, true)) {
            val caller = RecordingContext(serviceResult = null, serviceThrows = throws)
            SystemProbe.startSettingsIntent(caller, SystemProbe.settingsIntent(caller))
            SystemProbe.startSettingsIntent(caller, networkFirst(caller))
            assertEquals(2, caller.services.size)
            assertEquals(2, caller.activities.size)
            assertTrue(settings.filterEquals(caller.activities[0]))
            assertTrue(network.filterEquals(caller.activities[1]))
        }
    }

    @Test
    fun absentServiceStartsTheOriginalActivity() {
        val caller = RecordingContext()
        SystemProbe.startSettingsIntent(caller, SystemProbe.settingsIntent(caller))
        SystemProbe.startSettingsIntent(caller, networkFirst(caller))
        assertTrue(caller.services.isEmpty())
        assertEquals(2, caller.activities.size)
        assertTrue(settingsCandidates().last().filterEquals(caller.activities[0]))
        assertTrue(networkCandidates().last().filterEquals(caller.activities[1]))
    }

    @Test
    fun mainActivityStartsSettingsAndNetworkAsServicesWithoutUnavailableToast() {
        pm.addServiceIfNotPresent(service).exported = true
        shadowOf(RuntimeEnvironment.getApplication()).checkActivities(true)
        val activity = Robolectric.buildActivity(MainActivity::class.java).get()
        for ((select, page) in listOf(
            SystemProbe::settingsIntent to "Settings://com.xgimi.misckey.main/mainPage",
            ::networkFirst to "Settings://com.xgimi.settings.net/wifi_misckey",
        )) {
            open(activity, select(activity))
            assertEquals(0, ShadowToast.shownToastCount())
            val started = shadowOf(activity).nextStartedService
            assertNotNull("MainActivity must start the settings service", started)
            assertService(started, page)
            assertNull(shadowOf(activity).nextStartedActivity)
        }
    }

    @Test
    fun mainActivityShowsUnavailableToastWhenServiceAndActivityBothFail() {
        pm.addServiceIfNotPresent(service).exported = true
        shadowOf(RuntimeEnvironment.getApplication()).checkActivities(true)
        val activity = Robolectric.buildActivity(MainActivity::class.java).get()
        val caller = RecordingContext(serviceThrows = true)
        ReflectionHelpers.setField(activity, "mBase", caller)
        listOf(SystemProbe::settingsIntent, ::networkFirst).forEachIndexed { index, select ->
            open(activity, select(activity))
            assertEquals(index + 1, caller.services.size)
            assertEquals(index + 1, ShadowToast.shownToastCount())
            assertEquals(context.getString(R.string.unavailable), ShadowToast.getTextOfLatestToast())
            assertNull(shadowOf(activity).nextStartedActivity)
        }
    }

    @Test
    fun pictureSettingsOpensXgimiServiceInsteadOfLegacyActivity() {
        pm.addServiceIfNotPresent(service).exported = true
        val settings = settingsCandidates().first()
        pm.addActivityIfNotPresent(settings.component!!).exported = true
        val application = shadowOf(RuntimeEnvironment.getApplication())
        application.checkActivities(true)
        assertTrue(SystemProbe.openPictureSettings(context))
        assertService(application.nextStartedService, "Settings://com.xgimi.settings.brightness/brightness_misckey")
        assertNull(application.nextStartedActivity)
    }

    @Test
    fun pictureServiceFailurePreservesLegacyFallback() {
        pm.addServiceIfNotPresent(service).exported = true
        val settings = settingsCandidates().first()
        pm.addActivityIfNotPresent(settings.component!!).exported = true
        for (throws in listOf(false, true)) {
            val caller = RecordingContext(serviceResult = null, serviceThrows = throws)
            assertTrue(SystemProbe.openPictureSettings(caller))
            assertService(caller.services.single(), "Settings://com.xgimi.settings.brightness/brightness_misckey")
            assertTrue(settings.filterEquals(caller.activities.single()))
        }
    }

    @Test
    fun xgimiBrightnessNeverWritesTheGenericScreenSetting() {
        pm.addServiceIfNotPresent(service).exported = true
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        shadowOf(ops).setMode(android.app.AppOpsManager.OPSTR_WRITE_SETTINGS,
            android.os.Process.myUid(), context.packageName, android.app.AppOpsManager.MODE_ALLOWED)
        assertTrue(Settings.System.canWrite(context))
        Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 100)
        val hal = com.xgimi.gmpf.api.DisplayManager.getInstance()
        hal.unavailable = true
        try {
            assertNull(Brightness.level(context))
            assertNull(Brightness.adjust(context, 1))
        } finally {
            hal.unavailable = false
        }
        assertEquals(100, Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS))
    }

    @Test
    fun disabledAndPrivateServicesDoNotOverrideGenericBrightnessOrInputs() {
        val info = pm.addServiceIfNotPresent(service)
        info.exported = true
        info.enabled = false
        assertFalse(SystemProbe.hasXgimiSettings(context))
        assertFalse(SystemProbe.openInputPicker(context))
        info.enabled = true
        info.exported = false
        assertFalse(SystemProbe.hasXgimiSettings(context))
        assertFalse(SystemProbe.openInputPicker(context))
    }

    @Test
    fun serviceRequiringAnUngrantedPermissionPreservesActivityFallback() {
        val info = pm.addServiceIfNotPresent(service)
        info.exported = true
        info.permission = "test.permission.PRIVATE_SETTINGS"
        assertFalse(SystemProbe.hasXgimiSettings(context))
        val caller = RecordingContext()
        SystemProbe.startSettingsIntent(caller, SystemProbe.settingsIntent(caller))
        assertTrue(caller.services.isEmpty())
        assertEquals(1, caller.activities.size)
    }

    @Test
    fun inputsOpenNativeServiceWithoutFrameworkInputs() {
        pm.addServiceIfNotPresent(service).exported = true
        val caller = RecordingContext()
        assertTrue(SystemProbe.openInputPicker(caller))
        assertService(caller.services.single(), "Settings://com.xgimi.settings.signalSource/hdmi_misckey")
        assertTrue(caller.activities.isEmpty())
    }

    @Test
    fun listedHdmiRoutesToTheServiceEvenWhenPassthroughResolves() {
        pm.addServiceIfNotPresent(service).exported = true
        InputManagerShadow.inputs = listOf(hdmi())
        try {
            val generic = Intent(Intent.ACTION_VIEW, android.media.tv.TvContract.buildChannelUriForPassthroughInput("test/HW5"))
            val player = pm.addActivityIfNotPresent(ComponentName("test.player", "test.player.Player"))
            pm.addResolveInfoForIntent(generic, android.content.pm.ResolveInfo().apply { activityInfo = player })
            val source = SystemProbe.sources(context).single()
            assertEquals("HDMI 1", source.label)
            assertEquals("test/HW5", source.id)
            assertService(source.intent, "Settings://com.xgimi.settings.signalSource/hdmi_misckey")
            val activity = Robolectric.buildActivity(MainActivity::class.java).get()
            open(activity, source.intent)
            assertService(shadowOf(activity).nextStartedService, "Settings://com.xgimi.settings.signalSource/hdmi_misckey")
        } finally {
            InputManagerShadow.inputs = emptyList()
        }
    }

    @Test
    fun nonXgimiHdmiRetainsItsFrameworkRoute() {
        InputManagerShadow.inputs = listOf(hdmi())
        try {
            val source = SystemProbe.sources(context).single()
            assertEquals(Intent.ACTION_VIEW, source.intent.action)
            assertEquals(android.media.tv.TvContract.buildChannelUriForPassthroughInput(source.id), source.intent.data)
        } finally {
            InputManagerShadow.inputs = emptyList()
        }
    }

    @Test
    fun pinnedInputLaunchUsesServiceDispatcher() {
        pm.addServiceIfNotPresent(service).exported = true
        val caller = RecordingContext()
        SystemProbe.openInputPicker(caller)
        val source = TvSource("hdmi", "HDMI", SourceKind.Hdmi, true, null, caller.services.single())
        val image = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).asImageBitmap()
        val entry = AppEntry("input:hdmi", "HDMI", ComponentName("input", "hdmi"), false, false,
            TileArt(image, null, Rect.Zero, Color.Black, image), source = source)
        AerialSettings.init(context)
        val activity = Robolectric.buildActivity(MainActivity::class.java).get()
        MainActivity::class.java.getDeclaredMethod("launch", AppEntry::class.java, Rect::class.java).apply {
            isAccessible = true
        }.invoke(activity, entry, null)
        assertService(shadowOf(activity).nextStartedService, "Settings://com.xgimi.settings.signalSource/hdmi_misckey")
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun failedInputServiceReportsUnavailableInsteadOfOpeningGeneralSettings() {
        pm.addServiceIfNotPresent(service).exported = true
        val selection = RecordingContext()
        SystemProbe.openInputPicker(selection)
        val activity = Robolectric.buildActivity(MainActivity::class.java).get()
        val denied = RecordingContext(serviceThrows = true)
        ReflectionHelpers.setField(activity, "mBase", denied)
        open(activity, selection.services.single())
        assertEquals(context.getString(R.string.unavailable), ShadowToast.getTextOfLatestToast())
        assertTrue(denied.activities.isEmpty())
    }

    private fun hdmi(): TvInputInfo = ReflectionHelpers.newInstance(TvInputInfo::class.java).apply {
        ReflectionHelpers.setField(this, "mId", "test/HW5")
        ReflectionHelpers.setField(this, "mType", TvInputInfo.TYPE_HDMI)
        ReflectionHelpers.setField(this, "mLabel", "HDMI 1")
    }

    @Test
    @Config(sdk = [34])
    fun newGmuiHdmiSwitchTakesPriorityOverTheLegacyPicker() {
        pm.addServiceIfNotPresent(service).apply {
            exported = true
            enabled = true
            applicationInfo.enabled = true
        }
        pm.addIntentFilterForService(service, android.content.IntentFilter("com.xgimi.settings.SETTINGS"))
        assertTrue(SystemProbe.hasXgimiSettings(context))
        val player = ComponentName("com.xgimi.xhplayer", "com.xgimi.xhplayer.InputActivity")
        pm.addActivityIfNotPresent(player).apply {
            exported = true
            enabled = true
            applicationInfo.enabled = true
        }
        val input = hdmi().apply {
            ReflectionHelpers.setField(this, "mId", "com.xgimi.tvinput/.inputservice.Hdmi2TvInputService/HW6")
        }
        InputManagerShadow.inputs = listOf(input)
        val source = SystemProbe.inputsToCheck(context).single()
        assertEquals(player, source.intent.component)
        assertEquals("HDMI2", source.intent.getStringExtra("sw"))
        assertFalse(SystemProbe.listedInputsNeedVendorPage(listOf(source)))
    }

    @Test
    @Config(sdk = [34])
    fun unavailableNewGmuiPlayerFallsBackToTheLegacyPicker() {
        pm.addServiceIfNotPresent(service).apply {
            exported = true
            enabled = true
            applicationInfo.enabled = true
        }
        pm.addIntentFilterForService(service, android.content.IntentFilter("com.xgimi.settings.SETTINGS"))
        assertTrue(SystemProbe.hasXgimiSettings(context))
        val player = ComponentName("com.xgimi.xhplayer", "com.xgimi.xhplayer.InputActivity")
        pm.addActivityIfNotPresent(player).apply { exported = true; enabled = false }
        val input = hdmi().apply {
            ReflectionHelpers.setField(this, "mId", "com.xgimi.tvinput/.inputservice.Hdmi2TvInputService/HW6")
        }
        InputManagerShadow.inputs = listOf(input)
        assertService(SystemProbe.inputsToCheck(context).single().intent, "Settings://com.xgimi.settings.signalSource/hdmi_misckey")
    }

    @Test
    @Config(sdk = [34])
    fun listedPlayerDisappearingFallsBackFromControlCenter() {
        val intent = listedGmuiInput()
        pm.removeActivity(intent.component!!)
        shadowOf(RuntimeEnvironment.getApplication()).checkActivities(true)
        val activity = Robolectric.buildActivity(MainActivity::class.java).get()
        open(activity, intent)
        assertService(shadowOf(activity).nextStartedService, "Settings://com.xgimi.settings.signalSource/hdmi_misckey")
        assertEquals(0, ShadowToast.shownToastCount())
    }

    @Test
    @Config(sdk = [34], shadows = [RejectingActivityShadow::class])
    fun rejectedPlayerFallsBackOnceFromControlCenterAndPinnedTile() {
        val intent = listedGmuiInput()
        AerialSettings.init(context)
        for (pinned in listOf(false, true)) {
            val caller = RecordingContext()
            val activity = Robolectric.buildActivity(MainActivity::class.java).get()
            ReflectionHelpers.setField(activity, "mBase", caller)
            if (pinned) {
                val source = TvSource("hdmi", "HDMI", SourceKind.Hdmi, true, null, intent)
                val image = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).asImageBitmap()
                val entry = AppEntry("input:hdmi", "HDMI", ComponentName("input", "hdmi"), false, false,
                    TileArt(image, null, Rect.Zero, Color.Black, image), source = source)
                MainActivity::class.java.getDeclaredMethod("launch", AppEntry::class.java, Rect::class.java).apply {
                    isAccessible = true
                }.invoke(activity, entry, null)
            } else open(activity, intent)
            assertEquals("Do not retry the rejected player", 1, rejectedStarts(activity))
            assertService(caller.services.single(), "Settings://com.xgimi.settings.signalSource/hdmi_misckey")
        }
        assertEquals(0, ShadowToast.shownToastCount())
    }

    @Test
    @Config(sdk = [34], shadows = [RejectingActivityShadow::class])
    fun rejectedPlayerAndPickerShowUnavailable() {
        val intent = listedGmuiInput()
        val caller = RecordingContext(serviceThrows = true)
        val activity = Robolectric.buildActivity(MainActivity::class.java).get()
        ReflectionHelpers.setField(activity, "mBase", caller)
        open(activity, intent)
        assertEquals(1, rejectedStarts(activity))
        assertEquals(1, caller.services.size)
        assertEquals(context.getString(R.string.unavailable), ShadowToast.getTextOfLatestToast())
    }

    @Test
    @Config(sdk = [34], shadows = [RejectingActivityShadow::class])
    fun unrelatedFailedActivityDoesNotOpenInputPicker() {
        val input = listedGmuiInput()
        val caller = RecordingContext()
        val activity = Robolectric.buildActivity(MainActivity::class.java).get()
        ReflectionHelpers.setField(activity, "mBase", caller)
        // Neither the player's component alone nor its action on another app identifies HDMI.
        for (intent in listOf(Intent(input).setAction(Intent.ACTION_MAIN),
            Intent(input).setComponent(ComponentName("other.app", "other.app.Player")))) {
            open(activity, intent)
        }
        assertTrue(caller.services.isEmpty())
        assertEquals(2, rejectedStarts(activity))
    }

    private fun rejectedStarts(activity: MainActivity): Int =
        org.robolectric.shadow.api.Shadow.extract<RejectingActivityShadow>(activity).attempts

    private fun listedGmuiInput(): Intent {
        pm.addServiceIfNotPresent(service).apply { exported = true; enabled = true; applicationInfo.enabled = true }
        pm.addIntentFilterForService(service, android.content.IntentFilter("com.xgimi.settings.SETTINGS"))
        val player = ComponentName("com.xgimi.xhplayer", "com.xgimi.xhplayer.InputActivity")
        pm.addActivityIfNotPresent(player).apply { exported = true; enabled = true; applicationInfo.enabled = true }
        InputManagerShadow.inputs = listOf(hdmi().apply {
            ReflectionHelpers.setField(this, "mId", "com.xgimi.tvinput/.inputservice.Hdmi2TvInputService/HW6")
        })
        return SystemProbe.inputsToCheck(context).single().intent.also { assertEquals(player, it.component) }
    }

    private fun open(activity: MainActivity, intent: Intent) {
        MainActivity::class.java.getDeclaredMethod("open", Intent::class.java).apply {
            isAccessible = true
        }.invoke(activity, intent)
    }

    private inner class RecordingContext(
        val serviceResult: ComponentName? = service,
        val serviceThrows: Boolean = false,
    ) : ContextWrapper(context) {
        val services = mutableListOf<Intent>()
        val activities = mutableListOf<Intent>()

        override fun startService(intent: Intent): ComponentName? {
            services += intent
            if (serviceThrows) throw SecurityException("Service access denied")
            return serviceResult
        }

        override fun startActivity(intent: Intent) {
            activities += intent
        }

    }

    private fun assertService(intent: Intent, page: String) {
        assertEquals(service, intent.component)
        assertEquals("com.xgimi.settings.SETTINGS", intent.action)
        assertEquals(page, intent.getStringExtra("data"))
        assertEquals("com.xgimi.misckey", intent.getStringExtra("packageName"))
        assertTrue(intent.hasExtra("unlocked"))
        assertEquals(false, intent.getBooleanExtra("unlocked", true))
        assertNull(intent.data)
    }

    private fun assertLegacyCandidates(candidates: List<Intent>, select: (Context) -> Intent) {
        val components = candidates.mapIndexed { index, intent ->
            val component = intent.component ?: ComponentName("test.settings", "test.settings.Page$index")
            pm.addActivityIfNotPresent(component).exported = true
            if (intent.component == null) {
                val filter = android.content.IntentFilter(intent.action).apply {
                    intent.data?.let { uri ->
                        addDataScheme(uri.scheme)
                        addDataAuthority(uri.host, null)
                        addDataPath(uri.path, android.os.PatternMatcher.PATTERN_LITERAL)
                    }
                }
                pm.addIntentFilterForActivity(component, filter)
            }
            component
        }
        candidates.forEachIndexed { index, expected ->
            val actual = select(context)
            assertTrue("Candidate $index: $actual", expected.filterEquals(actual))
            assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, actual.flags)
            assertNull(actual.extras)
            pm.removeActivity(components[index])
        }
        assertTrue(candidates.last().filterEquals(select(context)))
    }

    private fun settingsCandidates() = listOf(
        explicit("com.xiaomi.mitv.settings.SETTINGS", "com.xiaomi.mitv.settings", "com.xiaomi.mitv.settings.entry.MainActivity"),
        Intent(Settings.ACTION_SETTINGS),
        explicit(Intent.ACTION_MAIN, "com.tcl.settings", "com.tcl.settings.ui.MainActivity"),
        explicit(Intent.ACTION_MAIN, "com.pptv.setting", "com.pptv.setting.root.SettingsActivity"),
        Intent(Intent.ACTION_VIEW, Uri.parse("Settings://com.xgimi.misckey.main/mainPage")),
        explicit(Intent.ACTION_MAIN, "com.helios.setting", "com.helios.activity.StartActivity"),
    )

    private fun networkCandidates() = listOf(
        explicit("com.xiaomi.mitv.settings.WIFI_SETTINGS", "com.xiaomi.mitv.settings", "com.xiaomi.mitv.settings.wifi.MitvWifiActivity"),
        Intent(Settings.ACTION_WIFI_SETTINGS),
        explicit("com.pptv.intent.action.WIFI_ACTIVITY", "com.pptv.setting", "com.pptv.setting.wifi.WifiWorkActivity"),
        Intent(Intent.ACTION_VIEW, Uri.parse("Settings://com.xgimi.settings.net/wifi_misckey")),
        Intent(Settings.ACTION_SETTINGS),
        explicit(Intent.ACTION_MAIN, "com.tcl.settings", "com.tcl.settings.ui.MainActivity"),
        explicit(Intent.ACTION_MAIN, "com.helios.setting", "com.helios.activity.StartActivity"),
    )

    private fun explicit(action: String, pkg: String, name: String) =
        Intent(action).setComponent(ComponentName(pkg, name))
}

@Implements(TvInputManager::class)
class InputManagerShadow {
    companion object {
        var inputs: List<TvInputInfo> = emptyList()
    }

    @Implementation
    protected fun getTvInputList(): List<TvInputInfo> = inputs

    @Implementation
    protected fun getInputState(id: String): Int = TvInputManager.INPUT_STATE_CONNECTED
}

/** Simulates firmware rejecting Activity starts after PackageManager accepted the component. */
@Implements(android.app.Activity::class)
class RejectingActivityShadow : org.robolectric.shadows.ShadowActivity() {
    var attempts = 0
        private set

    @Implementation
    protected fun startActivity(intent: Intent) {
        startActivity(intent, null)
    }

    @Implementation
    protected fun startActivity(intent: Intent, options: android.os.Bundle?) {
        attempts++
        throw SecurityException("Firmware rejected activity start")
    }
}
