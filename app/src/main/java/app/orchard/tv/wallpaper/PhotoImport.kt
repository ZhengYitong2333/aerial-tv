package app.orchard.tv.wallpaper

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/** Bound decoded memory as well as file size: a narrow panorama can contain millions of rows. */
internal object PhotoImport {
    private const val MAX_SIDE = 2560
    private const val MAX_PIXELS = 2560L * 2560

    internal fun sampleSize(width: Int, height: Int): Int? {
        if (width <= 0 || height <= 0) return null
        var sample = 1
        while (true) {
            // Round up: decoders may keep the last partial row or column.
            val w = (width.toLong() + sample - 1) / sample
            val h = (height.toLong() + sample - 1) / sample
            if (w <= MAX_SIDE && h <= MAX_SIDE && w * h <= MAX_PIXELS) return sample
            sample *= 2
        }
    }

    fun decode(file: File): Bitmap? {
        if (!file.isFile || file.length() == 0L) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        val sample = sampleSize(bounds.outWidth, bounds.outHeight) ?: return null
        val decoded = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply {
            inSampleSize = sample
            inScaled = false
        }) ?: return null
        // Check the decoder's actual result too, before storing or rendering it.
        if (decoded.width > MAX_SIDE || decoded.height > MAX_SIDE ||
            decoded.width.toLong() * decoded.height > MAX_PIXELS
        ) {
            decoded.recycle()
            return null
        }
        return decoded
    }
}
