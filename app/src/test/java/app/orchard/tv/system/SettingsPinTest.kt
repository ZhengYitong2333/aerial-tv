package app.orchard.tv.system

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import app.orchard.tv.apps.AppEntry
import app.orchard.tv.apps.TileArt
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

    @Test
    fun fengosSettingsIntentAndTileWaitForPinWhenDefaultSettingsAreAosp() {
        val pm = shadowOf(app.packageManager)
        val component = ComponentName("cn.formovie.fengos.settings", "cn.formovie.fengos.settings.ui.activity.MainActivity")
        pm.addActivityIfNotPresent(component).apply { exported = true; enabled = true; applicationInfo.enabled = true }
        val aosp = pm.addActivityIfNotPresent(ComponentName("com.android.settings", "com.android.settings.Settings"))
        pm.addResolveInfoForIntent(Intent(Settings.ACTION_SETTINGS), android.content.pm.ResolveInfo().apply { activityInfo = aosp })
        assertTrue(SystemProbe.isSettingsApp(app, component.packageName))
        val intent = Intent(Intent.ACTION_MAIN).setComponent(component)
        val image = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).asImageBitmap()
        val entry = AppEntry(component.packageName, "Settings", component, false, false,
            TileArt(image, null, Rect.Zero, Color.Black, image))
        val activity = activity()
        for (tile in listOf(false, true)) {
            lock()
            if (tile) {
                MainActivity::class.java.getDeclaredMethod("launch", AppEntry::class.java, Rect::class.java).apply {
                    isAccessible = true
                }.invoke(activity, entry, null)
            } else open(activity, intent)
            assertNull(shadowOf(app).nextStartedActivity)
            val pin = vm.overlay as Overlay.Pin
            assertTrue(AerialSettings.unlock("1234"))
            pin.onDone()
            assertEquals(component, shadowOf(app).nextStartedActivity.component)
        }
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
