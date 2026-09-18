package kz.aita

import kz.aita.updates.*
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.security.KeyPairGenerator
import java.security.Signature
import kotlin.test.*

class ManagedClientInstallerTest {
    private fun installed(build:Long,channel:ReleaseChannel=ReleaseChannel.RELEASE)=ClientBuildIdentity("1.0.0",build,channel,"","","direct","","")
    private fun withRoot(test: (java.io.File,ManagedClientInstaller)->Unit) {
        val root=Files.createTempDirectory("aita-installer-test-").toRealPath().toFile()
        try {test(root,ManagedClientInstaller(root){a,b->Files.move(a.toPath(),b.toPath(),StandardCopyOption.REPLACE_EXISTING);Unit})}
        finally {root.deleteRecursively()}
    }
    private fun fixture(root:java.io.File):Pair<PreparedClientInstaller,ClientArtifact> {
        val bytes="a verified installer fixture—not executable".toByteArray()
        val sha=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it.toInt() and 255)}
        val name="release-4-$sha.pkg";java.io.File(root,name).writeBytes(bytes)
        val record=PreparedClientInstaller("release4",4,ReleaseChannel.RELEASE,name,sha,bytes.size.toLong(),1)
        java.io.File(root,"release-pending.json").writeText(clientReleaseJson.encodeToString(PreparedClientInstaller.serializer(),record))
        return record to ClientArtifact(ClientOs.MACOS,kind=InstallerKind.PKG,url="https://updates.example.org/$sha.pkg",bytes=bytes.size.toLong(),sha256=sha)
    }
    @Test fun verifiedFileRequiresTheExactSavedBytes()=withRoot{root,cache->
        val (record,artifact)=fixture(root);assertTrue(cache.verifiedFile(record,artifact).isFile)
        java.io.File(root,record.fileName).appendText("changed")
        assertFailsWith<ClientUpdateFailure>{cache.verifiedFile(record,artifact)}
    }
    @Test fun uninstalledTargetIsNotDeleted()=withRoot{root,cache->runBlocking{
        val(record,_)=fixture(root);cache.clean(installed(3));assertTrue(java.io.File(root,record.fileName).exists());assertTrue(java.io.File(root,"release-pending.json").exists())
    }}
    @Test fun newInstalledBuildCleansOnlyItsManagedArtifact()=withRoot{root,cache->runBlocking{
        val(record,_)=fixture(root);val foreign=java.io.File(root,"personal.pkg").apply{writeText("keep")}
        cache.clean(installed(4));assertFalse(java.io.File(root,record.fileName).exists());assertFalse(java.io.File(root,"release-pending.json").exists());assertTrue(foreign.exists())
    }}
    @Test fun anotherChannelDoesNotAcknowledgeInstallation()=withRoot{root,cache->runBlocking{
        val(record,_)=fixture(root);cache.clean(installed(5,ReleaseChannel.TEST));assertTrue(java.io.File(root,record.fileName).exists())
    }}
    @Test fun traversalAndSymlinksCannotEscapeOwnedDirectory()=withRoot{root,cache->
        assertFailsWith<ClientUpdateFailure>{cache.readPreference("../secret")}
        val outside=Files.createTempFile("aita-outside-",".txt").toRealPath()
        try {
            Files.writeString(outside,"outside data must survive")
            Files.createSymbolicLink(root.toPath().resolve("pref-linked"),outside)
            assertFailsWith<ClientUpdateFailure>{cache.readPreference("linked")}
            assertFailsWith<ClientUpdateFailure>{cache.writePreference("linked","replacement")}
            assertFailsWith<ClientUpdateFailure>{cache.writePreference("linked",null)}
            assertEquals("outside data must survive",Files.readString(outside))
        } finally {Files.deleteIfExists(root.toPath().resolve("pref-linked"));Files.deleteIfExists(outside)}
    }
    @Test fun danglingLinksCannotCreateFilesOutsideOwnedDirectory()=withRoot{root,cache->
        val outside=Files.createTempDirectory("aita-outside-directory-").toRealPath()
        val missing=outside.resolve("must-not-be-created.txt")
        try {
            Files.createSymbolicLink(root.toPath().resolve("pref-linked"),missing)
            assertFailsWith<ClientUpdateFailure>{cache.readPreference("linked")}
            assertFailsWith<ClientUpdateFailure>{cache.writePreference("linked","replacement")}
            assertFalse(Files.exists(missing))
        } finally {Files.deleteIfExists(root.toPath().resolve("pref-linked"));Files.deleteIfExists(missing);Files.deleteIfExists(outside)}
    }
    @Test fun linkedRootCannotRedirectTheInstallerDirectory()=withRoot{root,_->
        val outside=Files.createTempDirectory("aita-outside-root-").toRealPath()
        val linked=root.toPath().resolve("linked-root")
        try {
            Files.createSymbolicLink(linked,outside)
            assertFailsWith<ClientUpdateFailure>{ManagedClientInstaller(linked.toFile()){_,_->}}
        } finally {Files.deleteIfExists(linked);Files.deleteIfExists(outside)}
    }
    @Test fun restoreRequiresTheSameReleaseAndArtifact()=withRoot{root,cache->runBlocking{
        val(record,artifact)=fixture(root)
        val r=ClientRelease(channel=ReleaseChannel.RELEASE,sequence=4,id=record.releaseId,version="1.0.0",build=4,publishedAtMillis=1,expiresAtMillis=2)
        assertNotNull(cache.restore(r,artifact));assertNull(cache.restore(r.copy(build=5),artifact))
    }}
    @Test fun corruptJournalCannotDeleteAnArbitraryFile()=withRoot{root,cache->runBlocking{
        val personal=java.io.File(root,"personal.pkg").apply{writeText("keep")}
        java.io.File(root,"release-pending.json").writeText("malformed")
        cache.clean(installed(9));assertTrue(personal.exists())
    }}
    @Test fun javaSignatureVerifierRejectsChangedBytes() {
        val pair=KeyPairGenerator.getInstance("RSA").apply{initialize(2048)}.generateKeyPair();val bytes="release".toByteArray()
        val sig=Signature.getInstance("SHA256withRSA").run{initSign(pair.private);update(bytes);sign()}
        assertTrue(verifyRsaClientRelease(bytes,sig,pair.public.encoded));assertFalse(verifyRsaClientRelease("changed".toByteArray(),sig,pair.public.encoded))
    }
}
