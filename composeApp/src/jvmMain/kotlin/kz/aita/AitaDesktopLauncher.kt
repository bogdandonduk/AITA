package kz.aita

/** Dispatch printer work before UI, credentials, database, timers or network initialization.
 * The installed launcher supplies the packaged classpath and runtime, including jlink images
 * which intentionally omit java.exe. Normal startup remains the existing desktop entrypoint. */
object AitaDesktopLauncher {
    @JvmStatic fun main(args: Array<String>) {
        if (args.firstOrNull() == "--aita-native-print") {
            WindowsRawPrintProcess.main(args.drop(1).toTypedArray())
            return
        }
        kz.aita.main()
    }
}
