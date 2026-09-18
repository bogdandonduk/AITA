package kz.aita

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import aita.composeapp.generated.resources.*
import androidx.compose.material3.*
import org.jetbrains.compose.resources.DrawableResource
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.compose.resources.decodeToImageBitmap

internal data class ProfilePhotoPick(val bytes:ByteArray?=null,val error:String?=null)
@Composable internal expect fun rememberProfilePhotoPicker(title:String,onResult:(ProfilePhotoPick)->Unit):()->Unit
internal data class PhotoEditorState(val saved:ProfilePhotoSnapshot?=null,val preview:String?=null,val busy:Boolean=false,
    val error:String?=null,val notice:String?=null)
internal interface PhotoEditorBackend {
    suspend fun load():ResponseDataModel<ProfilePhotoSnapshot>
    suspend fun preview(bytes:ByteArray):ResponseDataModel<ProfilePhotoPreview>
    suspend fun change(value:ProfilePhotoChange):ResponseDataModel<ProfilePhotoSnapshot>
}
/** Screen-owned operations; every acknowledgement belongs to the exact account/session that opened it. */
internal class PhotoEditor(private val owner:String,private val current:()->Boolean,
    private val scope:CoroutineScope,private val backend:PhotoEditorBackend,private val mode:ProfilePhotoMode?=null) {
    private val mutable=MutableStateFlow(PhotoEditorState())
    val state=mutable.asStateFlow()
    private fun error(response:ResponseDataModel<*>)=when(response.httpStatusCode) {
        404,405,501,503->"unavailable";409->"conflict";413->"size";400,422->"format";429->"busy";else->"network"
    }
    private fun run(block:suspend ()->Unit) {
        if(!current() || mutable.value.busy)return
        mutable.value=mutable.value.copy(busy=true,error=null,notice=null)
        scope.launch {
            try {withTimeout(60_000) {block()}}
            catch(_:TimeoutCancellationException){if(current())mutable.value=mutable.value.copy(error="network")}
            catch(cancel:CancellationException){throw cancel}
            catch(_:Exception){if(current())mutable.value=mutable.value.copy(error="network")}
            finally {if(current())mutable.value=mutable.value.copy(busy=false)}
        }
    }
    fun load()=run {
        val response=backend.load(); if(!current())return@run
        val photo=response.payload
        mutable.value=if(!response.negative && photo!=null && validProfilePhotoSnapshot(photo,owner,mode))
            mutable.value.copy(saved=photo,preview=null) else mutable.value.copy(error=error(response))
    }
    fun select(pick:ProfilePhotoPick) {
        if(!current() || mutable.value.busy)return
        if(pick.error!=null){mutable.value=mutable.value.copy(error=pick.error);return}
        val bytes=pick.bytes ?: return
        if(bytes.size !in 1..PROFILE_PHOTO_MAX_INPUT_BYTES){mutable.value=mutable.value.copy(error="size");return}
        run {
            // A failed initial read must not silently disable choosing a picture. Retry the
            // revision read here; never invent revision zero or overwrite an unseen photo.
            if (mutable.value.saved == null) {
                val loaded = backend.load()
                if (!current()) return@run
                val saved = loaded.payload
                if (loaded.negative || saved == null || !validProfilePhotoSnapshot(saved, owner, mode)) {
                    mutable.value = mutable.value.copy(error = error(loaded))
                    return@run
                }
                mutable.value = mutable.value.copy(saved = saved)
            }
            val response=backend.preview(bytes);if(!current())return@run
            val value=response.payload?.jpegBase64
            mutable.value=if(!response.negative && profilePhotoJpegBytes(value)!=null)
                mutable.value.copy(preview=value) else mutable.value.copy(error=error(response))
        }
    }
    fun discard(){if(current() && !mutable.value.busy)mutable.value=mutable.value.copy(preview=null,error=null,notice=null)}
    fun save(remove:Boolean=false) {
        val before=mutable.value;val saved=before.saved ?: return
        if(!remove && before.preview==null)return
        val request=ProfilePhotoChange(saved.revision,if(remove)null else before.preview)
        run {
            val response=backend.change(request);if(!current())return@run
            val result=response.payload
            if(result!=null && validProfilePhotoSnapshot(result,owner,mode) && result.revision>=saved.revision &&
                (if(response.negative) response.httpStatusCode==409 else profilePhotoAcknowledges(request,result,owner,mode))) {
                // A conflict must be reviewed afresh; never turn its new revision into an automatic overwrite.
                mutable.value=mutable.value.copy(saved=result,preview=null,
                    error=if(response.negative)"conflict" else null,notice=if(response.negative)null else if(remove)"removed" else "saved")
            } else mutable.value=mutable.value.copy(error=error(response))
        }
    }
}

@Composable internal fun AppConfiguration.UserProfilePhotoCard() {
    CompositionLocalProvider(LocalLoadingAnimationsEnabled provides false) { UserProfilePhotoContent() }
}

@Composable private fun AppConfiguration.UserProfilePhotoContent() {
    val owner=stateValues.userAccount?.id ?: return
    val generation=currentAuthenticatedSessionGeneration()
    val mode=profilePhotoModeForApp(stateValues.appModeId) ?: return
    key(owner,generation,mode) {
        val scope=rememberCoroutineScope()
        val editor=remember {PhotoEditor(owner,{
            authenticatedSessionGenerationIsCurrent(generation) && userAccountState.payloadValue?.id==owner && profilePhotoModeForApp(appModeState.value)==mode
        },scope,object:PhotoEditorBackend {
            override suspend fun load()=ProfilePhotoClient.load(generation,mode)
            override suspend fun preview(bytes:ByteArray)=ProfilePhotoClient.preview(bytes,generation,mode)
            override suspend fun change(value:ProfilePhotoChange)=ProfilePhotoClient.save(value,generation,mode)
        }, mode)}
        val state by editor.state.collectAsState()
        val photoRevision by ProfilePhotoSignals.revision.collectAsState()
        var observedPhotoRevision by remember {mutableStateOf(photoRevision)}
        LaunchedEffect(editor){editor.load()}
        LaunchedEffect(photoRevision,state.preview,state.busy) {
            // Keep a reviewed local edit tied to its original revision; otherwise a push
            // could silently upgrade its CAS revision and overwrite another device's photo.
            if(shouldRefreshProfilePhoto(photoRevision,observedPhotoRevision,state.preview!=null,state.busy)) {
                observedPhotoRevision=photoRevision;editor.load()
            }
        }
        val pick=rememberProfilePhotoPicker(accountPresentationText("photo.choose"),editor::select)
        val picture=state.preview ?: state.saved?.jpegBase64
        val bitmap by produceState<ImageBitmap?>(null,picture) {
            value=null
            value=withContext(Dispatchers.Default) {runCatching {profilePhotoJpegBytes(picture)?.decodeToImageBitmap()}.getOrNull()}
        }
        Column(Modifier.fillMaxWidth().padding(bottom=20.dp).clip(RoundedCornerShape(stateValues.cornerRadius))
            .background(stateValues.BackgroundColor)
            .border(stateValues.unfocusedBorderWidth,stateValues.PlaceholderTextColor.copy(alpha=.45f),RoundedCornerShape(stateValues.cornerRadius))
            .padding(18.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(accountPresentationText("photo.title"),color=stateValues.TextColor,fontSize=stateValues.accentTextSize,fontWeight=FontWeight.Bold)
            var confirmRemove by remember(state.saved?.revision) { mutableStateOf(false) }
            Box(Modifier.size(176.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(112.dp).clip(CircleShape).background(stateValues.AccentColor.copy(alpha=.10f))
                    .border(2.dp,stateValues.AccentColor.copy(alpha=.6f),CircleShape),contentAlignment=Alignment.Center) {
                    val decoded=bitmap
                    if(decoded!=null)Image(decoded,accountPresentationText("photo.title"),Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                    else CpImage(Modifier.size(52.dp),url=stateValues.drawablePathIconPerson,fallbackRes=stateValues.drawableResIconPerson.value,
                        contentDescription=accountPresentationText("photo.title"),tintColor=stateValues.PlaceholderTextColor)
                }
                val hasPicture = picture != null
                PhotoRingAction(Modifier.align(Alignment.TopCenter),
                    accountPresentationText(if (hasPicture) "photo.change" else "photo.choose"),
                    if (hasPicture) stateValues.drawablePathIconEdit else uiAppearanceResourcesState.value.catalog.drawable(215L, stateValues.appThemeId),
                    if (hasPicture) stateValues.drawableResIconEdit.value else if (isDarkAppTheme(stateValues.appThemeId)) Res.drawable._215_1 else Res.drawable._215_0,
                    enabled = !state.busy, onClick = pick)
                if (state.preview != null) {
                    PhotoRingAction(Modifier.align(Alignment.CenterEnd), accountPresentationText("photo.save"),
                        stateValues.drawablePathIconCheck, stateValues.drawableResIconCheck.value,
                        enabled = !state.busy, onClick = { editor.save() })
                    PhotoRingAction(Modifier.align(Alignment.CenterStart), accountPresentationText("photo.discard"),
                        stateValues.drawablePathIconCancel, stateValues.drawableResIconCancel.value,
                        enabled = !state.busy, onClick = editor::discard)
                } else {
                    PhotoRingAction(Modifier.align(Alignment.CenterEnd), accountPresentationText("photo.retry"),
                        stateValues.drawablePathIconRefresh, stateValues.drawableResIconRefresh.value,
                        enabled = !state.busy, onClick = editor::load)
                    if (state.saved?.jpegBase64 != null) {
                        PhotoRingAction(Modifier.align(Alignment.BottomCenter), accountPresentationText("photo.remove"),
                            stateValues.drawablePathIconDelete, stateValues.drawableResIconDelete.value,
                            enabled = !state.busy, onClick = { confirmRemove = true })
                    }
                }
            }
            if (confirmRemove) ModalDialogWidget(
                title = stateValues.stringConfirm,
                subTitle = accountPresentationText("photo.remove") + "?",
                negativeButtonText = stateValues.stringCancel,
                positiveButtonText = stateValues.stringDelete,
                onDismiss = { confirmRemove = false },
                negativeAction = { confirmRemove = false },
                positiveAction = { confirmRemove = false; editor.save(remove = true) }
            )
            if(state.preview!=null) Text(storePeopleText("photo_unsaved"),color=stateValues.AccentColor,
                fontSize=stateValues.smallTextSize,textAlign=TextAlign.Center)
            Text(storePeopleText(if(mode==ProfilePhotoMode.STORE)"photo_store_visibility" else "photo_separate"),
                color=stateValues.PlaceholderTextColor,fontSize=stateValues.smallTextSize,textAlign=TextAlign.Center)
            state.error?.let {code->Text(accountPresentationText("photo.error.${code.takeIf {it in setOf("size","format","conflict","busy","unavailable")} ?: "network"}"),
                color=stateValues.ErrorColor,fontSize=stateValues.smallTextSize,textAlign=TextAlign.Center)}
            state.notice?.let {Text(accountPresentationText("photo.$it"),color=stateValues.AccentColor,fontSize=stateValues.smallTextSize)}

        }
    }
}

/** Fixed touch targets sit on the portrait ring, with no raised rectangular shadow. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppConfiguration.PhotoRingAction(modifier: Modifier, label: String, path: String,
    resource: DrawableResource, enabled: Boolean, onClick: () -> Unit) {
    // Alignment parent data belongs on a direct child of the portrait Box. TooltipBox
    // forwards its modifier to an inner anchor, which otherwise stacks every action centrally.
    Box(modifier) {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
            tooltip = { PlainTooltip { Text(label) } }, state = rememberTooltipState()) {
            IconButton(onClick = onClick, enabled = enabled,
                modifier = Modifier.size(48.dp).clip(CircleShape).background(stateValues.BackgroundColor)
                    .border(stateValues.unfocusedBorderWidth,
                        if (enabled) stateValues.AccentColor else stateValues.PlaceholderTextColor, CircleShape)) {
                CpImage(Modifier.size(23.dp), url = path, fallbackRes = resource, contentDescription = label,
                    tintColor = if (enabled) stateValues.TextColor else stateValues.PlaceholderTextColor)
            }
        }
    }
}
