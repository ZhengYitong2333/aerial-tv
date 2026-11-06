package app.orchard.tv.wallpaper

import android.graphics.Bitmap
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ThumbnailCacheTest {
    @Test
    fun browsingOneHundredPhotosRetainsAtMostEightMiB() {
        val cache = ThumbnailCache()
        var inserted = 0L
        repeat(100) {
            val bitmap = Bitmap.createBitmap(384, 216, Bitmap.Config.ARGB_8888)
            inserted += bitmap.allocationByteCount
            cache["photo:$it"] = bitmap
        }
        assertTrue(cache.sizeBytes > 0)
        assertTrue(cache.sizeBytes <= 8 * 1024 * 1024)
        assertTrue(cache.sizeBytes < inserted / 2)
        assertNull(cache["photo:0"])
        assertNotNull(cache["photo:99"])
        println("Thumbnail cache: inserted=$inserted bytes, retained=${cache.sizeBytes} bytes")
    }

    @Test
    fun recentlyUsedPhotoSurvivesAndEvictionDoesNotRecycleVisiblePixels() {
        val cache = ThumbnailCache(2048)
        val a = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        val b = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        cache["a"] = a; cache["b"] = b
        assertSame(a, cache["a"])
        cache["c"] = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        assertSame(a, cache["a"])
        assertNull(cache["b"])
        assertFalse(b.isRecycled)
    }

    @Test
    fun oversizedBitmapDoesNotDefeatTheByteLimit() {
        val cache = ThumbnailCache(1024)
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        cache["large"] = bitmap
        assertNull(cache["large"])
        assertFalse(bitmap.isRecycled)
    }

    @Test
    fun memoryPressureClearsRetainedReferencesWithoutInvalidatingDisplayedBitmaps() {
        val cache = ThumbnailCache()
        val bitmap = Bitmap.createBitmap(384, 216, Bitmap.Config.ARGB_8888)
        cache["photo"] = bitmap
        cache.clear()
        assertTrue(cache.sizeBytes == 0)
        assertNull(cache["photo"])
        assertFalse(bitmap.isRecycled)
    }
}
