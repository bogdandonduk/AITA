/* A renderer SIGSEGV cannot execute a JS exception handler. Stop repeated early-start
 * failures before starting WebGL/Wasm again; keep every app database and draft intact. */
(() => {
    const key = 'aita.startup-attempt';
    let previous = null;
    try { previous = JSON.parse(sessionStorage.getItem(key)); } catch (_) {}
    const now = Date.now();
    const incomplete = !!previous && previous.pending === true && now - previous.at >= 0 && now - previous.at < 86400000;
    const failures = incomplete ? Math.min(3, (previous.failures || 0) + 1) : 0;
    let pending = false;
    let healthyTimer;
    function save(value) { try { sessionStorage.setItem(key, JSON.stringify(value)); } catch (_) {} }
    function clear() { pending = false; clearTimeout(healthyTimer); save({pending: false, failures: 0, at: Date.now()}); }
    window.addEventListener('pagehide', clear);
    globalThis.aitaRecovery = Object.freeze({
        previousIncomplete: incomplete,
        blocked: failures >= 2,
        begin() { pending = true; save({pending: true, failures, at: now}); },
        ready() {
            // One rendered frame is not evidence that the initial GPU work survived.
            healthyTimer = setTimeout(clear, 30000);
        },
        retry() { clear(); },
        interrupted() { return pending; }
    });
})();
