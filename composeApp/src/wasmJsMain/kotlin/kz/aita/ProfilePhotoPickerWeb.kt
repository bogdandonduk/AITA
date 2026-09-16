@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
package kz.aita

import androidx.compose.runtime.*
import kotlin.io.encoding.Base64
import kotlin.js.*

// Kept synchronous with the button's gesture, as required by browser file selection.
private fun selectProfilePhoto(result:(String,String)->Unit):Unit=js("""{
    const input=document.createElement('input');
    input.type='file';input.accept='image/jpeg,image/png';input.style.display='none';
    let finished=false, reader=null, reading=false, timer=null;
    const finish=(value,error)=>{
        if(finished)return;finished=true;
        if(timer)clearTimeout(timer);
        window.removeEventListener('focus',focus);
        input.remove();result(value,error);
    };
    const focus=()=>{timer=setTimeout(()=>{if(!reading && !input.files?.length)finish('','');},700);};
    input.addEventListener('cancel',()=>finish('',''));
    input.addEventListener('change',()=>{
        const file=input.files?.[0];if(!file){finish('','');return;}
        reading=true;
        if(file.size<=0 || file.size>8388608){finish('','size');return;}
        if(file.type && file.type!=='image/jpeg' && file.type!=='image/png'){finish('','format');return;}
        reader=new FileReader();reader.onerror=()=>finish('','format');reader.onabort=()=>finish('','');
        reader.onload=()=>{const value=String(reader.result);finish(value.slice(value.indexOf(',')+1),'');};
        reader.readAsDataURL(file);
    },{once:true});
    document.body.appendChild(input);window.addEventListener('focus',focus);
    try{input.click();}catch(_){finish('','unavailable');}
}""")
@Composable internal actual fun rememberProfilePhotoPicker(title:String,onResult:(ProfilePhotoPick)->Unit):()->Unit {
    val result by rememberUpdatedState(onResult)
    var active by remember {mutableStateOf(false)}
    var alive by remember {mutableStateOf(true)}
    DisposableEffect(Unit){onDispose{alive=false}}
    return {if(!active){active=true;selectProfilePhoto {data,error->
        if(alive) {
            active=false
            if(error.isNotEmpty())result(ProfilePhotoPick(error=error))
            else if(data.isNotEmpty())result(runCatching{ProfilePhotoPick(Base64.decode(data))}.getOrElse{ProfilePhotoPick(error="format")})
        }
    }}}
}
