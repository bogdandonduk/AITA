package kz.aita.server

import kotlin.test.*

class SignInDevicePolicyTest {
    @Test fun installationIdentityDistinguishesTwoIdenticallyNamedDevices() {
        val one = mapOf("installationId" to "a", "deviceName" to "Chrome", "platformName" to "Web")
        assertFalse(isDifferentSignInDevice(one, one))
        assertTrue(isDifferentSignInDevice(one, one + ("installationId" to "b")))
        assertFalse(isDifferentSignInDevice(null, one))
        assertTrue(isDifferentSignInDevice(mapOf("platformName" to "Android"), mapOf("platformName" to "Web")))
    }
}
