package kz.aita

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.util.UUID
import kotlinx.coroutines.delay
import kz.aita.updates.InstallerKind

internal fun linuxShellQuote(value: String) = "'" + value.replace("'", "'\\''") + "'"

/** No GUI package-center cache: install the exact verified file using the system package manager. */
internal fun linuxPackageCommand(kind: InstallerKind, file: File, exists: (String) -> Boolean = { File(it).canExecute() }): List<String> = when {
    kind == InstallerKind.DEB && exists("/usr/bin/apt-get") -> listOf("/usr/bin/sudo", "/usr/bin/apt-get", "install", "--reinstall", "--no-remove", "--allow-downgrades", "--", file.absolutePath)
    kind == InstallerKind.RPM && exists("/usr/bin/dnf") -> listOf("/usr/bin/sudo", "/usr/bin/dnf", "install", "--", file.absolutePath)
    kind == InstallerKind.RPM && exists("/usr/bin/zypper") -> listOf("/usr/bin/sudo", "/usr/bin/zypper", "install", "--oldpackage", "--", file.absolutePath)
    else -> error("Supported system package manager not found")
}
internal fun linuxTerminalCommand(script: File, exists: (String) -> Boolean = { File(it).canExecute() }): List<String>? = when {
    exists("/usr/bin/gnome-terminal") -> listOf("/usr/bin/gnome-terminal", "--", "/bin/sh", script.absolutePath)
    exists("/usr/bin/konsole") -> listOf("/usr/bin/konsole", "-e", "/bin/sh", script.absolutePath)
    exists("/usr/bin/xfce4-terminal") -> listOf("/usr/bin/xfce4-terminal", "--disable-server", "-x", "/bin/sh", script.absolutePath)
    exists("/usr/bin/x-terminal-emulator") -> listOf("/usr/bin/x-terminal-emulator", "-e", "/bin/sh", script.absolutePath)
    exists("/usr/bin/xterm") -> listOf("/usr/bin/xterm", "-e", "/bin/sh", script.absolutePath)
    else -> null
}
internal fun linuxInstallerScript(file: File, sha256: String, packageCommand: List<String>, parent: Long,
    launcher: File, ready: File, token: String, result: File, log: File, russian: Boolean): String {
    require(sha256.matches(Regex("[a-fA-F0-9]{64}")) && parent > 1 && token.matches(Regex("[a-zA-Z0-9-]+")))
    fun q(s: String) = linuxShellQuote(s)
    val intro = if (russian) "AITA · Установка обновления. Сначала приложение сохранит данные и закроется. Затем Linux попросит системный пароль. Ввод пароля не отображается." else "AITA · Installing update. The app will save its work and close. Linux will then ask for your system password; typed characters are hidden."
    val done = if (russian) "Установка завершена. Запускаем AITA." else "Installation finished. Starting AITA."
    val failed = if (russian) "Установка не завершена. Файл сохранён для повторной попытки. Подробности выше и в журнале:" else "Installation did not finish. The verified file is kept for retry. Details above and in the log:"
    val enter = if (russian) "Нажмите Enter, чтобы закрыть это окно." else "Press Enter to close this window."
    // sudo runs only the fixed package-manager argv, never this writable helper as root.
    return """#!/bin/sh
umask 077
printf '%s\n' ${q(intro)}
package=${q(file.absolutePath)}
expected=${q(sha256.lowercase())}
ready=${q(ready.absolutePath)}
result=${q(result.absolutePath)}
log=${q(log.absolutePath)}
verify() {
  actual=${'$'}(/usr/bin/sha256sum -- "${'$'}package") || return 1
  actual=${'$'}{actual%% *}
  [ "${'$'}actual" = "${'$'}expected" ]
}
if ! verify; then printf '%s\n' 'AITA: SHA-256 mismatch. Installation blocked.'; exit 1; fi
printf '%s' ${q(token)} > "${'$'}ready"
tries=0
while kill -0 $parent 2>/dev/null; do
  tries=${'$'}((tries + 1))
  if [ "${'$'}tries" -ge 90 ]; then printf '%s\n' 'AITA did not close. Installation cancelled.'; exit 1; fi
  sleep 1
done
if ! verify; then printf '%s\n' 'AITA: SHA-256 mismatch. Installation blocked.'; exit 1; fi
fifo="${'$'}log.pipe"
mkfifo "${'$'}fifo" || exit 1
tee "${'$'}log" < "${'$'}fifo" &
tee_pid=${'$'}!
${packageCommand.joinToString(" ") { q(it) }} > "${'$'}fifo" 2>&1
status=${'$'}?
wait "${'$'}tee_pid"
rm -f -- "${'$'}fifo"
printf '%s' "${'$'}status" > "${'$'}result"
if [ "${'$'}status" = 0 ]; then
  printf '%s\n' ${q(done)}
else
  printf '%s\n' ${q(failed)} "${'$'}log"
fi
${q(launcher.absolutePath)} >/dev/null 2>&1 &
printf '%s\n' ${q(enter)}
read answer
exit "${'$'}status"
"""
}

internal suspend fun startLinuxUpdateHandoff(file: File, sha256: String, kind: InstallerKind): Boolean {
    val launcher = System.getProperty("jpackage.app-path")?.let(::File)?.takeIf { it.isFile && it.canExecute() }
        ?: listOf("/opt/aita/bin/AITA", "/opt/AITA/bin/AITA").map(::File).firstOrNull { it.isFile && it.canExecute() }
        ?: run { clientInstallerDetailsState.value = "AITA: installed application launcher not found"; return false }
    val parent = ProcessHandle.current().pid()
    val directory = File(System.getProperty("user.home"), ".aita/linux-installations/${UUID.randomUUID()}")
    Files.createDirectories(directory.toPath(), PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")))
    val script = File(directory, "install.sh")
    val ready = File(directory, "ready")
    val token = UUID.randomUUID().toString()
    val command = linuxPackageCommand(kind, file)
    script.writeText(linuxInstallerScript(file, sha256, command, parent, launcher, ready, token,
        File(directory, "result"), File(directory, "install.log"), appLanguageState.value == "ru"))
    val terminal = linuxTerminalCommand(script) ?: run {
        clientInstallerDetailsState.value = "AITA: ${if (appLanguageState.value == "ru") "Откройте терминал и выполните" else "Open a terminal and run"}: ${command.joinToString(" ") { linuxShellQuote(it) }}"
        return false
    }
    ProcessBuilder(terminal).start()
    repeat(100) {
        if (ready.isFile && ready.length() < 100 && ready.readText() == token) return true
        delay(100)
    }
    clientInstallerDetailsState.value = if (appLanguageState.value == "ru") "Окно установки не открылось. Приложение остаётся открытым, файл сохранён." else "The installation window did not open. AITA stays open and the download is kept."
    return false
}
