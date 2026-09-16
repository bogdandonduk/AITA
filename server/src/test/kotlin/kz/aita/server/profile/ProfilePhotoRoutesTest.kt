package kz.aita.server.profile

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import kz.aita.*
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.Base64
import javax.imageio.ImageIO
import kotlin.test.*

/** Real Ktor/JWT/bounded image routes with an isolated in-memory repository, never the user's database. */
class ProfilePhotoRoutesTest {
    private val owner=UUID.fromString("11111111-1111-4111-8111-111111111111")
    private val other=UUID.fromString("22222222-2222-4222-8222-222222222222")
    private val algorithm=Algorithm.HMAC256("disposable-unit-test-verifier-only")
    private fun token(id:UUID=owner)=JWT.create().withSubject(id.toString()).sign(algorithm)
    private fun png()=ByteArrayOutputStream().use {ImageIO.write(BufferedImage(30,40,BufferedImage.TYPE_INT_RGB),"PNG",it);it.toByteArray()}
    private class Repository:ProfilePhotoRepository {
        val pictures=mutableMapOf<UUID,ProfilePhotoSnapshot>();var writes=0;var reads=0;var conflict=false
        val announced=mutableListOf<UUID>()
        override suspend fun read(owner:UUID):ProfilePhotoSnapshot {reads++;return pictures[owner] ?: ProfilePhotoSnapshot(owner.toString())}
        override suspend fun change(owner:UUID,expected:Long,jpeg:ByteArray?):PhotoWriteResult {
            writes++;val existing=pictures[owner] ?: ProfilePhotoSnapshot(owner.toString())
            if(conflict)return PhotoWriteResult(existing,true)
            val saved=ProfilePhotoSnapshot(owner.toString(),existing.revision+1,jpeg?.let{Base64.getEncoder().encodeToString(it)},1)
            pictures[owner]=saved;return PhotoWriteResult(saved)
        }
    }
    private fun scenario(block:suspend ApplicationTestBuilder.(Repository)->Unit)=testApplication {
        val repo=Repository()
        application {
            install(Authentication) {jwt("auth-jwt") {verifier(JWT.require(algorithm).build());validate {JWTPrincipal(it.payload)}}}
            routing {authenticate("auth-jwt") {installProfilePhotoActions(repo,onChanged={repo.announced.add(it)})}}
        }
        block(repo)
    }
    private suspend fun payload(response:HttpResponse):JsonObject {
        val envelope=jsonBase.parseToJsonElement(response.bodyAsText()).jsonObject
        return jsonBase.parseToJsonElement(envelope.getValue("payload").jsonPrimitive.content).jsonObject
    }
    @Test fun noAuthenticationMeansNoReadOrWrite()=scenario {repo->
        assertEquals(HttpStatusCode.Unauthorized,client.get("/users/profile-photo").status)
        assertEquals(HttpStatusCode.Unauthorized,client.post("/users/profile-photo/preview"){setBody(png())}.status)
        assertEquals(HttpStatusCode.Unauthorized,client.put("/users/profile-photo"){setBody("{}")} .status)
        assertEquals(0,repo.writes);assertEquals(0,repo.reads)
    }
    @Test fun currentAccountIsDerivedFromJwtNotRequestParameters()=scenario {repo->
        repo.pictures[other]=ProfilePhotoSnapshot(other.toString(),5,null,1)
        val response=client.get("/users/profile-photo?userId=$other"){bearerAuth(token())}
        assertEquals(HttpStatusCode.OK,response.status)
        assertEquals(owner.toString(),payload(response).getValue("accountId").jsonPrimitive.content)
        assertEquals("private, no-store",response.headers[HttpHeaders.CacheControl])
    }
    @Test fun previewDoesNotPersistOriginalOrNormalizedPicture()=scenario {repo->
        val response=client.post("/users/profile-photo/preview"){bearerAuth(token());contentType(ContentType.Application.OctetStream);setBody(png())}
        assertEquals(HttpStatusCode.OK,response.status)
        assertNotNull(profilePhotoJpegBytes(payload(response).getValue("jpegBase64").jsonPrimitive.content))
        assertEquals(0,repo.writes);assertTrue(repo.pictures.isEmpty());assertTrue(repo.announced.isEmpty())
    }
    @Test fun explicitSaveAndRemovalOnlyModifySignedInAccount()=scenario {repo->
        val image=Base64.getEncoder().encodeToString(normalizeProfilePhoto(png()))
        val response=client.put("/users/profile-photo"){bearerAuth(token());contentType(ContentType.Application.Json)
            setBody(jsonBase.encodeToString(ProfilePhotoChange.serializer(),ProfilePhotoChange(0,image)))}
        assertEquals(HttpStatusCode.OK,response.status);assertNotNull(repo.pictures[owner]?.jpegBase64);assertNull(repo.pictures[other])
        val removal=client.put("/users/profile-photo"){bearerAuth(token());contentType(ContentType.Application.Json)
            setBody(jsonBase.encodeToString(ProfilePhotoChange.serializer(),ProfilePhotoChange(1,null)))}
        assertEquals(HttpStatusCode.OK,removal.status);assertNull(repo.pictures[owner]!!.jpegBase64);assertEquals(2,repo.pictures[owner]!!.revision)
        assertEquals(listOf(owner,owner),repo.announced)
    }
    @Test fun conflictReturnsTheLatestOwnedSnapshotWithoutSuccess()=scenario {repo->
        repo.conflict=true;repo.pictures[owner]=ProfilePhotoSnapshot(owner.toString(),5,null,1)
        val response=client.put("/users/profile-photo"){bearerAuth(token());contentType(ContentType.Application.Json);setBody("""{"expectedRevision":2,"jpegBase64":null}""")}
        assertEquals(HttpStatusCode.Conflict,response.status)
        val envelope=jsonBase.parseToJsonElement(response.bodyAsText()).jsonObject;assertTrue(envelope.getValue("negative").jsonPrimitive.boolean)
        assertEquals(5,payload(response).getValue("revision").jsonPrimitive.long);assertTrue(repo.announced.isEmpty())
    }
    @Test fun badMediaAndMalformedOrNegativeChangesCannotReachRepository()=scenario {repo->
        val media=client.post("/users/profile-photo/preview"){bearerAuth(token());setBody("<svg/>".toByteArray())}
        assertEquals(HttpStatusCode.BadRequest,media.status)
        for(body in listOf("{}","""{"expectedRevision":-1}""","""{"expectedRevision":0,"jpegBase64":"../../secret"}""")) {
            assertEquals(HttpStatusCode.BadRequest,client.put("/users/profile-photo"){bearerAuth(token());contentType(ContentType.Application.Json);setBody(body)}.status)
        }
        assertEquals(0,repo.writes)
    }
    @Test fun accountRateLimitDoesNotAccumulateUnlimitedDecodeWork()=scenario {repo->
        repeat(12) {assertEquals(HttpStatusCode.BadRequest,client.post("/users/profile-photo/preview"){bearerAuth(token());setBody(byteArrayOf(1))}.status)}
        val response=client.post("/users/profile-photo/preview"){bearerAuth(token());setBody(byteArrayOf(1))}
        assertEquals(HttpStatusCode.TooManyRequests,response.status);assertEquals("60",response.headers[HttpHeaders.RetryAfter]);assertEquals(0,repo.writes)
    }
    @Test fun globalDecodeSlotsAreReleasedAfterFailure()=runBlocking {
        val budget=ProfilePhotoBudget();val gate=CompletableDeferred<Unit>();val entered=CompletableDeferred<Unit>();var count=0
        val a=launch(start=CoroutineStart.UNDISPATCHED){budget.bounded(owner){if(++count==2)entered.complete(Unit);gate.await()}}
        val b=launch(start=CoroutineStart.UNDISPATCHED){budget.bounded(other){if(++count==2)entered.complete(Unit);gate.await()}}
        entered.await();assertEquals("busy",assertFailsWith<PhotoProblem>{budget.bounded(UUID.randomUUID()){Unit}}.code)
        gate.complete(Unit);joinAll(a,b)
        assertFailsWith<IllegalStateException>{budget.bounded(owner){throw IllegalStateException("test")}}
        assertEquals("done",budget.bounded(owner){"done"})
    }
}
