package kz.aita.server

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlin.test.*

class SupplierCorsTest {
    private fun scenario(block:suspend ApplicationTestBuilder.()->Unit)=testApplication {
        application {
            install(CORS) {
                allowHost("aita.kz", schemes=listOf("https"))
                allowMethod(HttpMethod.Get);allowMethod(HttpMethod.Put);allowMethod(HttpMethod.Options)
                allowHeader(HttpHeaders.Authorization);allowHeader(HttpHeaders.ContentType)
                allowSupplierWorkspaceHeaders()
            }
            routing { get("/supplierOrders/get"){call.respondText("isolated")};put("/supplierOrders/update"){call.respondText("isolated")} }
        }
        block()
    }
    @Test fun focusedSupplierReadAndExplicitSupplierWritePreflightsAreAllowed()=scenario {
        for ((method,path) in listOf("GET" to "/supplierOrders/get","PUT" to "/supplierOrders/update")) {
            val result=client.options(path){
                header(HttpHeaders.Origin,"https://aita.kz")
                header(HttpHeaders.AccessControlRequestMethod,method)
                header(HttpHeaders.AccessControlRequestHeaders,"authorization,content-type,supplier_id,supplier_side,mark_seen")
            }
            assertEquals(HttpStatusCode.OK,result.status)
            assertEquals("https://aita.kz",result.headers[HttpHeaders.AccessControlAllowOrigin])
            val allowed=result.headers[HttpHeaders.AccessControlAllowHeaders].orEmpty().lowercase()
            for (name in listOf("supplier_id","supplier_side","mark_seen")) assertTrue(allowed.contains(name),name)
        }
    }
    @Test fun foreignOriginAndUnlistedHeadersRemainDenied()=scenario {
        for((origin,requestedHeader) in listOf("https://untrusted.example" to "supplier_id","https://aita.kz" to "x-arbitrary-secret")) {
            val result=client.options("/supplierOrders/get"){
                header(HttpHeaders.Origin,origin);header(HttpHeaders.AccessControlRequestMethod,"GET")
                header(HttpHeaders.AccessControlRequestHeaders,requestedHeader)
            }
            assertEquals(HttpStatusCode.Forbidden,result.status)
            assertNull(result.headers[HttpHeaders.AccessControlAllowOrigin])
        }
    }
}
