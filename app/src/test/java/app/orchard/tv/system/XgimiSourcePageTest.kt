package app.orchard.tv.system

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class XgimiSourcePageTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val service = ComponentName("com.android.newsettings", "com.xgimi.newsettings.SettingService")
    private val page = "Settings://com.xgimi.settings.signalSource/hdmi_misckey"

    @Test
    fun passthroughIsLeftToTheVendorPageOnlyWhenThatPageExists() {
        val passthrough = Intent(Intent.ACTION_VIEW, Uri.parse("content://android.media.tv/passthrough/hdmi1"))
        val pageIntent = Intent("com.xgimi.settings.SETTINGS").setComponent(service)
        assertFalse(SystemProbe.passthroughLeftToVendor(passthrough, null))
        assertTrue(SystemProbe.passthroughLeftToVendor(passthrough, pageIntent))
        assertFalse(SystemProbe.passthroughLeftToVendor(Intent(Intent.ACTION_MAIN).setComponent(service), pageIntent))
    }

    @Test
    fun listedHdmiRowsThatOnlyOpenTheSourcePageSkipTheList() {
        val pageIntent = Intent("com.xgimi.settings.SETTINGS").setComponent(service).putExtra("data", page)
        val hdmi = source("tvinput/HW5", pageIntent)
        val usb = source(SystemProbe.USB_SOURCE_ID, Intent(Intent.ACTION_MAIN))
        assertTrue(SystemProbe.listedInputsNeedVendorPage(listOf(hdmi, usb)))
        assertFalse(SystemProbe.listedInputsNeedVendorPage(listOf(usb)))
        assertFalse(SystemProbe.listedInputsNeedVendorPage(listOf(source("tvinput/HW5", Intent(Intent.ACTION_VIEW)))))
    }

    @Test
    fun exportedSettingsServiceOpensTheSourcePage() {
        shadowOf(context.packageManager).addServiceIfNotPresent(service).apply {
            exported = true
            enabled = true
            applicationInfo.enabled = true
        }
        val caller = RecordingContext(context)
        assertTrue(SystemProbe.openInputPicker(caller))
        val started = caller.services.single()
        assertEquals(service, started.component)
        assertEquals("com.xgimi.settings.SETTINGS", started.action)
        assertEquals(page, started.getStringExtra("data"))
        assertTrue(caller.activities.isEmpty())
    }

    private fun source(id: String, intent: Intent) = TvSource(
        id = id,
        label = "HDMI 1",
        kind = SourceKind.Hdmi,
        connected = false,
        deviceName = null,
        intent = intent,
    )

    private class RecordingContext(base: Context) : ContextWrapper(base) {
        val services = mutableListOf<Intent>()
        val activities = mutableListOf<Intent>()

        override fun startService(service: Intent): ComponentName? {
            services += Intent(service)
            return service.component
        }

        override fun startActivity(intent: Intent) {
            activities += Intent(intent)
        }
    }
}
