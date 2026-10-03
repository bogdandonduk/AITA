/* Lightweight recovery downloads: no Compose, WebGL, user-account access or app-cache writes. */
(() => {
    const language = (navigator.language || 'en').split('-')[0];
    const translations = {
        en: ['AITA for your devices', 'Check for updates', 'Checking the signed release catalogue…', 'Download', 'Could not verify the catalogue. Check your connection and retry.'],
        ru: ['AITA для ваших устройств', 'Проверить обновления', 'Проверяем подпись каталога выпусков…', 'Скачать', 'Не удалось проверить каталог. Проверьте соединение и повторите.'],
        kk: ['Құрылғыларыңызға арналған AITA', 'Жаңартуларды тексеру', 'Шығарылым каталогының қолтаңбасы тексерілуде…', 'Жүктеу', 'Каталог тексерілмеді. Байланысты тексеріп, қайталаңыз.'],
        ky: ['Түзмөктөрүңүз үчүн AITA', 'Жаңыртууларды текшерүү', 'Чыгарылыш каталогунун кол тамгасы текшерилүүдө…', 'Жүктөө', 'Каталог текшерилген жок. Байланышты текшерип, кайталаңыз.'],
        tg: ['AITA барои дастгоҳҳои шумо', 'Санҷиши навсозиҳо', 'Имзои феҳристи нашрҳо санҷида мешавад…', 'Боргирӣ', 'Феҳрист санҷида нашуд. Пайвастшавиро санҷида, такрор кунед.'],
        uz: ['Qurilmalaringiz uchun AITA', 'Yangilanishlarni tekshirish', 'Nashrlar katalogi imzosi tekshirilmoqda…', 'Yuklab olish', 'Katalog tekshirilmadi. Ulanishni tekshirib, qayta urining.']
    };
    const words = translations[language] || translations.en;
    document.documentElement.lang = language;
    document.getElementById('intro').textContent = words[0];
    const button = document.getElementById('refresh'), status = document.getElementById('status'), files = document.getElementById('files');
    button.textContent = words[1];
    const feed = 'https://aita-api.bogdan-donduk.workers.dev/client-updates/';
    const bytes = value => Uint8Array.from(atob(value), c => c.charCodeAt(0));
    async function read(url, limit) {
        const response = await fetch(url, {cache: 'no-store', credentials: 'omit', signal: AbortSignal.timeout(20000)});
        if (!response.ok) throw Error('network');
        const text = await response.text();
        if (text.length > limit) throw Error('size');
        return JSON.parse(text);
    }
    async function refresh() {
        button.disabled = true; files.replaceChildren(); status.textContent = words[2];
        try {
            const [keyInfo, envelope] = await Promise.all([read('downloads-public-key.json', 4096), read(feed + 'release.json', 300000)]);
            const key = await crypto.subtle.importKey('spki', bytes(keyInfo.spki), {name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256'}, false, ['verify']);
            const payload = bytes(envelope.payload);
            if (!await crypto.subtle.verify('RSASSA-PKCS1-v1_5', key, bytes(envelope.signature), payload)) throw Error('signature');
            const release = JSON.parse(new TextDecoder().decode(payload));
            const now = Date.now();
            if (release.schema !== 1 || release.channel !== 'RELEASE' || !Number.isSafeInteger(release.expiresAtMillis) || release.expiresAtMillis <= now || release.publishedAtMillis > now + 600000) throw Error('expired');
            const entries = release.artifacts.filter(file => ['APK', 'EXE', 'MSI', 'DEB', 'RPM'].includes(file.kind));
            if (!entries.length || entries.length > 20) throw Error('artifacts');
            for (const file of entries) {
                const url = new URL(file.url);
                if (!url.href.startsWith(feed + 'artifacts/') || url.username || url.password || !/^[a-f0-9]{64}$/.test(file.sha256) || !Number.isSafeInteger(file.bytes) || file.bytes <= 0) throw Error('artifact');
            }
            status.textContent = `${release.version} · ${release.build}`;
            for (const file of entries) {
                const tile = document.createElement('article'), title = document.createElement('h2'), link = document.createElement('a'), hash = document.createElement('small');
                title.textContent = `${file.os} · ${file.arch} · ${file.kind}`;
                link.textContent = `${words[3]} · ${(file.bytes / 1048576).toFixed(1)} MiB`;
                link.href = file.url; link.rel = 'noopener';
                hash.textContent = `SHA-256: ${file.sha256}`;
                tile.append(title, link, hash); files.append(tile);
            }
        } catch (_) { files.replaceChildren(); status.textContent = words[4]; }
        finally { button.disabled = false; }
    }
    button.addEventListener('click', refresh); refresh();
})();
