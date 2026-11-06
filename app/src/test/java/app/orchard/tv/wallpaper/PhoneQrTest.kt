package app.orchard.tv.wallpaper

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Exercise the app's bitmap writer and decode its result after changing ZXing. No LAN access. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 28])
class PhoneQrTest {
    @Test
    fun phoneSetupUrlSurvivesBitmapRoundTrip() {
        val url = "http://192.0.2.1:8765/?token=abcdefgh23456789#wallpaper"
        val bitmap = PhoneServer.qr(url)
        try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
            // The TV UI supplies the white quiet zone around this margin-free bitmap.
            val decoded = QRCodeReader().decode(
                BinaryBitmap(HybridBinarizer(source)), mapOf(DecodeHintType.PURE_BARCODE to true),
            )
            assertEquals(url, decoded.text)
        } finally {
            bitmap.recycle()
        }
    }
}
