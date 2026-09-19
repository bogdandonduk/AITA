/* Print only the prepared document. The application and its drafts stay open. */
window.aitaPrintDocument = function (title, html) {
    return new Promise((resolve, reject) => {
        const frame = document.createElement('iframe');
        frame.title = title;
        frame.setAttribute('aria-hidden', 'true');
        // Generated labels may contain an old auto-print script. Only this parent owns
        // printing; scripts in the document are disabled, even if input escaping regresses.
        frame.setAttribute('sandbox', 'allow-same-origin allow-modals');
        frame.style.cssText = 'position:fixed;left:-10000px;top:0;width:800px;height:600px;border:0';
        let finished = false;
        const cleanup = () => { clearTimeout(timeout); frame.remove(); };
        const fail = () => { if (!finished) { finished = true; cleanup(); reject(new Error('print-unavailable')); } };
        const timeout = setTimeout(fail, 30000);
        frame.onerror = fail;
        frame.onload = async () => {
            try {
                const target = frame.contentWindow;
                const content = frame.contentDocument;
                if (!target || !content) return fail();
                content.title = title;
                const fontStyle = content.createElement('style');
                const fontBase = new URL('/composeResources/aita.composeapp.generated.resources/font/', location.href).href;
                fontStyle.textContent = '@font-face{font-family:AITAWebFont;src:url("'+fontBase+'noto_sans_regular.ttf")}'+
                    '@font-face{font-family:AITAWebFont;font-weight:700;src:url("'+fontBase+'noto_sans_bold.ttf")}'+
                    'body{font-family:AITAWebFont,Arial,sans-serif!important}';
                content.head.appendChild(fontStyle);
                await content.fonts.load('12px AITAWebFont');
                await content.fonts.load('bold 12px AITAWebFont');
                await content.fonts.ready;
                if (finished) return;
                // Browser print destinations are private to the print dialog. We provide
                // the physical roll geometry, never infer a printer type from an A4 default.
                const receiptPaper = content.querySelector('meta[name="aita-receipt-paper"]');
                if (receiptPaper) {
                    const [width, margin, requestedLimit = 842] = receiptPaper.content.split(',').map(Number);
                    if (!Number.isFinite(width) || width < 150 || width > 240 ||
                        !Number.isFinite(margin) || margin < 0 || margin > 24 ||
                        !Number.isFinite(requestedLimit) || requestedLimit < 72 || requestedLimit > 842) return fail();
                    // Measure content, never scrollHeight: it can include the iframe viewport.
                    // Long receipts paginate at the cap; they never request metres of blank paper.
                    let printable = content.getElementById('aita-print-content');
                    if (!printable) {
                        printable = content.createElement('main');
                        printable.style.display = 'flow-root';
                        while (content.body.firstChild) printable.appendChild(content.body.firstChild);
                        content.body.appendChild(printable);
                    }
                    const measured = printable.getBoundingClientRect().height * 72 / 96;
                    if (!Number.isFinite(measured) || measured <= 0) return fail();
                    const height = Math.min(requestedLimit, Math.max(72, Math.ceil(measured + margin * 2 + 2)));
                    const paperStyle = content.createElement('style');
                    paperStyle.textContent = '@page{size:'+width+'pt '+height+'pt;margin:'+margin+'pt}';
                    content.head.appendChild(paperStyle);
                }
                target.addEventListener('afterprint', cleanup, {once: true});
                target.focus();
                target.print();
                if (finished) return;
                finished = true;
                clearTimeout(timeout);
                // A browser can return before its dialog closes. Keep the document alive.
                setTimeout(cleanup, 300000);
                resolve();
            } catch (_) { fail(); }
        };
        frame.srcdoc = html;
        // ComposeViewport renders the body through a shadow root. Unslotted light-DOM
        // children still load, but have no rendered geometry and can print blank pages.
        (document.body.shadowRoot || document.body).appendChild(frame);
    });
};
