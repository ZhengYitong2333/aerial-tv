package app.orchard.tv.guard

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Process
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

// Legacy SDK behavior is the subject of these compatibility tests.
@Suppress("DEPRECATION")
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RevivalBindingTest {
    private val app = RuntimeEnvironment.getApplication()
    private val permission = "${app.packageName}.permission.BIND_REVIVAL"

    @Test
    fun bothFrameworkServicesRequireTheAppsSignaturePermission() {
        val info = app.packageManager.getPermissionInfo(permission, 0)
        assertEquals(PermissionInfo.PROTECTION_SIGNATURE, info.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE)
        for (type in listOf(RevivalSyncService::class.java, RevivalAccountService::class.java)) {
            val service = app.packageManager.getServiceInfo(ComponentName(app, type), 0)
            assertTrueExported(service.exported)
            assertEquals(permission, service.permission)
        }
    }

    @Test
    fun androidComponentPermissionCheckPreservesSystemAndOwnUid() {
        val own = Process.myUid()
        fun check(uid: Int) = ReflectionHelpers.callStaticMethod<Int>(ActivityManager::class.java,
            "checkComponentPermission",
            ReflectionHelpers.ClassParameter.from(String::class.java, permission),
            ReflectionHelpers.ClassParameter.from(Int::class.javaPrimitiveType!!, uid),
            ReflectionHelpers.ClassParameter.from(Int::class.javaPrimitiveType!!, own),
            ReflectionHelpers.ClassParameter.from(Boolean::class.javaPrimitiveType!!, true))
        assertEquals(PackageManager.PERMISSION_GRANTED, check(1000)) // Android system_server UID.
        assertEquals(PackageManager.PERMISSION_GRANTED, check(own))
        // Robolectric's IPackageManager does not implement checkUidPermission. Manifest protection
        // is checked above; use its public package-manager model for an unrelated package here.
        assertEquals(PackageManager.PERMISSION_DENIED, app.packageManager.checkPermission(permission, "unrelated.app"))
    }

    @Test
    fun syncServiceOnlyReturnsABinderForTheFrameworkSyncAction() {
        val service = Robolectric.buildService(RevivalSyncService::class.java).create().get()
        assertNull(service.onBind(Intent("unrelated.action")))
        assertNotNull(service.onBind(Intent("android.content.SyncAdapter")))
    }

    @Test
    fun authenticatorOnlyReturnsABinderForTheFrameworkAccountAction() {
        val service = Robolectric.buildService(RevivalAccountService::class.java).create().get()
        assertNull(service.onBind(Intent("unrelated.action")))
        assertNotNull(service.onBind(Intent("android.accounts.AccountAuthenticator")))
    }

    private fun assertTrueExported(exported: Boolean) = org.junit.Assert.assertTrue(exported)
}
