package app.orchard.tv.wallpaper

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PhotoImportTest {
    @Test
    fun rejectsInvalidDimensions() {
        assertNull(PhotoImport.sampleSize(0, 500))
        assertNull(PhotoImport.sampleSize(500, -1))
    }

    @Test
    fun narrowTallPhotoIsSampledBeforeDecoding() {
        val sample = PhotoImport.sampleSize(2000, 30000)!!
        assertEquals(16, sample)
        assertTrue(2000L * 30000 / sample / sample * 4 <= 2560L * 2560 * 4)
    }

    @Test
    fun hugeReportedDimensionsDoNotOverflow() {
        val sample = PhotoImport.sampleSize(Int.MAX_VALUE, Int.MAX_VALUE)!!
        assertTrue(sample > 0)
        assertTrue((Int.MAX_VALUE.toLong() + sample - 1) / sample <= 2560)
    }

    @Test
    fun wideImageIsActuallyDecodedWithinBounds() {
        checkImage(4000, 800, 2000, 400)
    }

    @Test
    fun portraitImageIsActuallyDecodedWithinBounds() {
        checkImage(800, 4000, 400, 2000)
    }

    @Test
    fun smallImageIsNotUpscaled() {
        checkImage(320, 240, 320, 240)
    }

    @Test
    fun emptyFileIsNotAcceptedAsAPhoto() {
        val file = File.createTempFile("photo-test", ".png", RuntimeEnvironment.getApplication().cacheDir)
        try {
            assertNull(PhotoImport.decode(file))
        } finally {
            file.delete()
        }
    }

    private fun checkImage(width: Int, height: Int, expectedWidth: Int, expectedHeight: Int) {
        val file = File.createTempFile("photo-test", ".png", RuntimeEnvironment.getApplication().cacheDir)
        val original = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            file.outputStream().use { assertTrue(original.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            val decoded = requireNotNull(PhotoImport.decode(file))
            try {
                assertEquals(expectedWidth, decoded.width)
                assertEquals(expectedHeight, decoded.height)
            } finally {
                decoded.recycle()
            }
        } finally {
            original.recycle()
            file.delete()
        }
    }
}
