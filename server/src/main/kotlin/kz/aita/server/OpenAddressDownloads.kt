package kz.aita.server

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.plugins.partialcontent.PartialContent
import java.nio.file.Files
import java.nio.file.LinkOption

/** Public ODbL source offer, limited to the two prepared catalogue files. */
internal fun Route.installOpenAddressDownloads() {
    route("/open-addresses") {
        install(PartialContent)
        get("/{name}") {
            val name=call.parameters["name"]
            if (name !in setOf("addresses.sqlite.gz","manifest.json")) { call.respond(HttpStatusCode.NotFound);return@get }
            val root=OpenAddressService.databasePath().toAbsolutePath().normalize().parent
            val path=root.resolve(name!!)
            if (!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS) || path.toRealPath().parent != root.toRealPath()) {
                call.respond(HttpStatusCode.NotFound);return@get
            }
            call.response.header(HttpHeaders.CacheControl,"public, max-age=3600")
            call.response.header(HttpHeaders.ContentDisposition,ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName,name).toString())
            call.respondFile(path.toFile())
        }
    }
}
