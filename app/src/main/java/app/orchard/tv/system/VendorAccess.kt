package app.orchard.tv.system

import android.annotation.SuppressLint
import android.os.IBinder

/** Optional OEM hooks have no public replacement; resolve once and fail closed when blocked. */
@SuppressLint("PrivateApi")
internal object VendorAccess {
    private val propertyGetter by lazy {
        runCatching { Class.forName("android.os.SystemProperties").getMethod("get", String::class.java) }.getOrNull()
    }

    private val serviceGetter by lazy {
        runCatching { Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java) }.getOrNull()
    }

    fun property(key: String): String? = runCatching { propertyGetter?.invoke(null, key) as? String }.getOrNull()
    fun service(name: String): IBinder? = runCatching { serviceGetter?.invoke(null, name) as? IBinder }.getOrNull()
}
