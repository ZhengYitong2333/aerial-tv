package app.orchard.tv.system

import android.media.SoundPool
import app.orchard.tv.settings.AerialSettings
import app.orchard.tv.settings.UiSounds
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ResourceLifecycleTest {
    @Test
    fun enablingDiagnosticsKeepsEarlyBootLinesInPrivateStorage() {
        val app = RuntimeEnvironment.getApplication()
        DiagLog.setEnabled(app, false)
        DiagLog.i("early-boot-merge-regression")
        try {
            DiagLog.setEnabled(app, true)
            val writer = ReflectionHelpers.getField<Lazy<DiagnosticWriter>>(DiagLog, "writer\$delegate").value
            writer.submit {}.get(5, java.util.concurrent.TimeUnit.SECONDS)
            val log = java.io.File(app.filesDir, "diagnostics/aerial-diag.log")
            assertTrue(log.readText().contains("early-boot-merge-regression"))
            assertTrue(log.readText().contains("-- earlier in this process:"))
            assertTrue(!java.io.File(app.getExternalFilesDir(null), "aerial-diag.log").exists())
        } finally {
            DiagLog.setEnabled(app, false)
        }
    }

    @Test
    fun logRingIsBoundedByCharactersAsWellAsNumberOfLines() {
        val message = "x".repeat(10_000)
        repeat(100) { DiagLog.i(message) }
        val recent = DiagLog.recent(1000)
        assertTrue(recent.isNotEmpty())
        assertTrue(recent.sumOf { it.length } <= 128 * 1024)
        assertTrue(recent.all { it.length < 4200 })
    }

    @Test
    fun disablingOwnSoundsReleasesTheMixerAndAllowsItToBeCreatedAgain() {
        val app = RuntimeEnvironment.getApplication()
        AerialSettings.init(app)
        AerialSettings.chooseUiSounds(UiSounds.Aerial)
        Sounds.init(app)
        val first = ReflectionHelpers.getField<SoundPool?>(Sounds, "pool")
        assertNotNull(first)
        AerialSettings.chooseUiSounds(UiSounds.Off)
        Sounds.prepare()
        assertNull(ReflectionHelpers.getField<SoundPool?>(Sounds, "pool"))
        AerialSettings.chooseUiSounds(UiSounds.Aerial)
        Sounds.prepare()
        val second = ReflectionHelpers.getField<SoundPool?>(Sounds, "pool")
        assertNotNull(second)
        assertNotSame(first, second)
        Sounds.release()
    }
}
