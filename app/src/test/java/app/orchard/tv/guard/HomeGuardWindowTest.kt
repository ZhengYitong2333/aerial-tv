package app.orchard.tv.guard

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.PowerManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowAccessibilityService
import org.robolectric.util.ReflectionHelpers

// Legacy SDK behavior is the subject of these compatibility tests.
@Suppress("DEPRECATION")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], shadows = [GuardAccessibilityShadow::class])
class HomeGuardWindowTest {
    private val context: Context = RuntimeEnvironment.getApplication()
    private val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    @Test
    fun updateRecoveryUsesFreshFrontAndRechecksPower() {
        val controller = Robolectric.buildService(HomeGuardService::class.java).create()
        val guard = controller.get()
        val app = shadowOf(RuntimeEnvironment.getApplication())
        ReflectionHelpers.setField(guard, "stockHomes", mapOf("vendor.home" to setOf("vendor.home.Home")))
        fun front(pkg: String, sequence: Long) {
            ReflectionHelpers.setField(guard, "lastWindow", pkg to "$pkg.Home")
            ReflectionHelpers.setField(guard, "lastActivityWindowSequence", sequence)
        }
        fun finish() = ReflectionHelpers.callInstanceMethod<Unit>(guard, "recoverAfterUpdate",
            ReflectionHelpers.ClassParameter.from(Long::class.javaPrimitiveType, 1L))
        try {
            shadowOf(power).setIsInteractive(true)
            front("vendor.home", 1) // An old window is not current evidence.
            finish(); assertNull(app.nextStartedActivity)
            front("video.app", 2) // User switched during the delay.
            finish(); assertNull(app.nextStartedActivity)
            front("vendor.home", 3)
            shadowOf(power).setIsInteractive(false)
            finish(); assertNull(app.nextStartedActivity)
            shadowOf(power).setIsInteractive(true)
            finish(); assertNotNull(app.nextStartedActivity)
        } finally { controller.destroy() }
    }

    @Test
    fun reconnectDuringStandbyRemovesKeyFilterAndScreenOnRestoresIt() {
        // A stock launcher is Home, so the guard filters keys whenever the screen is on.
        val stock = shadowOf(context.packageManager).addActivityIfNotPresent(ComponentName("vendor.launcher", "vendor.launcher.Home"))
        shadowOf(context.packageManager).setResolveInfosForIntent(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), listOf(ResolveInfo().apply { activityInfo = stock }))
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(
            "${context.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")
        val controller = Robolectric.buildService(HomeGuardService::class.java).create()
        val guard = controller.get()
        val filter = AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
        guard.serviceInfo = AccessibilityServiceInfo().apply { flags = filter }
        shadowOf(power).setIsInteractive(false)
        try {
            ReflectionHelpers.callInstanceMethod<Unit>(guard, "onServiceConnected")
            assertFalse(guard.serviceInfo.flags and filter != 0)
            assertNull(shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity)
            shadowOf(power).setIsInteractive(true)
            context.sendBroadcast(Intent(Intent.ACTION_SCREEN_ON))
            shadowOf(android.os.Looper.getMainLooper()).idle()
            assertTrue(guard.serviceInfo.flags and filter != 0)
        } finally {
            controller.destroy()
        }
    }
}

@Implements(AccessibilityService::class)
class GuardAccessibilityShadow : ShadowAccessibilityService() {
    private var currentInfo = AccessibilityServiceInfo()

    @Implementation
    protected fun getServiceInfo(): AccessibilityServiceInfo = currentInfo

    @Implementation
    protected fun setServiceInfo(info: AccessibilityServiceInfo) {
        currentInfo = info
    }
}
