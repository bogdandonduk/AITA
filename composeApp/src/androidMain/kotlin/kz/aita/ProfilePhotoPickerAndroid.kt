package kz.aita

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream

@Composable internal actual fun rememberProfilePhotoPicker(title:String,onResult:(ProfilePhotoPick)->Unit):()->Unit {
    val context=LocalContext.current.applicationContext
    val scope=rememberCoroutineScope();val result by rememberUpdatedState(onResult)
    var picking by remember {mutableStateOf(false)}
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {uri->
        picking=false
        if(uri!=null)scope.launch {
            val choice=withContext(Dispatchers.IO) {
                try {
                    val bytes=context.contentResolver.openInputStream(uri)?.use {input->
                        val output=ByteArrayOutputStream();val buffer=ByteArray(16*1024)
                        while(true){currentCoroutineContext().ensureActive();val n=input.read(buffer);if(n<0)break
                            if(output.size()+n>PROFILE_PHOTO_MAX_INPUT_BYTES)return@withContext ProfilePhotoPick(error="size")
                            output.write(buffer,0,n)}
                        output.toByteArray()
                    }
                    if(bytes == null || bytes.isEmpty())ProfilePhotoPick(error="format") else ProfilePhotoPick(bytes)
                }catch(cancel:CancellationException){throw cancel}catch(_:Exception){ProfilePhotoPick(error="format")}
            }
            result(choice)
        }
    }
    return {if(!picking){picking=true;try{launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))}
        catch(_:Exception){picking=false;result(ProfilePhotoPick(error="unavailable"))}}}
}
