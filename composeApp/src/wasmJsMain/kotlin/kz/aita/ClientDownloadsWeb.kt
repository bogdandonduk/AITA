package kz.aita

import androidx.compose.runtime.Composable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kz.aita.updates.ClientDownloadFile

private fun saveDownloadWeb(url: String, expected: Double, hash: String, fileName: String,
    progress: (Double, Double) -> Unit, result: (String) -> Unit): Unit = js("""{
    // Keep the native picker inside this direct user gesture, before any network await.
    if (!globalThis.crypto || !crypto.subtle || expected <= 0 || expected > 268435456) { result('unsupported'); return; }
    let selection;
    try { selection = window.showSaveFilePicker ? window.showSaveFilePicker({suggestedName:fileName}) : Promise.resolve(null); }
    catch (_) { result('unavailable'); return; }
    const control = { cancelled: false, abort: () => {} };
    globalThis.aitaActiveDownload = control;
    const deadline = (promise, milliseconds, reason) => new Promise((resolve, reject) => {
      const stop = setTimeout(() => reject(Error(reason)), milliseconds);
      Promise.resolve(promise).then(value => { clearTimeout(stop); resolve(value); }, error => { clearTimeout(stop); reject(error); });
    });
    (async () => {
      let writer = null, reader = null;
      let controller = new AbortController();
      let timer;
      const armTimeout = () => { clearTimeout(timer); timer = setTimeout(() => controller.abort(), 30000); };
      try {
        const handle = await deadline(selection, 300000, 'cancelled');
        let chunks = [], count = 0, reported = 0;
        for (let attempt = 0; attempt < 4; attempt++) {
          if (control.cancelled) throw Error('cancelled');
          controller = new AbortController();
          control.abort = () => controller.abort();
          armTimeout();
          try {
            const response = await fetch(url, {credentials:'omit',redirect:'error',cache:'no-store',signal:controller.signal,
              headers:count > 0 ? {'Range':'bytes='+count+'-'} : {}});
            if (!response.ok || !response.body) throw Error('network');
            const range = response.headers.get('Content-Range');
            if (response.status === 206) {
              const match = range && /^bytes ([0-9]+)-([0-9]+)\/([0-9]+)$/.exec(range);
              if (!match || Number(match[1]) !== count || Number(match[2]) !== expected-1 || Number(match[3]) !== expected) throw Error('integrity');
            } else if (response.status === 200) {
              if (count > 0) { chunks = []; count = 0; reported = 0; progress(0,expected); }
            } else throw Error('network');
            const declared = response.headers.get('Content-Length');
            if (declared !== null && Number(declared) !== expected-count) throw Error('integrity');
            reader = response.body.getReader();
            while (true) {
              const chunk = await reader.read();
              if (chunk.done) break;
              armTimeout(); count += chunk.value.byteLength;
              if (count > expected) throw Error('integrity');
              chunks.push(chunk.value);
              if (count - reported >= 262144) { progress(count,expected); reported = count; }
            }
            if (count !== expected) throw Error('network');
            break;
          } catch (error) {
            if (control.cancelled) throw Error('cancelled');
            if (error && error.message === 'integrity') throw error;
            if (attempt === 3) throw Error('network');
          } finally {
            clearTimeout(timer); controller.abort();
            if (reader) { try { await deadline(reader.cancel(), 1000, 'network'); } catch (_) {} reader = null; }
          }
          await new Promise(resolve => setTimeout(resolve, 500 * (1 << attempt)));
        }
        const blob = new Blob(chunks, {type:'application/octet-stream'}); chunks.length = 0;
        const digest = await deadline(crypto.subtle.digest('SHA-256',await deadline(blob.arrayBuffer(), 60000, 'storage')), 60000, 'storage');
        if (control.cancelled) throw Error('cancelled');
        const actual = Array.from(new Uint8Array(digest), b => b.toString(16).padStart(2,'0')).join('');
        if (actual !== hash) throw Error('integrity');
        if (handle) {
          writer = await deadline(handle.createWritable(), 60000, 'storage');
          await deadline(writer.write(blob), 60000, 'storage'); await deadline(writer.close(), 60000, 'storage'); writer = null;
        } else {
          if (globalThis.aitaReadyDownload) URL.revokeObjectURL(globalThis.aitaReadyDownload.url);
          const objectUrl = URL.createObjectURL(blob);
          globalThis.aitaReadyDownload = { url: objectUrl, fileName };
          const link = document.createElement('a'); link.href=objectUrl; link.download=fileName; link.style.display='none';
          document.body.appendChild(link); link.click(); link.remove();
          setTimeout(() => {
            URL.revokeObjectURL(objectUrl);
            if (globalThis.aitaReadyDownload && globalThis.aitaReadyDownload.url === objectUrl) delete globalThis.aitaReadyDownload;
          },60000);
        }
        progress(count,expected); result(handle ? '' : 'browser');
      } catch (error) {
        if (writer) { try { await deadline(writer.abort(), 1000, 'storage'); } catch (_) {} }
        // Network timeouts are classified inside the transfer loop. Its cleanup also aborts
        // the controller after success, so that signal must not classify picker/disk errors.
        result(error && error.name === 'AbortError' ? 'cancelled' :
          error && ['integrity','network','cancelled'].includes(error.message) ? error.message : 'storage');
      } finally {
        clearTimeout(timer); controller.abort();
        if (globalThis.aitaActiveDownload === control) delete globalThis.aitaActiveDownload;
        if (reader) { try { await deadline(reader.cancel(), 1000, 'network'); } catch (_) {} }
      }
    })();
}""")
private fun cancelActiveWebDownload(): Unit = js("""{
    const active = globalThis.aitaActiveDownload;
    if (active) { active.cancelled = true; active.abort(); }
}""")
private fun repeatVerifiedWebDownload(fileName: String): Boolean = js("""{
    const ready = globalThis.aitaReadyDownload;
    if (!ready || ready.fileName !== fileName) return false;
    const link = document.createElement('a'); link.href = ready.url; link.download = fileName;
    document.body.appendChild(link); link.click(); link.remove(); return true;
}""")
internal actual fun clientDownloadsCanChooseFolder() = false
internal actual suspend fun clientDownloadsFolderLabel(folder: String?): String? = null
@Composable internal actual fun rememberDownloadsFolderPicker(onChosen: (String?) -> Unit): () -> Unit = {}
internal actual suspend fun saveClientDownload(file: ClientDownloadFile, fileName: String, folder: String?, installRequest: ClientDownloadInstallRequest?, progress: (Long, Long) -> Unit): ClientDownloadResult =
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancelActiveWebDownload() }
        saveDownloadWeb(file.url, file.bytes.toDouble(), file.sha256, fileName, { count, total -> progress(count.toLong(), total.toLong()) }) { result ->
            if (continuation.isActive) when (result) {
                "" -> continuation.resume(ClientDownloadResult())
                "browser" -> continuation.resume(ClientDownloadResult(repeatSave = { repeatVerifiedWebDownload(fileName) }))
                "cancelled" -> continuation.resume(ClientDownloadResult(cancelled = true))
                else -> continuation.resumeWithException(ClientUpdateFailure(result))
            }
        }
    }
