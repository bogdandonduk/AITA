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
            // Match Skiko's context. The default browser probe requests MSAA,
            // which can fail on devices where the actual non-MSAA renderer works.
            context = document.createElement('canvas').getContext('webgl2', {
                alpha: true, depth: true, stencil: true, antialias: false,
                premultipliedAlpha: true, preserveDrawingBuffer: false,
                failIfMajorPerformanceCaveat: false
            });
            return !!context && !context.isContextLost();
        } catch (_) {
            return false;
        } finally {
            // This is only a capability probe, never the application's canvas.
            // Release its GPU resources before Skiko creates the real context.
            try { context?.getExtension('WEBGL_lose_context')?.loseContext(); } catch (_) {}
        }
    }

    function startupIssue(environment) {
        const {navigator, document} = environment;
        if (needsBrowserUpdate(navigator?.userAgent)) return 'engine';
        if (!environment.isSecureContext) return 'secure';
        try {
            const wasm = environment.WebAssembly;
            if (typeof wasm !== 'object' || typeof wasm.Tag !== 'function' ||
                !wasm.validate(new Uint8Array([0,97,115,109,1,0,0,0,1,3,1,95,0]))) return 'wasm';
        } catch (_) { return 'wasm'; }
        try {
            if (!navigator?.locks || !environment.crypto?.subtle || !environment.indexedDB) return 'storage';
        } catch (_) { return 'storage'; }
        return hasGraphics(document) ? null : 'graphics';
    }

    globalThis.aitaBrowserCompatibility = Object.freeze({needsBrowserUpdate, hasGraphics, startupIssue});
})();
