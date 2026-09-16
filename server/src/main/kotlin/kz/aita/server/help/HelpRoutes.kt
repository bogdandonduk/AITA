package kz.aita.server.help

import io.ktor.http.*
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kz.aita.ResponseDataModel
import kz.aita.help.*
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.security.MessageDigest

/** Public instructions only. Editing is an operator-side file publication, never an unprotected write API. */
internal class HelpCatalogStore(private val configured: Path? = null, private val assets: Path? = null,
    private val bundled: () -> ByteArray = {
        requireNotNull(HelpCatalogStore::class.java.getResourceAsStream("/help/tutorials.json")).use { it.readBytes() }
    }) {
    private val mutex=Mutex()
    private var cached: HelpCatalogue?=null
    private var fileStamp: Pair<Long,Long>?=null
    suspend fun catalogue(): HelpCatalogue = withContext(Dispatchers.IO) { mutex.withLock {
        val file=configured?.toAbsolutePath()?.normalize()
        val stamp=file?.let { require(Files.isRegularFile(it,LinkOption.NOFOLLOW_LINKS) && it.toRealPath()==it)
            require(Files.size(it) in 1..HELP_CATALOGUE_MAX_BYTES.toLong())
            Files.getLastModifiedTime(it).toMillis() to Files.size(it) }
        if(cached!=null && stamp==fileStamp) return@withLock requireNotNull(cached)
        val bytes=if(file==null) bundled() else Files.newInputStream(file).use { it.readNBytes(HELP_CATALOGUE_MAX_BYTES+1) }
        require(bytes.size<=HELP_CATALOGUE_MAX_BYTES)
        val data=helpJson.decodeFromString<HelpCatalogue>(bytes.decodeToString(throwOnInvalidSequence=true))
        require(validHelpCatalogue(data))
        require(cached==null || data.revision>=requireNotNull(cached).revision)
        cached=data; fileStamp=stamp; data
    } }
    suspend fun screenshot(name: String): ByteArray? = withContext(Dispatchers.IO) {
        if(!validHelpAssetName(name)) return@withContext null
        // Serving is restricted to assets explicitly referenced in the currently published help book.
        val known=catalogue().tutorials.any { t -> t.steps.any { s -> s.screenshots.any { it.asset==name } } }
        if(!known) return@withContext null
        val root=assets?.toAbsolutePath()?.normalize() ?: return@withContext null
        if(!Files.isDirectory(root) || root.toRealPath()!=root || Files.isSymbolicLink(root)) return@withContext null
        val file=root.resolve(name)
        if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS) || file.toRealPath().parent!=root || Files.size(file) !in 1..8_388_608) return@withContext null
        val bytes=Files.newInputStream(file).use { it.readNBytes(8_388_609) }
        if(bytes.size>8_388_608) return@withContext null
        val hash=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
        if(hash!=name.substringBefore('.')) return@withContext null
        bytes
    }
}
internal fun Route.installHelpRoutes(store: HelpCatalogStore) {
    route("/help") {
        get("/tutorials/{mode}") {
            val mode=HelpMode.fromSlug(call.parameters["mode"])
            if(mode==null) { call.respond(HttpStatusCode.NotFound); return@get }
            val data=try { store.catalogue().forMode(mode) } catch(cancel:kotlinx.coroutines.CancellationException) { throw cancel }
                catch(_:Exception) { call.respond(HttpStatusCode.ServiceUnavailable); return@get }
            call.response.header(HttpHeaders.CacheControl,"no-cache")
            call.response.header("X-Content-Type-Options","nosniff")
            call.respondText(helpJson.encodeToString(ResponseDataModel.serializer(HelpCatalogue.serializer()),ResponseDataModel(message=null,payload=data,negative=false)),ContentType.Application.Json)
        }
        get("/screenshots/{name}") {
            val name=call.parameters["name"].orEmpty()
            val image=try { store.screenshot(name) } catch(cancel:kotlinx.coroutines.CancellationException) { throw cancel }
                catch(_:Exception) { null }
            if(image==null) { call.respond(HttpStatusCode.NotFound); return@get }
            call.response.header("X-Content-Type-Options","nosniff")
            call.response.header(HttpHeaders.CacheControl,"public, max-age=31536000, immutable")
            call.respondBytes(image,ContentType.parse(when(name.substringAfterLast('.')) { "png"->"image/png"; "jpg"->"image/jpeg"; else->"image/webp" }))
        }
    }
}
fun Route.installHelpRoutes(config: ApplicationConfig) = installHelpRoutes(HelpCatalogStore(
    config.propertyOrNull("help.catalogFile")?.getString()?.takeIf(String::isNotBlank)?.let(Path::of),
    config.propertyOrNull("help.screenshotDirectory")?.getString()?.takeIf(String::isNotBlank)?.let(Path::of)))
