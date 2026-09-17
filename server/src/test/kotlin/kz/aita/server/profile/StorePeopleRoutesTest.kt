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
import kz.aita.*
import java.util.UUID
import kotlin.test.*

class StorePeopleRoutesTest {
    private val viewer=UUID.randomUUID();private val store=UUID.randomUUID();private val person=UUID.randomUUID()
    private val algorithm=Algorithm.HMAC256("isolated-route-test-only-not-a-production-secret")
    private val token=JWT.create().withSubject(viewer.toString()).sign(algorithm)
    private class Repository:StorePeopleRepository {
        val reads=mutableListOf<List<Any>>();var deny=false
        override suspend fun read(viewer:UUID,store:UUID,person:UUID,days:Int):StorePersonProfile? {
            reads+=listOf(viewer,store,person,days)
            return if(deny)null else StorePersonProfile(viewer.toString(),store.toString(),person.toString(),"Person",checkedAtMillis=1)
        }
    }
    private fun scenario(block:suspend ApplicationTestBuilder.(Repository)->Unit)=testApplication {
        val repo=Repository()
        application {
            install(Authentication){jwt("auth-jwt"){verifier(JWT.require(algorithm).build());validate {JWTPrincipal(it.payload)}}}
            routing {authenticate("auth-jwt"){storePeopleActions(repo)}}
        };block(repo)
    }
    @Test fun anonymousRequestsCannotReadProfiles()=scenario {repo->
        assertEquals(HttpStatusCode.Unauthorized,client.get("/stores/$store/people/$person").status);assertTrue(repo.reads.isEmpty())
    }
    @Test fun malformedIdentifiersAndUnsupportedPeriodsAreRejectedBeforeRepository()=scenario {repo->
        for(path in listOf("/stores/not-a-uuid/people/$person","/stores/$store/people/not-a-uuid")+
            listOf("abc","0","8","91","2147483648","-1").map {"/stores/$store/people/$person?days=$it"}) {
            assertEquals(HttpStatusCode.BadRequest,client.get(path){bearerAuth(token)}.status,path)
        };assertTrue(repo.reads.isEmpty())
    }
    @Test fun viewerIsAuthenticatedIdentityNotAQueryParameter()=scenario {repo->
        val result=client.get("/stores/$store/people/$person?viewer=$person&days=7"){bearerAuth(token)}
        assertEquals(HttpStatusCode.OK,result.status);assertEquals("private, no-store",result.headers[HttpHeaders.CacheControl])
        assertEquals(listOf(viewer,store,person,7),repo.reads.single())
    }
    @Test fun inaccessibleProfileReturnsNoPrivatePayload()=scenario {repo->
        repo.deny=true;val result=client.get("/stores/$store/people/$person"){bearerAuth(token)}
        assertEquals(HttpStatusCode.NotFound,result.status);assertFalse(result.bodyAsText().contains("displayName"))
    }
    @Test fun defaultPeriodAndBoundedReadBudget()=scenario {repo->
        repeat(12) {assertEquals(HttpStatusCode.OK,client.get("/stores/$store/people/$person"){bearerAuth(token)}.status)}
        assertEquals(30,repo.reads.first().last());assertEquals(HttpStatusCode.TooManyRequests,client.get("/stores/$store/people/$person"){bearerAuth(token)}.status)
    }
}
