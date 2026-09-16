package kz.aita

import androidx.compose.runtime.*
import kotlinx.coroutines.*
import java.awt.FileDialog
import java.awt.Frame
import java.nio.file.Files
import javax.swing.SwingUtilities
import java.io.ByteArrayOutputStream

@Composable internal actual fun rememberProfilePhotoPicker(title:String,onResult:(ProfilePhotoPick)->Unit):()->Unit {
    val scope=rememberCoroutineScope();val result by rememberUpdatedState(onResult)
    var active by remember {mutableStateOf(false)}
    val dialog=remember {arrayOfNulls<FileDialog>(1)}
    DisposableEffect(Unit){onDispose{SwingUtilities.invokeLater{dialog[0]?.dispose();dialog[0]=null}}}
    return {
        if(!active){active=true
            SwingUtilities.invokeLater {
                try {
                    val picker=FileDialog(null as Frame?,title,FileDialog.LOAD);dialog[0]=picker
                    picker.isMultipleMode=false
                    picker.setFilenameFilter {_,name->name.substringAfterLast('.').lowercase() in setOf("jpg","jpeg","png")}
                    picker.isVisible=true
                    val selected=picker.files.firstOrNull()?.toPath();picker.dispose();dialog[0]=null
                    scope.launch {
                        try {
                            if(selected!=null) {
                                val chosen=withContext(Dispatchers.IO) {
                                    if(!Files.isRegularFile(selected) || Files.size(selected)>PROFILE_PHOTO_MAX_INPUT_BYTES)ProfilePhotoPick(error="size")
                                    else Files.newInputStream(selected).use {input->
                                        val output=ByteArrayOutputStream();val buf=ByteArray(16384)
                                        while(true){currentCoroutineContext().ensureActive();val n=input.read(buf);if(n<0)break
                                            if(output.size()+n>PROFILE_PHOTO_MAX_INPUT_BYTES)return@withContext ProfilePhotoPick(error="size")
                                            output.write(buf,0,n)}
                                        ProfilePhotoPick(output.toByteArray())
                                    }
                                }
                                result(chosen)
                            }
                        }catch(cancel:CancellationException){throw cancel}
                        catch(_:Exception){result(ProfilePhotoPick(error="format"))}
                        finally{active=false}
                    }
                }catch(_:Exception){scope.launch{active=false;result(ProfilePhotoPick(error="unavailable"))}}
            }
        }
    }
}
