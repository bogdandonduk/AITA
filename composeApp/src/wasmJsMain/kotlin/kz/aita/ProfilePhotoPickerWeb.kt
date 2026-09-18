@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package kz.aita

import androidx.compose.runtime.*
import kotlin.io.encoding.Base64
import kotlin.js.*

// Kept synchronous with the button's gesture, as required by browser file selection.
private fun selectProfilePhoto(result:(String,String)->Unit):JsAny=js("""{
    const input=document.createElement('input');
    input.type='file';input.accept='image/jpeg,image/png';
    input.style.cssText='position:fixed;left:-10000px;top:0;width:1px;height:1px';
    let finished=false, reader=null, reading=false, focusTimer=null, deadline=null;
    const finish=(value,error,notify=true)=>{
        if(finished)return;finished=true;
        clearTimeout(focusTimer);clearTimeout(deadline);
        window.removeEventListener('focus',focus);
        if(reader){reader.onload=null;reader.onerror=null;reader.onabort=null;if(reader.readyState===1)reader.abort();}
        input.remove();if(notify)result(value,error);
    };
    const focus=()=>{
        clearTimeout(focusTimer);
        focusTimer=setTimeout(()=>{if(!reading && !input.files?.length)finish('','');},1000);
    };
    input.addEventListener('cancel',()=>finish('',''));
    input.addEventListener('change',()=>{
        const file=input.files?.[0];if(!file){finish('','');return;}
        reading=true;clearTimeout(focusTimer);clearTimeout(deadline);
        deadline=setTimeout(()=>finish('','unavailable'),60000);
        if(file.size<=0 || file.size>8388608){finish('','size');return;}
        if(file.type && file.type!=='image/jpeg' && file.type!=='image/png'){finish('','format');return;}
        reader=new FileReader();reader.onerror=()=>finish('','format');reader.onabort=()=>finish('','');
        reader.onload=()=>{const value=String(reader.result);finish(value.slice(value.indexOf(',')+1),'');};
        reader.readAsDataURL(file);
    },{once:true});
    document.body.appendChild(input);window.addEventListener('focus',focus);
    // showPicker reports blocked user activation instead of silently doing nothing.
    // The deadline also recovers browsers that omit both cancel and focus events.
    deadline=setTimeout(()=>finish('','unavailable'),300000);
    try{if(typeof input.showPicker==='function')input.showPicker();else input.click();}
    catch(_){finish('','unavailable');}
    return {cancel:()=>finish('','',false)};
}""")
private fun cancelProfilePhotoPicker(handle:JsAny):Unit=js("handle.cancel()")
@Composable internal actual fun rememberProfilePhotoPicker(title:String,onResult:(ProfilePhotoPick)->Unit):()->Unit {
    val result by rememberUpdatedState(onResult)
    var active by remember {mutableStateOf(false)}
    var alive by remember {mutableStateOf(true)}
    val handle=remember {arrayOfNulls<JsAny>(1)}
    DisposableEffect(Unit){onDispose{alive=false;handle[0]?.let(::cancelProfilePhotoPicker);handle[0]=null}}
    return {if(!active){
        handle[0]?.let(::cancelProfilePhotoPicker)
        active=true
        handle[0]=selectProfilePhoto {data,error->
            if(alive) {
                active=false
                if(error.isNotEmpty())result(ProfilePhotoPick(error=error))
                else if(data.isNotEmpty())result(runCatching{ProfilePhotoPick(Base64.decode(data))}.getOrElse{ProfilePhotoPick(error="format")})
            }
        }
    }}
}
