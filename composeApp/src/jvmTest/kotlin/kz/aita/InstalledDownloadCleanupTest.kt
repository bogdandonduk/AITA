package kz.aita

import kz.aita.updates.*
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlin.test.*

class InstalledDownloadCleanupTest {
    private fun installed(build: Long, channel: ReleaseChannel = ReleaseChannel.RELEASE) = ClientBuildIdentity("1.2.1", build, channel, "", "", "direct", "", "")
    private fun fixture(test: (InstalledDownloadCleanup, File, OwnedInstallerDownload) -> Unit) {
        val root = Files.createTempDirectory("aita-owned-download-test").toRealPath().toFile()
        try {
            val cache = ManagedClientInstaller(File(root, "private")) { a,b -> Files.move(a.toPath(), b.toPath(), StandardCopyOption.REPLACE_EXISTING); Unit }
            val registry = InstalledDownloadCleanup(cache)
            val file = File(root, "user-download.apk").apply { writeText("test fixture, not executable") }
            val hash = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it.toInt() and 255) }
            val record = OwnedInstallerDownload(file.path, 22, ReleaseChannel.RELEASE, file.length(), hash, file.lastModified())
            registry.record(record)
            test(registry, file, record)
        } finally { root.deleteRecursively() }
    }
    @Test fun cleansOnlyAfterInstalledBuildAcknowledgesSameChannel() = fixture { registry,file,_ ->
        registry.clean(installed(21), ::removeOwnedInstallerFile); assertTrue(file.exists())
        registry.clean(installed(22,ReleaseChannel.TEST), ::removeOwnedInstallerFile); assertTrue(file.exists())
        registry.clean(installed(22), ::removeOwnedInstallerFile); assertFalse(file.exists())
    }
    @Test fun preservesReplacedFilesEvenWithSameLengthAndTimestamp() = fixture { registry,file,record ->
        file.writeBytes(ByteArray(record.bytes.toInt()) { 1 }); file.setLastModified(record.modifiedAt!!)
        registry.clean(installed(22), ::removeOwnedInstallerFile); assertTrue(file.exists())
    }
    @Test fun retriesSharingFailuresOnNextLaunch() = fixture { registry,file,_ ->
        registry.clean(installed(22)) { false }; assertTrue(file.exists())
        registry.clean(installed(22), ::removeOwnedInstallerFile); assertFalse(file.exists())
    }
    @Test fun leavesUntrackedDownloadsAlone() = fixture { registry,file,_ ->
        val other = File(file.parentFile,"other.exe").apply { writeText("keep") }
        registry.clean(installed(23), ::removeOwnedInstallerFile); assertTrue(other.exists())
    }
}
