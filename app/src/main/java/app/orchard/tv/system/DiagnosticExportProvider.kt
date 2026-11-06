package app.orchard.tv.system

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

/** Read-only ADB exports, protected by the platform DUMP permission in the manifest. */
class DiagnosticExportProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Read-only export")
        val file = resolveFile(requireNotNull(context), uri)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String? = runCatching {
        if (resolveFile(requireNotNull(context), uri).extension == "png") "image/png" else "text/plain"
    }.getOrNull()

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException("Read-only export")
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException("Read-only export")
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException("Read-only export")

    companion object {
        internal fun resolveFile(context: Context, uri: Uri): File {
            if (uri.authority != "${context.packageName}.diagnostics" || uri.query != null || uri.fragment != null) {
                throw FileNotFoundException("Unknown export")
            }
            val path = uri.pathSegments
            if (path.size != 2) throw FileNotFoundException("Unknown export")
            val (dir, name) = when {
                path[0] == "log" && path[1] == "current" -> File(context.filesDir, "diagnostics") to "aerial-diag.log"
                path[0] == "log" && path[1] == "previous" -> File(context.filesDir, "diagnostics") to "aerial-diag.old.log"
                path[0] == "tiles" && path[1].matches(Regex("[A-Za-z0-9_-]{1,40}")) ->
                    File(context.filesDir, "tile-exports") to "${path[1]}.png"
                else -> throw FileNotFoundException("Unknown export")
            }
            val file = File(dir, name).canonicalFile
            if (file.parentFile != dir.canonicalFile || !file.isFile) throw FileNotFoundException("Unknown export")
            return file
        }
    }
}
