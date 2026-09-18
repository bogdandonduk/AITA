package kz.aita

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.File

internal fun managedInstallerFileIdentity(file: File): File {
    // Os.lstat is available on every supported Android version, including API 24/25
    // where java.nio.file.Files is unavailable.
    try {
        if (OsConstants.S_ISLNK(Os.lstat(file.absolutePath).st_mode)) throw ClientUpdateFailure("storage")
    } catch (error: ErrnoException) {
        if (error.errno != OsConstants.ENOENT) throw ClientUpdateFailure("storage")
    }
    return file.canonicalFile
}
