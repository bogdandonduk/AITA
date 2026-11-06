@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package kz.aita

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.io.encoding.Base64
import kotlin.js.*
import kotlinx.coroutines.suspendCancellableCoroutine

private fun inflateStockBytes(input: String, expected: Int, done: (String, String) -> Unit): JsAny =
    js(
        """{
    let reader=null, stopped=false;
    const timer=setTimeout(()=>finish('','file_error'),30000);
    function finish(data,error) { if(stopped)return;stopped=true;clearTimeout(timer);if(reader)reader.cancel().catch(()=>{});done(data,error); }
    (async()=>{
        if(typeof DecompressionStream!=='function'){finish('','file_xlsx_unavailable');return;}
        let stream;
        try{stream=new DecompressionStream('deflate-raw');}catch(_){finish('','file_xlsx_unavailable');return;}
        const bytes=Uint8Array.from(atob(input),c=>c.charCodeAt(0));
        reader=new Blob([bytes]).stream().pipeThrough(stream).getReader();
        const chunks=[];let size=0;
        while(!stopped){const {value,done:ended}=await reader.read();if(ended)break;
            size+=value.length;if(size>expected){finish('','file_limit');return;}chunks.push(value);}
        if(stopped)return;if(size!==expected){finish('','file_format');return;}
        let parts=[];for(const chunk of chunks)for(let i=0;i<chunk.length;i+=8192)parts.push(String.fromCharCode(...chunk.subarray(i,i+8192)));
        finish(btoa(parts.join('')),'');
    })().catch(()=>finish('','file_format'));
    return {cancel:()=>{stopped=true;clearTimeout(timer);if(reader)reader.cancel().catch(()=>{});}};
}"""
    )

private fun stopStockInflate(handle: JsAny): Unit = js("handle.cancel()")

internal actual suspend fun inflateStockFile(bytes: ByteArray, expectedSize: Int): ByteArray =
    suspendCancellableCoroutine { c ->
        if (expectedSize !in 0..8 * 1024 * 1024) {
            c.resumeWithException(StockFileProblem("file_limit"))
            return@suspendCancellableCoroutine
        }
        val handle =
            inflateStockBytes(Base64.encode(bytes), expectedSize) { data, error ->
                if (c.isActive) {
                    if (error.isNotEmpty()) c.resumeWithException(StockFileProblem(error))
                    else
                        try {
                            c.resume(Base64.decode(data))
                        } catch (_: Exception) {
                            c.resumeWithException(StockFileProblem("file_format"))
                        }
                }
            }
        c.invokeOnCancellation { stopStockInflate(handle) }
    }
