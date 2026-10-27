/* Chrome 122 can SIGSEGV in V8's Finch-enabled WebAssemblyGenericWrapper path.
 * Reproduced on the release bundle with --enable-features=WebAssemblyGenericWrapper,
 * without extensions or app storage. Feature detection alone cannot catch it, and a
 * renderer crash cannot be recovered by a JavaScript error handler.
 * Keep this check outside Wasm, before any application/database initialization. */
(() => {
    function needsBrowserUpdate(userAgent) {
        // Chromium-derived browsers include their engine's Chrome/ version. CriOS
        // is WebKit, not Chromium; unknown engines still use the feature checks.
        const match = /\b(?:Chrome|Chromium)\/(\d+)\./.exec(userAgent || '');
        return !!match && Number(match[1]) < 123;
    }

    function hasGraphics(document) {
        let context;
        try {
            context = document.createElement('canvas').getContext('webgl2');
            return !!context && !context.isContextLost();
        } catch (_) {
            return false;
        } finally {
            // This is only a capability probe, never the application's canvas.
            // Release its GPU resources before Skiko creates the real context.
            try { context?.getExtension('WEBGL_lose_context')?.loseContext(); } catch (_) {}
        }
    }

    globalThis.aitaBrowserCompatibility = Object.freeze({needsBrowserUpdate, hasGraphics});
})();
