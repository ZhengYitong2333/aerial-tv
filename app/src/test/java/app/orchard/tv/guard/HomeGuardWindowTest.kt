package app.orchard.tv.guard

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.os.PowerManager
import android.os.UserManager
import android.view.accessibility.AccessibilityEvent
import app.orchard.tv.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    private val pm = shadowOf(context.packageManager)
    private val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    private val chooser = ComponentName("test.framework", "test.framework.HiddenHome")

    @Before
    fun awakeAndUnlocked() {
        shadowOf(power).setIsInteractive(true)
        shadowOf(context.getSystemService(Context.USER_SERVICE) as UserManager).setUserUnlocked(true)
        val info = pm.addActivityIfNotPresent(chooser)
        pm.setResolveInfosForIntent(home, listOf(ResolveInfo().apply { activityInfo = info }))
    }

    @Test
    fun resolvedHomeOutsideCachedCandidatesIsTakenOver() {
        val guard = Robolectric.buildService(HomeGuardService::class.java).get()
        // No cached candidate: reproduce firmware resolving a HOME chooser outside its list.
        ReflectionHelpers.setField(guard, "stockHomes", emptyMap<String, Set<String>>())
        guard.onAccessibilityEvent(window(chooser))
        assertEquals(ComponentName(context, MainActivity::class.java), shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity.component)
    }

    @Test
    fun anotherActivityInResolverPackageIsLeftAloneEvenOnWakeCheck() {
        val settings = ComponentName(chooser.packageName, "test.framework.Settings")
        pm.addActivityIfNotPresent(settings)
        val guard = Robolectric.buildService(HomeGuardService::class.java).get()
        guard.onAccessibilityEvent(window(settings))
        ReflectionHelpers.callInstanceMethod<Unit>(guard, "takeOverIfStockHomeInFront",
            ReflectionHelpers.ClassParameter.from(String::class.java, "test wake"),
            ReflectionHelpers.ClassParameter.from(Boolean::class.javaPrimitiveType!!, true))
        assertNull(shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity)
    }

    @Test
    fun changedHomeResolutionDoesNotKeepTakingTheOldChooser() {
        val guard = Robolectric.buildService(HomeGuardService::class.java).get()
        val newHome = pm.addActivityIfNotPresent(ComponentName("test.launcher", "test.launcher.Home"))
        pm.setResolveInfosForIntent(home, listOf(ResolveInfo().apply { activityInfo = newHome }))
        guard.onAccessibilityEvent(window(chooser))
        assertNull(shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity)
    }

    @Test
    fun excludedDaemonAndNegativePriorityFallbackAreLeftAlone() {
        val guard = Robolectric.buildService(HomeGuardService::class.java).get()
        val daemon = ComponentName("com.stv.appdaemon", "com.stv.appdaemon.Home")
        val daemonInfo = pm.addActivityIfNotPresent(daemon)
        pm.setResolveInfosForIntent(home, listOf(ResolveInfo().apply { activityInfo = daemonInfo }))
        guard.onAccessibilityEvent(window(daemon))
        assertNull(shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity)
        val chooserInfo = pm.addActivityIfNotPresent(chooser)
        pm.setResolveInfosForIntent(home, listOf(ResolveInfo().apply {
            activityInfo = chooserInfo
            priority = -1000
        }))
        guard.onAccessibilityEvent(window(chooser))
        assertNull(shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity)
    }

    @Test
    fun resolvedHomeNeverWakesTheScreenOrInterruptsIntentionalStockVisit() {
        val guard = Robolectric.buildService(HomeGuardService::class.java).get()
        shadowOf(power).setIsInteractive(false)
        guard.onAccessibilityEvent(window(chooser))
        assertNull(shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity)
        shadowOf(power).setIsInteractive(true)
        ReflectionHelpers.setField(guard, "allowStockUntil", Long.MAX_VALUE)
        guard.onAccessibilityEvent(window(chooser))
        assertNull(shadowOf(RuntimeEnvironment.getApplication()).nextStartedActivity)
    }

    @Test
    fun reconnectDuringStandbyRemovesKeyFilterAndScreenOnRestoresIt() {
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

    private fun window(component: ComponentName) = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED).apply {
        packageName = component.packageName
        className = component.className
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
