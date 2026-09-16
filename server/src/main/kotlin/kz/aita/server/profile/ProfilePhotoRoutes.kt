package kz.aita.server.profile

import io.ktor.http.*
import io.ktor.server.auth.authenticate
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kz.aita.*
import kz.aita.server.RealtimeServerBus
import kz.aita.server.checkPrincipal
import kz.aita.server.genericResponse
import kz.aita.server.genericResponseNoPayload
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.UUID

/** Decode concurrency is bounded even across different accounts. Budget entries expire and are bounded. */
internal class ProfilePhotoBudget {
    private val slots=Semaphore(2)
    private val visits=LinkedHashMap<UUID,Pair<Long,Int>>()
    private fun allowed(owner:UUID):Boolean=synchronized(visits) {
        val now=System.currentTimeMillis()
        visits.entries.removeIf {now-it.value.first>=60_000}
        val entry=visits[owner]
        if(entry==null && visits.size>=4096)return@synchronized false
        if(entry!=null && entry.second>=12)return@synchronized false
        visits[owner]=(entry?.first ?: now) to ((entry?.second ?: 0)+1);true
    }
    suspend fun <T> bounded(owner:UUID,block:suspend ()->T):T {
        if(!allowed(owner) || !slots.tryAcquire())throw PhotoProblem("busy")
        try{return block()}finally{slots.release()}
    }
}
private suspend fun RoutingCall.photoBytes(max:Int):ByteArray=withTimeout(45_000L) {
    val declared=request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
    if(declared!=null && declared !in 1..max.toLong())throw PhotoProblem("size")
    val stream=receiveChannel();val buffer=ByteArray(16*1024);val output=ByteArrayOutputStream()
    while(true) {
        val n=stream.readAvailable(buffer,0,minOf(buffer.size,max-output.size()+1))
        if(n<0)break
        if(n==0){yield();continue}
        if(output.size()+n>max)throw PhotoProblem("size")
        output.write(buffer,0,n)
    }
    output.toByteArray().also {if(it.isEmpty())throw PhotoProblem("format")}
}
private suspend fun RoutingCall.photoError(problem:PhotoProblem) {
    val status=when(problem.code) {"size"->HttpStatusCode.PayloadTooLarge;"busy"->HttpStatusCode.TooManyRequests
        "unavailable"->HttpStatusCode.ServiceUnavailable;else->HttpStatusCode.BadRequest}
    if(problem.code=="busy")response.header(HttpHeaders.RetryAfter,"60")
    genericResponseNoPayload(status,eventMessage("account.ui.photo.error.${problem.code}"))
}
internal fun Route.installProfilePhotoActions(repository:ProfilePhotoRepository,budget:ProfilePhotoBudget=ProfilePhotoBudget(),
    onChanged:suspend (UUID)->Unit={}) {
    route("/users/profile-photo") {
        get {
            val owner=call.checkPrincipal() ?: return@get
            call.response.header(HttpHeaders.CacheControl,"private, no-store")
            call.response.header("X-Content-Type-Options","nosniff")
            call.genericResponse(HttpStatusCode.OK,repository.read(owner))
        }
        post("/preview") {
            val owner=call.checkPrincipal() ?: return@post
            call.response.header(HttpHeaders.CacheControl,"private, no-store")
            try {
                val result=budget.bounded(owner) {
                    val input=call.photoBytes(PROFILE_PHOTO_MAX_INPUT_BYTES)
                    withContext(Dispatchers.IO) {normalizeProfilePhoto(input)}
                }
                call.genericResponse(HttpStatusCode.OK,ProfilePhotoPreview(Base64.getEncoder().encodeToString(result)))
            } catch(problem:PhotoProblem){call.photoError(problem)}
            catch(timeout:TimeoutCancellationException){call.genericResponseNoPayload(HttpStatusCode.RequestTimeout,eventMessage("account.ui.photo.error.network"))}
        }
        put {
            val owner=call.checkPrincipal() ?: return@put
            call.response.header(HttpHeaders.CacheControl,"private, no-store")
            try {
                val result=budget.bounded(owner) {
                    val body=call.photoBytes(PROFILE_PHOTO_MAX_OUTPUT_BYTES*2)
                    val change=try {jsonBase.decodeFromString<ProfilePhotoChange>(body.decodeToString(throwOnInvalidSequence=true))}
                        catch(_:Exception){throw PhotoProblem("format")}
                    if(change.expectedRevision<0)throw PhotoProblem("format")
                    val image=change.jpegBase64?.let {encoded->
                        if(encoded.length>(PROFILE_PHOTO_MAX_OUTPUT_BYTES+2)/3*4)throw PhotoProblem("size")
                        val decoded=try {Base64.getDecoder().decode(encoded)}catch(_:Exception){throw PhotoProblem("format")}
                        withContext(Dispatchers.IO){normalizeProfilePhoto(decoded)}
                    }
                    repository.change(owner,change.expectedRevision,image)
                }
                if(!result.conflict)onChanged(owner) // After the repository transaction, never for preview or a rejected write.
                call.genericResponse(if(result.conflict)HttpStatusCode.Conflict else HttpStatusCode.OK,result.photo,
                    if(result.conflict)eventMessage("account.ui.photo.error.conflict") else null)
            } catch(problem:PhotoProblem){call.photoError(problem)}
            catch(timeout:TimeoutCancellationException){call.genericResponseNoPayload(HttpStatusCode.RequestTimeout,eventMessage("account.ui.photo.error.network"))}
        }
    }
}
fun Route.installProfilePhotoRoutes() {
    val repository=DatabaseProfilePhotos()
    authenticate("auth-jwt") {installProfilePhotoActions(repository,onChanged={owner ->
        RealtimeServerBus.publish(entity="users/profile-photo",userId=owner.toString())
    })}
}
