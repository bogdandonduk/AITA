package kz.aita

private var webDiagnosticsInstalled = false
private fun ownBrowserJournal(onGranted: () -> Unit, onUnavailable: () -> Unit): Unit = js("""(onGranted, onUnavailable) => {
    if (!navigator.locks || !navigator.locks.request) { onUnavailable(); return; }
    navigator.locks.request('aita.runtime-diagnostics.journal', async () => {
        onGranted();
        await new Promise(() => {});
    }).catch(() => onUnavailable());
}""")
private fun browserJournalRead(): String? = js("() => localStorage.getItem('aita.runtime-diagnostics')")
private fun browserJournalWrite(value: String): Unit = js("(value) => localStorage.setItem('aita.runtime-diagnostics', value)")
private fun browserFamily(): String = js("""() => {
    const ua = navigator.userAgent || '';
    for (const name of ['Firefox', 'Edg', 'Chrome', 'Version']) {
        const found = ua.match(new RegExp(name + '/([0-9]{1,4})'));
        if (found) return (name === 'Version' ? 'Safari' : name) + ' ' + found[1];
    }
    return 'Browser';
}""")
private fun browserErrorHooks(report: (String, String, String) -> Unit): Unit = js("""(report) => {
    const send = (value, category) => {
        try {
            const type = value && typeof value.name === 'string' ? value.name : 'BrowserError';
            if (type === 'AbortError' || type.includes('CancellationException')) return;
            const raw = value && typeof value.stack === 'string' ? value.stack.slice(0, 32768) : '';
            const frames = raw.split('\n').slice(0, 48).map(line => {
                const frame = line.match(/^\s*at\s+([A-Za-z_$][A-Za-z0-9_.$]*)\s*\(.+:([0-9]{1,7}):[0-9]+\)\s*$/)
                    || line.match(/^([A-Za-z_$][A-Za-z0-9_.$]*)@.+:([0-9]{1,7}):[0-9]+$/);
                if (frame) return 'at ' + frame[1] + '(browser.js:' + frame[2] + ')';
                const wasm = line.match(/wasm-function\[([0-9]{1,7})\]/);
                return wasm ? 'symbol wasm.function_' + wasm[1] : '';
            }).filter(Boolean).slice(0, 32).join('\n');
            report(type, category, frames);
        } catch (_) {}
    };
    window.addEventListener('error', event => send(event.error, 'web.error'));
    window.addEventListener('unhandledrejection', event => send(event.reason, 'web.rejection'));
}""")
internal fun installWebRuntimeDiagnostics() {
    if (webDiagnosticsInstalled) return
    webDiagnosticsInstalled = true
    RuntimeDiagnostics.waitForPlatformStorage()
    ownBrowserJournal(onGranted = {
        RuntimeDiagnostics.configure(object : DiagnosticLocalStorage {
            override fun read(): String? = browserJournalRead()?.also { require(it.length <= 2_000_000) }
            override fun write(value: String) {
                if (shouldWriteDiagnosticJournal(read(), value)) browserJournalWrite(value)
            }
            override fun reset(value: String) {
                diagnosticJson.decodeFromString(DiagnosticJournal.serializer(), value).validated()
                browserJournalWrite(value)
            }
        }, diagnosticBuildContext(DiagnosticDevice("web", browserFamily(), "WebAssembly", "browser")), newId = ::newDiagnosticId)
        RuntimeDiagnostics.sendNow()
    }, onUnavailable = { RuntimeDiagnostics.platformStorageUnavailable() })
    browserErrorHooks { type, category, stack -> RuntimeDiagnostics.captureBrowser(type, category, stack) }
}
