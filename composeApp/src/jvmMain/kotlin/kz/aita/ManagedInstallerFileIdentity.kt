package kz.aita

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption

internal fun managedInstallerFileIdentity(file: File): File {
    val path = file.toPath()
    // File.canonicalFile does not resolve NTFS symbolic links. Reject even dangling links,
    // and resolve existing paths to detect directory junctions and linked parent folders.
    if (Files.isSymbolicLink(path)) throw ClientUpdateFailure("storage")
    return if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) path.toRealPath().toFile() else file.canonicalFile
}
