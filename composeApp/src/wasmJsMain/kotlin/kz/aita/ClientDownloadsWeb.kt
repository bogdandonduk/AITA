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
    (async () => {
      let writer = null, reader = null;
      const controller = new AbortController();
      let timer;
      const armTimeout = () => { clearTimeout(timer); timer = setTimeout(() => controller.abort(), 30000); };
      try {
        const handle = await selection;
        armTimeout();
        const response = await fetch(url, {credentials:'omit',redirect:'error',cache:'no-store',signal:controller.signal});
        if (!response.ok || !response.body) throw Error('network');
        const declared = response.headers.get('Content-Length');
        if (declared !== null && Number(declared) !== expected) throw Error('integrity');
        reader = response.body.getReader();
        const chunks = []; let count = 0, reported = 0;
        while (true) {
          const chunk = await reader.read();
          if (chunk.done) break;
          armTimeout(); count += chunk.value.byteLength;
          if (count > expected) throw Error('integrity');
          chunks.push(chunk.value);
          if (count - reported >= 262144) { progress(count,expected); reported = count; }
        }
        clearTimeout(timer);
        if (count !== expected) throw Error('integrity');
        const blob = new Blob(chunks, {type:'application/octet-stream'}); chunks.length = 0;
        const digest = await crypto.subtle.digest('SHA-256',await blob.arrayBuffer());
        const actual = Array.from(new Uint8Array(digest), b => b.toString(16).padStart(2,'0')).join('');
        if (actual !== hash) throw Error('integrity');
        if (handle) {
          writer = await handle.createWritable(); await writer.write(blob); await writer.close(); writer = null;
        } else {
          const objectUrl = URL.createObjectURL(blob);
          const link = document.createElement('a'); link.href=objectUrl; link.download=fileName; link.style.display='none';
          document.body.appendChild(link); link.click(); link.remove();
          setTimeout(() => URL.revokeObjectURL(objectUrl),60000);
        }
        progress(count,expected); result('');
      } catch (error) {
        if (writer) { try { await writer.abort(); } catch (_) {} }
        result(error && error.name === 'AbortError' && !controller.signal.aborted ? 'cancelled' :
          error && ['integrity','network'].includes(error.message) ? error.message :
          controller.signal.aborted || error && error.name === 'TypeError' ? 'network' : 'storage');
      } finally {
        clearTimeout(timer); controller.abort();
        if (reader) { try { await reader.cancel(); } catch (_) {} }
      }
    })();
}""")
internal actual fun clientDownloadsCanChooseFolder() = false
internal actual suspend fun clientDownloadsFolderLabel(folder: String?): String? = null
@Composable internal actual fun rememberDownloadsFolderPicker(onChosen: (String?) -> Unit): () -> Unit = {}
internal actual suspend fun saveClientDownload(file: ClientDownloadFile, fileName: String, folder: String?, progress: (Long, Long) -> Unit): ClientDownloadResult =
    suspendCancellableCoroutine { continuation ->
        saveDownloadWeb(file.url, file.bytes.toDouble(), file.sha256, fileName, { count, total -> progress(count.toLong(), total.toLong()) }) { result ->
            if (continuation.isActive) when (result) {
                "" -> continuation.resume(ClientDownloadResult())
                "cancelled" -> continuation.resume(ClientDownloadResult(cancelled = true))
                else -> continuation.resumeWithException(ClientUpdateFailure(result))
            }
        }
    }
