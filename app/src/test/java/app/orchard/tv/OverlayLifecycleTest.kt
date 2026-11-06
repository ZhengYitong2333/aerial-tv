package app.orchard.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import app.orchard.tv.settings.AerialSettings
import app.orchard.tv.ui.home.PinMode
import app.orchard.tv.ui.home.PinPad
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Actual app overlay and ViewModel with Activity/Compose lifecycle wiring, without Home's network work. */
class OverlayTestActivity : ComponentActivity() {
    val model by viewModels<HomeViewModel>()
    var homeBacks = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AerialSettings.init(application)
        getSharedPreferences("settings", MODE_PRIVATE).edit().putBoolean("setup_offered", true).commit()
        setContent {
            val pin = model.overlay as? Overlay.Pin
            BackHandler(enabled = pin == null) { homeBacks++ }
            PinPad(
                visible = pin != null,
                mode = pin?.mode,
                onDone = { pin?.onDone?.invoke() },
                onCancel = { model.overlay = Overlay.None },
            )
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 28], qualifiers = "w960dp-h540dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OverlayLifecycleTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun backClosesOnlyTheOverlayAndStillWorksAfterBackgroundReturn() {
        val controller = Robolectric.buildActivity(OverlayTestActivity::class.java).setup().visible()
        try {
            val activity = controller.get()
            compose.runOnIdle { activity.model.overlay = Overlay.Pin(PinMode.Create) {} }
            compose.waitForIdle()
            compose.runOnIdle { activity.onBackPressedDispatcher.onBackPressed() }
            compose.runOnIdle {
                assertEquals(Overlay.None, activity.model.overlay)
                assertEquals(0, activity.homeBacks)
                assertFalse(activity.isFinishing)
            }
            compose.runOnIdle { activity.model.overlay = Overlay.Pin(PinMode.Create) {} }
            compose.waitForIdle()
            controller.pause().stop()
            assertEquals(Lifecycle.State.CREATED, activity.lifecycle.currentState)
            controller.restart().start().resume().visible()
            compose.runOnIdle { assertTrue(activity.model.overlay is Overlay.Pin) }
            compose.waitForIdle()
            compose.runOnIdle { activity.onBackPressedDispatcher.onBackPressed() }
            compose.runOnIdle { assertEquals(Overlay.None, activity.model.overlay) }
            compose.waitForIdle()
            compose.runOnIdle { activity.onBackPressedDispatcher.onBackPressed() }
            compose.runOnIdle {
                assertEquals(1, activity.homeBacks)
                assertEquals(Lifecycle.State.RESUMED, activity.lifecycle.currentState)
                assertFalse(activity.isFinishing)
            }
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test
    fun recreationRetainsTheAppViewModelAndRebindsTheVisibleOverlayBackHandler() {
        val controller = Robolectric.buildActivity(OverlayTestActivity::class.java).setup().visible()
        try {
            val before = controller.get()
            val model = before.model
            compose.runOnIdle { model.overlay = Overlay.Pin(PinMode.Create) {} }
            compose.waitForIdle()
            controller.recreate().visible()
            val after = controller.get()
            compose.waitForIdle()
            compose.runOnIdle {
                assertNotSame(before, after)
                assertSame(model, after.model)
                assertTrue(after.model.overlay is Overlay.Pin)
                after.onBackPressedDispatcher.onBackPressed()
            }
            compose.runOnIdle { assertEquals(Overlay.None, after.model.overlay) }
            compose.waitForIdle()
            compose.runOnIdle { after.onBackPressedDispatcher.onBackPressed() }
            compose.runOnIdle {
                assertEquals(1, after.homeBacks)
                assertEquals(0, before.homeBacks)
                assertFalse(after.isFinishing)
            }
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
