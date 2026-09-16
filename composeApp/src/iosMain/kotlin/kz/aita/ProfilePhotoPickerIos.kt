@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package kz.aita

import androidx.compose.runtime.*
import kotlinx.coroutines.*
import kotlinx.cinterop.*
import platform.Foundation.*
import platform.UIKit.*
import platform.darwin.NSObject
import platform.posix.memcpy

/** System Files picker: copies only the explicitly selected JPEG/PNG, without broad library access. */
@Composable internal actual fun rememberProfilePhotoPicker(title:String,onResult:(ProfilePhotoPick)->Unit):()->Unit {
    val scope=rememberCoroutineScope();val result by rememberUpdatedState(onResult)
    var visible by remember {mutableStateOf(false)}
    var picker by remember {mutableStateOf<UIDocumentPickerViewController?>(null)}
    val delegate=remember {
        object:NSObject(),UIDocumentPickerDelegateProtocol {
            override fun documentPickerWasCancelled(controller:UIDocumentPickerViewController){visible=false;picker=null}
            override fun documentPicker(controller:UIDocumentPickerViewController,didPickDocumentsAtURLs:List<*>) {
                visible=false;picker=null
                val url=didPickDocumentsAtURLs.firstOrNull() as? NSURL ?: return
                scope.launch {
                    val selection=withContext(Dispatchers.Default) {
                        val access=url.startAccessingSecurityScopedResource()
                        try {
                            val path=url.path ?: return@withContext ProfilePhotoPick(error="format")
                            val attributes=NSFileManager.defaultManager.attributesOfItemAtPath(path,null)
                            val size=(attributes?.get(NSFileSize) as? NSNumber)?.longLongValue ?: 0L
                            if(size !in 1..PROFILE_PHOTO_MAX_INPUT_BYTES.toLong())return@withContext ProfilePhotoPick(error="size")
                            val data=NSData.dataWithContentsOfURL(url) ?: return@withContext ProfilePhotoPick(error="format")
                            if(data.length !in 1uL..PROFILE_PHOTO_MAX_INPUT_BYTES.toULong())return@withContext ProfilePhotoPick(error="size")
                            val bytes=ByteArray(data.length.toInt())
                            bytes.usePinned {memcpy(it.addressOf(0),data.bytes,data.length)}
                            ProfilePhotoPick(bytes)
                        }finally{if(access)url.stopAccessingSecurityScopedResource()}
                    }
                    result(selection)
                }
            }
        }
    }
    DisposableEffect(Unit){onDispose {picker?.delegate=null;picker?.dismissViewControllerAnimated(false,null);picker=null}}
    return {
        if(!visible) {
            val windows=UIApplication.sharedApplication.connectedScenes.filterIsInstance<UIWindowScene>().flatMap {it.windows.filterIsInstance<UIWindow>()}
            var presenter=(windows.firstOrNull{it.isKeyWindow()} ?: UIApplication.sharedApplication.keyWindow)?.rootViewController
            while(presenter?.presentedViewController!=null)presenter=presenter?.presentedViewController
            if(presenter==null)result(ProfilePhotoPick(error="unavailable"))
            else {
                val selected=UIDocumentPickerViewController(documentTypes=listOf("public.jpeg","public.png"),inMode=UIDocumentPickerMode.UIDocumentPickerModeImport)
                selected.allowsMultipleSelection=false;selected.delegate=delegate;selected.title=title
                picker=selected;visible=true;presenter.presentViewController(selected,true,null)
            }
        }
    }
}
