package kz.aita.server.updates

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kz.aita.updates.*
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/** File-backed public release catalog. There is deliberately no unauthenticated publishing endpoint. */
internal class ClientReleaseCatalog(private val directory: Path?, private val publicKey: ByteArray,
    private val clock: () -> Long = System::currentTimeMillis) {
    data class Snapshot(val text: String, val etag: String, val release: ClientRelease)
    private data class Cached(val modified: Long, val size: Long, val snapshot: Snapshot)
    private val cache = ConcurrentHashMap<ReleaseChannel, Cached>()
    private fun root(): Path? = directory?.toAbsolutePath()?.normalize()?.takeIf {
        Files.isDirectory(it) && !Files.isSymbolicLink(it) && it.toRealPath() == it
    }
    suspend fun snapshot(channel: ReleaseChannel): Snapshot? = withContext(Dispatchers.IO) {
        val root = root() ?: return@withContext null
        val file = root.resolve("${channel.name.lowercase()}.json")
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return@withContext null
        require(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && file.toRealPath().parent == root)
        val size = Files.size(file); require(size in 1..CLIENT_RELEASE_MAX_BYTES.toLong())
        val modified = Files.getLastModifiedTime(file).toMillis()
        val old = cache[channel]
        if (old != null && old.size == size && old.modified == modified &&
            clientReleaseProblem(old.snapshot.release, clock(), channel) == null) return@withContext old.snapshot
        val bytes = Files.newInputStream(file).use { it.readNBytes(CLIENT_RELEASE_MAX_BYTES + 1) }
        require(bytes.size <= CLIENT_RELEASE_MAX_BYTES)
        val text = bytes.toString(Charsets.UTF_8)
        val verified = verifyClientReleaseEnvelope(text, publicKey) { payload, signature, key ->
            runCatching {
                val rsa = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(key)) as RSAPublicKey
                require(rsa.modulus.bitLength() in 2048..8192)
                Signature.getInstance("SHA256withRSA").run { initVerify(rsa); update(payload); verify(signature) }
            }.getOrDefault(false)
        } ?: error("Invalid release signature")
        require(clientReleaseProblem(verified.release, clock(), channel) == null)
        val tag = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
        Snapshot(text, "\"$tag\"", verified.release).also { cache[channel] = Cached(modified,size,it) }
    }
    suspend fun artifact(name: String): java.io.File? = withContext(Dispatchers.IO) {
        if (!Regex("[0-9a-f]{64}\\.(apk|aab|msi|exe|pkg|dmg|deb|rpm)").matches(name)) return@withContext null
        val root = root() ?: return@withContext null
        val folder = root.resolve("artifacts")
        val file = folder.resolve(name)
        if (!Files.isDirectory(folder) || Files.isSymbolicLink(folder) || folder.toRealPath() != folder ||
            !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || file.toRealPath().parent != folder ||
            Files.size(file) !in 1..CLIENT_INSTALLER_MAX_BYTES) return@withContext null
        file.toFile()
    }
}

internal fun Route.installClientUpdateRoutes(catalog: ClientReleaseCatalog) {
    route("/client-updates") {
        get("/{channel}.json") {
            val channel = when (call.parameters["channel"]) { "release" -> ReleaseChannel.RELEASE; "test" -> ReleaseChannel.TEST; else -> null }
            if (channel == null) { call.respond(HttpStatusCode.NotFound); return@get }
            call.response.header(HttpHeaders.CacheControl, "no-cache, max-age=0, must-revalidate")
            call.response.header("X-Content-Type-Options", "nosniff")
            val result = try { catalog.snapshot(channel) } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
                catch (_: Exception) { call.respondText("Release metadata unavailable", status = HttpStatusCode.ServiceUnavailable); return@get }
            if (result == null) { call.respond(HttpStatusCode.NoContent); return@get }
            call.response.header(HttpHeaders.ETag,result.etag)
            if (call.request.header(HttpHeaders.IfNoneMatch) == result.etag) { call.respond(HttpStatusCode.NotModified); return@get }
            call.respondText(result.text,ContentType.Application.Json)
        }
        get("/artifacts/{name}") {
            val name = call.parameters["name"].orEmpty()
            val file = catalog.artifact(name)
            if (file == null) { call.respond(HttpStatusCode.NotFound); return@get }
            call.response.header(HttpHeaders.CacheControl,"public, max-age=31536000, immutable")
            call.response.header("X-Content-Type-Options","nosniff")
            call.response.header(HttpHeaders.ContentDisposition,ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName,name).toString())
            call.respondFile(file)
        }
    }
}

fun Route.installClientUpdateRoutes(config: ApplicationConfig) {
    val directory = config.propertyOrNull("clientUpdates.directory")?.getString()?.takeIf(String::isNotBlank)
        ?: System.getenv("AITA_CLIENT_RELEASES_DIR")?.takeIf(String::isNotBlank)
    val encoded = config.propertyOrNull("clientUpdates.publicKey")?.getString()?.takeIf(String::isNotBlank)
        ?: System.getenv("AITA_UPDATE_PUBLIC_KEY").orEmpty()
    val publicKey = runCatching { Base64.getDecoder().decode(encoded) }.getOrDefault(byteArrayOf())
    installClientUpdateRoutes(ClientReleaseCatalog(directory?.let(Path::of),publicKey))
}
