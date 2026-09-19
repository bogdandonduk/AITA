package kz.aita

import kz.aita.updates.*
import kotlinx.browser.localStorage
import kotlinx.browser.window
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.io.encoding.Base64

private fun webVerify(payload: String, signature: String, key: String, result: (Boolean)->Unit): Unit = js("""{
    const bytes = s => Uint8Array.from(atob(s), c => c.charCodeAt(0));
    if (!globalThis.crypto || !globalThis.crypto.subtle) { result(false); return; }
    crypto.subtle.importKey('spki', bytes(key), {name:'RSASSA-PKCS1-v1_5',hash:'SHA-256'}, false, ['verify'])
      .then(k => crypto.subtle.verify('RSASSA-PKCS1-v1_5',k,bytes(signature),bytes(payload)))
      .then(ok => result(ok), () => result(false));
}""")
private fun webInstall(releaseUrl: String, build: String, result: (Boolean)->Unit): Unit = js("""{
    let finished = false, timer, listener, installing, stateListener;
    const done = ok => {
      if (finished) return false;
      finished = true;
      if (timer) clearTimeout(timer);
      if (installing && stateListener) installing.removeEventListener('statechange', stateListener);
      if (listener && 'serviceWorker' in navigator) navigator.serviceWorker.removeEventListener('controllerchange', listener);
      result(ok); return true;
    };
    try {
      const target = new URL(releaseUrl);
      if (target.origin !== location.origin) { done(false); return; }
      // Never clear localStorage, IndexedDB, or unrelated caches; only refresh the current app URL.
      const refresh = () => {
        if (finished) return;
        const next = new URL(location.href); next.searchParams.set('_aita_build',build);
        next.searchParams.set('_aita_refresh', Date.now().toString());
        if (done(true)) location.replace(next.href);
      };
      timer = setTimeout(() => done(false), 15000);
      if ('serviceWorker' in navigator) {
        navigator.serviceWorker.getRegistration().then(async registration => {
          if (finished) return;
          if (registration) {
            await registration.update();
            if (finished) return;
            const activateOrRefresh = () => {
              if (finished) return;
              const waiting = registration.waiting || (installing && installing.state === 'installed' ? installing : null);
              if (waiting) {
                listener = refresh;
                navigator.serviceWorker.addEventListener('controllerchange',listener,{once:true});
                // Only an opt-in worker implementing this protocol may activate a waiting build.
                waiting.postMessage({type:'AITA_ACTIVATE_UPDATE'});
              } else refresh();
            };
            installing = registration.installing;
            if (installing) {
              // update() can resolve before the new worker finishes its install event.
              stateListener = () => {
                if (finished) return;
                if (installing.state === 'redundant') { done(false); return; }
                if (installing.state === 'installed' || installing.state === 'activated') {
                  installing.removeEventListener('statechange',stateListener);
                  activateOrRefresh();
                }
              };
              installing.addEventListener('statechange',stateListener);
              stateListener();
              return;
            }
            activateOrRefresh();
            return;
          }
          refresh();
        }).catch(() => done(false));
      } else refresh();
    } catch (_) { done(false); }
}""")
internal actual fun clientUpdatePlatform() = ClientPlatform(ClientOs.WEB,ClientArch.UNIVERSAL,description="${window.navigator.platform} · Web/Wasm")
internal actual fun installedClientBuild() = GeneratedClientBuild.identity
internal actual suspend fun verifyClientReleaseSignature(payload: ByteArray,signature: ByteArray,publicKey: ByteArray): Boolean = suspendCancellableCoroutine { c ->
    webVerify(Base64.encode(payload),Base64.encode(signature),Base64.encode(publicKey)) { if(c.isActive) c.resume(it) }
}
internal actual suspend fun readClientUpdatePreference(key: String): String? = localStorage.getItem("aita.client-updates.$key")
internal actual suspend fun writeClientUpdatePreference(key: String,value: String?) {
    if(value==null) localStorage.removeItem("aita.client-updates.$key") else localStorage.setItem("aita.client-updates.$key",value)
}
internal actual suspend fun prepareClientInstaller(release: ClientRelease,artifact: ClientArtifact,progress: (Long,Long)->Unit): PreparedClientInstaller = throw ClientUpdateFailure("unsupported")
internal actual suspend fun restoreClientInstaller(release: ClientRelease,artifact: ClientArtifact): PreparedClientInstaller? = null
internal actual suspend fun cleanCompletedClientInstallers(installed: ClientBuildIdentity) = Unit
internal actual suspend fun handoffClientUpdate(release: ClientRelease,artifact: ClientArtifact,prepared: PreparedClientInstaller?): UpdateHandoff {
    if(artifact.kind!=InstallerKind.WEB_RELOAD || artifact!=selectClientArtifact(release,clientUpdatePlatform()) || !clientReleaseIsNewer(release,installedClientBuild())) throw ClientUpdateFailure("integrity")
    AppStateWorkspace.flush()
    flushCartsBeforeClientUpdate()
    val ok = suspendCancellableCoroutine<Boolean> { c -> webInstall(artifact.url,release.build.toString()) { if(c.isActive) c.resume(it) } }
    if(!ok) throw ClientUpdateFailure("unavailable")
    return UpdateHandoff.RELOADING
}

internal actual fun clientInstallerPermissionGranted() = false
internal actual suspend fun handoffClientDownload(request: ClientDownloadInstallRequest, prepared: PreparedClientInstaller): UpdateHandoff =
    throw ClientUpdateFailure("unsupported")
