package kz.aita.server.auth

import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kz.aita.*
import kz.aita.auth.*
import kotlinx.serialization.json.*
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kz.aita.server.*
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Assume.assumeTrue
import java.sql.DriverManager
import java.util.UUID
import kotlin.test.*

/** Complete migrated disposable schema: catches missing tables, constraints and cascading cleanup. */
class AccountDeletionDatabaseTest {
    @Test fun deletionRequiresCredentialsPreservesBusinessesAndRevokesAllSessions() {
        val baseUrl=System.getenv("AITA_AUTH_TEST_DB_URL").orEmpty()
        assumeTrue(baseUrl.isNotBlank())
        require(Regex("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/aita_test_[A-Za-z0-9_]+").matches(baseUrl))
        val user=System.getProperty("user.name");val name="aita_test_delete_"+UUID.randomUUID().toString().replace("-", "")
        val url=baseUrl.substringBeforeLast("/")+"/"+name
        DriverManager.getConnection(baseUrl,user,"").use { c -> c.createStatement().use { it.execute("CREATE DATABASE $name") } }
        val old=TransactionManager.defaultDatabase
        var db: Database?=null
        try {
            // V86 is a historical operator seed tied to a real store. Model its already-applied
            // marker in this empty fixture; never fabricate or import a customer's store.
            Flyway.configure().dataSource(url,user,"").schemas("public").defaultSchema("public").locations("classpath:db/migration").target("85").load().migrate()
            DriverManager.getConnection(url,user,"").use { c -> c.createStatement().use { st ->
                st.execute("CREATE TABLE IF NOT EXISTS aita_launch_stock_seed_state(seed_key TEXT PRIMARY KEY,store_id UUID NOT NULL,seeded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)")
                st.execute("INSERT INTO aita_launch_stock_seed_state(seed_key,store_id) VALUES ('parent_store_toys_stock_2026_06_eddec888','00000000-0000-4000-8000-000000000000')")
            } }
            Flyway.configure().dataSource(url,user,"").schemas("public").defaultSchema("public").locations("classpath:db/migration").load().migrate()
            db=Database.connect(url,driver="org.postgresql.Driver",user=user,password="")
            TransactionManager.defaultDatabase=db
            val password="Aita-test-password-42!";val id=UUID.randomUUID();val other=UUID.randomUUID()
            val factorOwner=UUID.randomUUID();val emailOwner=UUID.randomUUID()
            val config=AdvancedAuthConfig.load(readValue={ key -> mapOf("AITA_RESEND_API_KEY" to "local-test-key", "AITA_AUTH_EMAIL_FROM" to "AITA Test <security@example.test>")[key] },environmentName="test")
            transaction(db) {
                listOf(id,other,factorOwner,emailOwner).forEachIndexed { i,who -> Users.insert {
                    it[Users.id]=who;it[publicId]="DELETE-TEST-$i";it[phoneNumber]="delete-$i";it[email]="delete-$i@example.test"
                    it[firstName]="Test";it[lastName]="Person";it[countryLocale]="kz";it[passwordHash]=Pw.hash(password.toCharArray())
                } }
            }
            testApplication {
                lateinit var service:AitaAdvancedAuthService
                val tokens=TokenService(JwtConfig("test","test","test","test-only-key-".repeat(8),900000L,86400000L))
                application { service=AitaAdvancedAuthService(tokens,config,this) }
                startApplication()
                suspend fun deleteWithEmail(who: UUID): String? {
                    val flow = assertNotNull(service.requestSecurityEmail(who, AitaSecurityEmailRequest(AitaSecurityEmailAction.ACCOUNT_DELETE,
                        "", password, service.settings(who).securityRevision), "192.0.2.15"))
                    val code = transaction(db) {
                        val challenge = AuthOneTimeChallenges.selectAll().where { AuthOneTimeChallenges.publicId eq UUID.fromString(flow.flowId) }.single()
                        val mail = AuthEmailOutbox.selectAll().where { AuthEmailOutbox.challengeId eq challenge[AuthOneTimeChallenges.id] }.single()
                        val message = Json.parseToJsonElement(decrypt(config.encryptionKey, "auth-email:${mail[AuthEmailOutbox.id]}", requireNotNull(mail[AuthEmailOutbox.payloadCiphertext]))).jsonObject
                        Regex("(?<![0-9])[0-9]{6}(?![0-9])").find(message.getValue("text").jsonPrimitive.content)!!.value
                    }
                    return service.deleteAccount(who, AccountDeletionRequest(password, emailProof = AitaSecurityEmailProof(flow.flowId, code), confirmed = true))
                }
                assertEquals("confirmation",service.deleteAccount(id,AccountDeletionRequest("wrong",confirmed=true)))
                assertEquals("confirmation",service.deleteAccount(id,AccountDeletionRequest(password)))
                val store=UUID.randomUUID()
                transaction(db) { exec("INSERT INTO stores(id,public_id,owner_user_ids,country_locales,name) VALUES ('$store','DELETESTORE','[\"$id\"]','[\"kz\"]','[]')") }
                assertEquals("confirmation",service.deleteAccount(id,AccountDeletionRequest(password,confirmed=true)))
                assertEquals("owned_business",deleteWithEmail(id))
                transaction(db) { exec("UPDATE stores SET owner_user_ids='[\"$other\"]' WHERE id='$store'") }
                val login=service.passwordLogin(kz.aita.auth.AitaPasswordLoginRequestDataModel("delete-0@example.test",password),mapOf("installationId" to "first"))
                assertNotNull(login?.tokenPair)
                assertEquals("deleted",deleteWithEmail(id))
                transaction(db) {
                    val row=Users.selectAll().where { Users.id eq id }.single()
                    assertFalse(row[Users.isActive]);assertEquals("",row[Users.firstName]);assertEquals("",row[Users.passwordHash])
                    assertTrue(Users.selectAll().where { Users.id eq other }.single()[Users.isActive])
                    assertEquals(1L,Stores.selectAll().where { Stores.id eq store }.count())
                    assertEquals(0L,RefreshSessions.selectAll().where { RefreshSessions.userId eq id }.count())
                    assertEquals(0L,AuthLoginEmails.selectAll().where { AuthLoginEmails.userId eq id }.count())
                }
                assertNull(service.passwordLogin(kz.aita.auth.AitaPasswordLoginRequestDataModel("delete-0@example.test",password),emptyMap()))
                service.settings(factorOwner);service.settings(emailOwner)
                transaction(db) {
                    AuthSecurityProfiles.update({ AuthSecurityProfiles.userId eq factorOwner }) {
                        it[totpEnabledAtMillis]=System.currentTimeMillis()
                        it[totpSecretCiphertext]=encrypt(config.encryptionKey,"totp-active:$factorOwner","GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ")
                    }
                    AuthSecurityProfiles.update({ AuthSecurityProfiles.userId eq emailOwner }) { it[emailRequiredForLogin]=true }
                }
                assertEquals("confirmation",service.deleteAccount(factorOwner,AccountDeletionRequest(password,confirmed=true)))
                assertEquals("deleted",service.deleteAccount(factorOwner,AccountDeletionRequest(password,totp(),confirmed=true)))
                assertEquals("confirmation",service.deleteAccount(emailOwner,AccountDeletionRequest(password,confirmed=true)))
                val flow=assertNotNull(service.requestSecurityEmail(emailOwner,AitaSecurityEmailRequest(AitaSecurityEmailAction.ACCOUNT_DELETE,"",password,service.settings(emailOwner).securityRevision),"192.0.2.1"))
                val code=transaction(db) {
                    val challenge=AuthOneTimeChallenges.selectAll().where { AuthOneTimeChallenges.publicId eq UUID.fromString(flow.flowId) }.single()
                    val mail=AuthEmailOutbox.selectAll().where { AuthEmailOutbox.challengeId eq challenge[AuthOneTimeChallenges.id] }.single()
                    val message=Json.parseToJsonElement(decrypt(config.encryptionKey,"auth-email:${mail[AuthEmailOutbox.id]}",requireNotNull(mail[AuthEmailOutbox.payloadCiphertext]))).jsonObject
                    Regex("(?<![0-9])[0-9]{6}(?![0-9])").find(message.getValue("text").jsonPrimitive.content)!!.value
                }
                assertEquals("deleted",service.deleteAccount(emailOwner,AccountDeletionRequest(password,emailProof=AitaSecurityEmailProof(flow.flowId,code),confirmed=true)))

            }
        } finally {
            TransactionManager.defaultDatabase=old
            db?.let(TransactionManager::closeAndUnregister)
            DriverManager.getConnection(baseUrl,user,"").use { c -> c.createStatement().use { it.execute("DROP DATABASE IF EXISTS $name WITH (FORCE)") } }
        }
    }
    private fun encrypt(key:ByteArray,context:String,text:String):String {
        val iv=ByteArray(12).also(java.security.SecureRandom()::nextBytes)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,SecretKeySpec(key,"AES"),GCMParameterSpec(128,iv));cipher.updateAAD(context.toByteArray())
        val b64=Base64.getUrlEncoder().withoutPadding()
        return "v1.${b64.encodeToString(iv)}.${b64.encodeToString(cipher.doFinal(text.toByteArray()))}"
    }
    private fun decrypt(key:ByteArray,context:String,text:String):String {
        val parts=text.split('.');val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE,SecretKeySpec(key,"AES"),GCMParameterSpec(128,Base64.getUrlDecoder().decode(parts[1])));cipher.updateAAD(context.toByteArray())
        return cipher.doFinal(Base64.getUrlDecoder().decode(parts[2])).toString(Charsets.UTF_8)
    }
    private fun totp():String {
        val mac=Mac.getInstance("HmacSHA1");mac.init(SecretKeySpec("12345678901234567890".toByteArray(),"HmacSHA1"))
        val step=System.currentTimeMillis()/30000;val hash=mac.doFinal(ByteArray(8){(step ushr ((7-it)*8)).toByte()});val offset=hash.last().toInt() and 15
        val number=((hash[offset].toInt() and 127) shl 24) or ((hash[offset+1].toInt() and 255) shl 16) or ((hash[offset+2].toInt() and 255) shl 8) or (hash[offset+3].toInt() and 255)
        return (number%1000000).toString().padStart(6,'0')
    }
}
