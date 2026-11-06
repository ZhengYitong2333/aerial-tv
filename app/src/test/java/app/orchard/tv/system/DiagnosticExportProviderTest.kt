package app.orchard.tv.system

import android.content.ComponentName
import android.net.Uri
import android.os.ParcelFileDescriptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DiagnosticExportProviderTest {
    private val app = RuntimeEnvironment.getApplication()
    private fun uri(path: String) = Uri.parse("content://${app.packageName}.diagnostics/$path")

    @Test
    fun onlyCallersWithDumpPermissionCanUseTheExportedProvider() {
        val info = app.packageManager.getProviderInfo(ComponentName(app, DiagnosticExportProvider::class.java), 0)
        assertTrue(info.exported)
        assertEquals("android.permission.DUMP", info.readPermission)
        assertEquals("android.permission.DUMP", info.writePermission)
        assertTrue(!info.grantUriPermissions)
    }

    @Test
    fun adbReadInterfaceReturnsPrivateLogBytesAndRejectsWriteModes() {
        val dir = File(app.filesDir, "diagnostics").apply { mkdirs() }
        File(dir, "aerial-diag.log").writeText("private diagnostic bytes")
        val provider = Robolectric.buildContentProvider(DiagnosticExportProvider::class.java).create().get()
        ParcelFileDescriptor.AutoCloseInputStream(provider.openFile(uri("log/current"), "r")).use {
            assertEquals("private diagnostic bytes", it.bufferedReader().readText())
        }
        assertThrows(FileNotFoundException::class.java) { provider.openFile(uri("log/current"), "rw") }
    }

    @Test
    fun tilesExportIsLimitedToNamedPngsInItsPrivateDirectory() {
        val dir = File(app.filesDir, "tile-exports").apply { mkdirs() }
        val file = File(dir, "before.png").apply { writeText("test pixels") }
        assertEquals(file.canonicalFile, DiagnosticExportProvider.resolveFile(app, uri("tiles/before")))
        assertThrows(FileNotFoundException::class.java) { DiagnosticExportProvider.resolveFile(app, uri("tiles/missing")) }
    }

    @Test
    fun arbitraryFilesAndEncodedTraversalCannotBeExported() {
        File(app.filesDir, "secret.txt").writeText("not an export")
        for (path in listOf("../secret.txt", "tiles/%2e%2e", "tiles/%2e%2e%2fsecret", "log/secret", "log/current?file=secret", "log/current/extra")) {
            assertThrows(FileNotFoundException::class.java) { DiagnosticExportProvider.resolveFile(app, uri(path)) }
        }
        assertThrows(FileNotFoundException::class.java) {
            DiagnosticExportProvider.resolveFile(app, Uri.parse("content://another.authority/log/current"))
        }
    }

    @Test
    fun symlinksCannotRedirectALogExportToAnotherPrivateFile() {
        val dir = File(app.filesDir, "diagnostics").apply { mkdirs() }
        val secret = File(app.filesDir, "secret.txt").apply { writeText("not an export") }
        Files.createSymbolicLink(File(dir, "aerial-diag.log").toPath(), secret.toPath())
        assertThrows(FileNotFoundException::class.java) { DiagnosticExportProvider.resolveFile(app, uri("log/current")) }
    }
}
