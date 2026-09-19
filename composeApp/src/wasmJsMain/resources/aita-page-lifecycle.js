/* Pause periodic work while hidden/frozen; never reload a page or discard its drafts. */
(() => {
    let frozen = false;
    let pageHidden = false;
    let active = document.visibilityState === 'visible';
    const listeners = new Set();
    function publish() {
        const next = !frozen && !pageHidden && document.visibilityState === 'visible';
        if (active === next) return;
        active = next;
        for (const listener of listeners) listener(active);
    }
    document.addEventListener('visibilitychange', publish);
    document.addEventListener('freeze', () => { frozen = true; publish(); });
    document.addEventListener('resume', () => { frozen = false; publish(); });
    window.addEventListener('pagehide', () => { pageHidden = true; publish(); });
    window.addEventListener('pageshow', () => { pageHidden = false; frozen = false; publish(); });
    globalThis.aitaPageActivity = Object.freeze({
        subscribe(listener) {
            listeners.add(listener);
            listener(active);
            return () => listeners.delete(listener);
        }
    });
})();
