package app.orchard.tv.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModelStore
import app.orchard.tv.HomeViewModel
import app.orchard.tv.MainActivity
import app.orchard.tv.Overlay
import app.orchard.tv.settings.AerialSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import org.robolectric.util.ReflectionHelpers
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SettingsPinTest {
    private val app = RuntimeEnvironment.getApplication()
    private val service = ComponentName("com.android.newsettings", "com.xgimi.newsettings.SettingService")
    private val vm = HomeViewModel(app)
    private val store = ViewModelStore().apply { put("test", vm) }

    @Before
    fun setup() {
        AerialSettings.init(app)
        shadowOf(app.packageManager).addServiceIfNotPresent(service).exported = true
        ShadowSystemClock.advanceBy(Duration.ofSeconds(1))
    }

    @After
    fun cleanup() {
        store.clear()
    }

    @Test
    fun xgimiSettingsPackageIsProtectedWithoutAnActionSettingsActivity() {
        assertTrue(SystemProbe.isSettingsApp(app, service.packageName))
    }

    @Test
    fun settingsNetworkAndPictureServicesWaitForPinBeforeOpening() {
        assertTrue(SystemProbe.openPictureSettings(app))
        val picture = shadowOf(app).nextStartedService
        val intents = listOf(SystemProbe.settingsIntent(app), SystemProbe.networkSettingsIntents(app).first(), picture)
        val activity = activity()
        for (intent in intents) {
            lock()
            open(activity, intent)
            assertNull(shadowOf(app).nextStartedService)
            val pin = vm.overlay as Overlay.Pin
            assertTrue(AerialSettings.unlock("1234"))
            pin.onDone()
            assertEquals(intent.getStringExtra("data"), shadowOf(app).nextStartedService.getStringExtra("data"))
        }
    }

    @Test
    fun inputSelectionRemainsUsableWhileSettingsAreLocked() {
        assertTrue(SystemProbe.openInputPicker(app))
        val input = shadowOf(app).nextStartedService
        lock()
        open(activity(), input)
        assertEquals(Overlay.None, vm.overlay)
        assertEquals(input.getStringExtra("data"), shadowOf(app).nextStartedService.getStringExtra("data"))
    }

    @Test
    fun activityBasedSettingsAlsoRequirePin() {
        val component = ComponentName("com.android.settings", "com.android.settings.Settings")
        shadowOf(app.packageManager).addActivityIfNotPresent(component)
        lock()
        open(activity(), Intent(Intent.ACTION_MAIN).setComponent(component))
        assertTrue(vm.overlay is Overlay.Pin)
        assertNull(shadowOf(app).nextStartedActivity)
    }

    private fun lock() {
        AerialSettings.choosePin("1234")
        AerialSettings.relock()
        assertTrue(AerialSettings.locked)
    }

    private fun activity(): MainActivity = Robolectric.buildActivity(MainActivity::class.java).get().also {
        ReflectionHelpers.setField(it, "vm\$delegate", lazyOf(vm))
    }

    private fun open(activity: MainActivity, intent: Intent) {
        MainActivity::class.java.getDeclaredMethod("open", Intent::class.java).apply {
            isAccessible = true
        }.invoke(activity, intent)
    }
}
