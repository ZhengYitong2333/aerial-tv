package app.orchard.tv.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.Density
import app.orchard.tv.BuildConfig
import app.orchard.tv.ui.wallpaper.PhoneCard
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhoneCardScanTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun ordinaryDetectorReadsTheRenderedCardAtTwoTvCanvasScales() {
        val url = "http://192.0.2.1:8765/?token=abcdefgh23456789#wallpaper"
        val qr = PhoneServer.qr(url)
        val scale = mutableFloatStateOf(1f)
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup().visible()
        try {
            controller.get().setContent {
                CompositionLocalProvider(LocalDensity provides Density(scale.floatValue)) {
                    PhoneCard(url, qr.asImageBitmap())
                }
            }
            for (density in listOf(1f, 2f)) {
                compose.runOnIdle { scale.floatValue = density }
                compose.waitForIdle()
                val view = controller.get().window.decorView
                // Draw the real view tree with Robolectric's native renderer; PixelCopy's
                // asynchronous frame callback is unavailable in this local test environment.
                val rendered = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                compose.runOnIdle { view.draw(Canvas(rendered)) }
                val pixels = IntArray(rendered.width * rendered.height)
                rendered.getPixels(pixels, 0, rendered.width, 0, 0, rendered.width, rendered.height)
                // No PURE_BARCODE: locate the QR among the actual card's padding and labels.
                val source = RGBLuminanceSource(rendered.width, rendered.height, pixels)
                val decoded = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source)))
                assertEquals(url, decoded.text)
                assertTrue(decoded.resultPoints.size >= 3)
                val out = File("build/reports/phone-qr/${BuildConfig.FLAVOR}-${density.toInt()}x.png")
                requireNotNull(out.parentFile).mkdirs()
                out.outputStream().use { rendered.compress(Bitmap.CompressFormat.PNG, 100, it) }
                rendered.recycle()
            }
        } finally {
            controller.pause().stop().destroy()
            qr.recycle()
        }
    }
}
