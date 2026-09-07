package kz.aita.payments.management

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PaymentManagementClientTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun secretRequestIsMarkedWriteOnlyForTransport() = runTest {
        var captured: PaymentManagementHttpRequest? = null
        val client = PaymentManagementClient(
            transport = PaymentManagementTransport { request ->
                captured = request
                PaymentManagementHttpResponse(
                    200,
                    json.encodeToString(PaymentIntegrationMutationDto(changed = true)),
                )
            }
        )
        client.saveIntegration(
            PaymentManagedProvider.WEBKASSA,
            PaymentIntegrationSecretPatchRequest(
                storeId = "store-1",
                secrets = mapOf("apiToken" to "never-log-me"),
            )
        )
        assertEquals(true, captured?.containsWriteOnlySecrets)
        assertEquals(false, captured?.idempotencyKey.isNullOrBlank())
    }

    @Test
    fun safeServerErrorDoesNotExposeRequestSecret() = runTest {
        val client = PaymentManagementClient(
            transport = PaymentManagementTransport {
                PaymentManagementHttpResponse(
                    409,
                    json.encodeToString(PaymentApiErrorDto("REVISION_CONFLICT", "Changed elsewhere", currentRevision = 4)),
                )
            }
        )
        val error = assertFailsWith<PaymentManagementApiException> {
            client.integrations("store-1")
        }
        assertEquals("REVISION_CONFLICT", error.code)
        assertEquals(4, error.currentRevision)
    }
}
