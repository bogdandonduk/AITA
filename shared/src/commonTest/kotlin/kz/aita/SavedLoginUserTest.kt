package kz.aita

import kotlin.test.*
import kotlinx.serialization.encodeToString

class SavedLoginUserTest {
    private val account = UserAccountDataModel("account", phoneNumber = "+77001234567", email = "user@example.invalid",
        firstName = "First", lastName = "Last", countryLocale = "kz", workerAccountIds = null,
        supplierAccountIds = null, createdAt = 1, isActive = true)
    @Test fun rememberedIdentityContainsOnlyNameAndLogin() {
        val saved = assertNotNull(savedLoginUser(account))
        assertEquals("First Last", saved.name)
        assertEquals("user@example.invalid", saved.login)
        val json = jsonBase.encodeToString(saved)
        assertFalse(json.contains("password")); assertFalse(json.contains("token"))
        assertEquals(3, jsonBase.parseToJsonElement(json).let { (it as kotlinx.serialization.json.JsonObject).size })
    }
    @Test fun phoneOnlyAccountKeepsNormalPhoneAuthentication() {
        val saved = assertNotNull(savedLoginUser(account.copy(email = "")))
        assertEquals(kz.aita.auth.normalizeAitaPhoneAlias(account.phoneNumber), saved.login)
        assertNull(savedLoginUser(account.copy(email = "", phoneNumber = "")))
    }
}
