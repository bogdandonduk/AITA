package kz.aita

import java.io.File
import kz.aita.updates.InstallerKind
import kotlin.test.*

class LinuxInstallerHandoffTest {
    @Test fun localDebIsPassedAsOneArgumentAndPackageCenterIsNeverUsed() {
        val file = File("/tmp/update's package.deb")
        val args = linuxPackageCommand(InstallerKind.DEB, file) { it == "/usr/bin/apt-get" }
        assertEquals(file.absolutePath, args.last())
        assertContains(args, "--no-remove"); assertContains(args, "--reinstall")
        assertEquals("/usr/bin/sudo", args.first())
    }
    @Test fun terminalRunsHelperWithoutShellInterpolation() {
        val file = File("/tmp/$(bad) helper.sh")
        assertEquals(listOf("/usr/bin/gnome-terminal", "--", "/bin/sh", file.absolutePath),
            linuxTerminalCommand(file) { it == "/usr/bin/gnome-terminal" })
        assertNull(linuxTerminalCommand(file) { false })
    }
    @Test fun helperProtectsArgumentsAndVerifiesBeforeInstall() {
        val script = linuxInstallerScript(File("/tmp/a'$(never).deb"), "a".repeat(64),
            listOf("/usr/bin/sudo", "/usr/bin/apt-get", "install", "--", "/tmp/a'$(never).deb"),
            999999, File("/opt/aita/bin/AITA"), File("/tmp/ready"), "test-token", File("/tmp/result"), File("/tmp/log"), true)
        assertContains(script, linuxShellQuote(File("/tmp/a'$(never).deb").absolutePath))
        assertContains(script, linuxShellQuote("/tmp/a'$(never).deb"))
        assertTrue(script.indexOf("verify") < script.indexOf("/usr/bin/sudo"))
    }
    @Test fun helperHasValidLinuxShellSyntax() {
        org.junit.Assume.assumeTrue("Linux helper syntax requires the Linux shell",
            System.getProperty("os.name").lowercase().contains("linux"))
        val dir = java.nio.file.Files.createTempDirectory("aita-helper-test").toFile()
        try {
            val script = File(dir, "helper.sh")
            script.writeText(linuxInstallerScript(File(dir, "a'$(never).deb"), "a".repeat(64),
                listOf("/usr/bin/sudo", "/usr/bin/apt-get", "install", "--", "/tmp/a'$(never).deb"),
                999999, File("/opt/aita/bin/AITA"), File(dir, "ready"), "test-token", File(dir,"result"), File(dir,"log"), true))
            val result = ProcessBuilder("/bin/sh", "-n", script.absolutePath).redirectErrorStream(true).start()
            val output = result.inputStream.bufferedReader().readText()
            assertEquals(0, result.waitFor(), output)
            assertTrue(script.readText().indexOf("verify") < script.readText().indexOf("/usr/bin/sudo"))
        } finally { dir.deleteRecursively() }
    }
}
