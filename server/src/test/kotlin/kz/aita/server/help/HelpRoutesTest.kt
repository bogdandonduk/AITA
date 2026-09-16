package kz.aita.server.help

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import kz.aita.ResponseDataModel
import kz.aita.help.*
import kz.aita.DEFAULT_NEW_ACCOUNT_APP_MODE
import kz.aita.server.Users
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.test.*

class HelpRoutesTest {
    private fun temporary(test:(Path)->Unit) { val root=Files.createTempDirectory("aita-help-test-").toRealPath();try {test(root)}finally {root.toFile().deleteRecursively()} }
    @Test fun bundledBookIsValidAndHasSubstantialModeSpecificCoverage()=runBlocking {
        val all=HelpCatalogStore().catalogue();assertTrue(validHelpCatalogue(all));assertEquals(98,all.tutorials.size)
        assertTrue(all.forMode(HelpMode.STORE).tutorials.size>=60)
        assertTrue(all.forMode(HelpMode.BUYER).tutorials.size>=30)
        assertTrue(all.forMode(HelpMode.SUPPLIER).tutorials.size>=30)
    }
    @Test fun publicRoutesReturnOnlyTheRequestedModesBook()=testApplication {
        application {routing {installHelpRoutes(HelpCatalogStore())}}
        for(mode in HelpMode.entries) {
            val response=client.get("/help/tutorials/${mode.slug}");assertEquals(HttpStatusCode.OK,response.status)
            val book=helpJson.decodeFromString(ResponseDataModel.serializer(HelpCatalogue.serializer()),response.bodyAsText()).payload!!
            assertTrue(validHelpCatalogue(book,mode));assertTrue(book.faqs.all {mode in it.modes})
            if(mode!=HelpMode.STORE) assertTrue(book.tutorials.none {it.id.startsWith("sale.")})
        }
        assertEquals(HttpStatusCode.NotFound,client.get("/help/tutorials/admin").status)
    }
    @Test fun invalidConfiguredBookDoesNotReplaceTheBundledBookSilently()=temporary {root->
        val file=root.resolve("tutorials.json");Files.writeString(file,"{}")
        testApplication {application {routing {installHelpRoutes(HelpCatalogStore(file))}}
            assertEquals(HttpStatusCode.ServiceUnavailable,client.get("/help/tutorials/buyer").status)
        }
    }
    @Test fun screenshotMustBeReferencedAndHashVerified()=temporary {root->
        val raw=byteArrayOf(1,2,3);val hash=MessageDigest.getInstance("SHA-256").digest(raw).joinToString("") {"%02x".format(it.toInt() and 255)}
        val name="$hash.png";Files.write(root.resolve(name),raw)
        val base=runBlocking {HelpCatalogStore().catalogue()};val t=base.tutorials.first()
        val modified=t.copy(steps=t.steps.mapIndexed {i,s->if(i==0)s.copy(screenshots=listOf(HelpScreenshot(name,mapOf("en" to "Test header only"),width=1,height=1))) else s})
        val book=base.copy(tutorials=listOf(modified)+base.tutorials.drop(1))
        val file=root.resolve("tutorials.json");Files.writeString(file,helpJson.encodeToString(HelpCatalogue.serializer(),book))
        testApplication {application {routing {installHelpRoutes(HelpCatalogStore(file,root))}}
            assertEquals(HttpStatusCode.OK,client.get("/help/screenshots/$name").status)
            assertEquals(HttpStatusCode.NotFound,client.get("/help/screenshots/secret.txt").status)
            assertEquals(HttpStatusCode.NotFound,client.get("/help/screenshots/${"b".repeat(64)}.png").status)
            Files.write(root.resolve(name),byteArrayOf(9));assertEquals(HttpStatusCode.NotFound,client.get("/help/screenshots/$name").status)
        }
    }
    @Test fun newDatabaseRowsUseBuyerDefault() {
        assertEquals(DEFAULT_NEW_ACCOUNT_APP_MODE,Users.appModeId.defaultValueFun?.invoke())
    }
}
