@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package kz.aita

import androidx.compose.runtime.*
import kotlin.js.*

private fun watchCourierPosition(position: (Double,Double,Double,Double)->Unit,error: (String)->Unit): JsAny = js("""{
    let watch=null,closed=false;
    const stop=()=>{if(watch!==null){navigator.geolocation?.clearWatch(watch);watch=null;}};
    const start=()=>{
        if(closed||document.hidden||watch!==null)return;
        if(!navigator.geolocation){error('location_unavailable');return;}
        watch=navigator.geolocation.watchPosition(p=>{if(!closed&&!document.hidden)position(p.coords.latitude,p.coords.longitude,p.coords.accuracy,p.timestamp);},
            e=>{if(!closed)error(e.code===1?'location_denied':'location_unavailable');},
            {enableHighAccuracy:true,maximumAge:10000,timeout:20000});
    };
    const visibility=()=>{if(document.hidden)stop();else start();};
    document.addEventListener('visibilitychange',visibility);start();
    return {close:()=>{closed=true;stop();document.removeEventListener('visibilitychange',visibility);}};
}""")
private fun stopCourierPosition(handle: JsAny): Unit = js("handle.close()")

@Composable internal actual fun CourierLocationSharing(enabled: Boolean,onPosition: (MarketCourierPosition)->Unit,onError: (String)->Unit) {
    val position by rememberUpdatedState(onPosition)
    val error by rememberUpdatedState(onError)
    DisposableEffect(enabled) {
        val handle=if(enabled) watchCourierPosition({lat,lon,accuracy,time->position(MarketCourierPosition(lat,lon,accuracy,time.toLong()))}, {error(it)}) else null
        onDispose { handle?.let(::stopCourierPosition) }
    }
}
