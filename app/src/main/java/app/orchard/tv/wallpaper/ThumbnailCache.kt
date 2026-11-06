package app.orchard.tv.wallpaper

import android.graphics.Bitmap
import android.util.LruCache

/** Bound retained pixel memory, not just the number of photos the owner has browsed. */
internal class ThumbnailCache(maxBytes: Int = 8 * 1024 * 1024) {
    private val cache = object : LruCache<String, Bitmap>(maxBytes) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    operator fun get(id: String): Bitmap? = cache.get(id)
    operator fun set(id: String, bitmap: Bitmap) { cache.put(id, bitmap) }
    fun remove(id: String) { cache.remove(id) }
    fun removeIf(predicate: (String) -> Boolean) { cache.snapshot().keys.filter(predicate).forEach { cache.remove(it) } }
    fun clear() { cache.evictAll() }
    val sizeBytes: Int get() = cache.size()

    // Eviction only releases our reference. Compose or an HTTP response may still use the bitmap.
}
