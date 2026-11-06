package app.orchard.tv.update

import android.annotation.SuppressLint
import android.os.Build
import java.io.File

/** Android 5/6 OEM installers read public APK code from a file URI; no owner data is shared. */
internal object LegacyInstaller {
    @SuppressLint("SetWorldReadable")
    fun makeApkReadable(file: File) {
        if (Build.VERSION.SDK_INT < 24) file.setReadable(true, false)
    }
}
