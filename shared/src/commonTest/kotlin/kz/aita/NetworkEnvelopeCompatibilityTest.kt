package kz.aita

import io.ktor.http.HttpStatusCode
import kotlin.test.*

class NetworkEnvelopeCompatibilityTest {
    @Test fun stringEncodedAndTypedEnvelopesGiveTheSameResult() {
        val stringEncoded="""{"message":null,"negative":false,"payload":"[1,2,3]"}"""
        val typed="""{"message":null,"negative":false,"payload":[1,2,3]}"""
        assertEquals(decodeNetworkResponseDataModel<List<Int>>(typed,HttpStatusCode.OK),
            decodeNetworkResponseDataModel<List<Int>>(stringEncoded,HttpStatusCode.OK))
    }
    @Test fun invalidPayloadDoesNotBecomeSuccessAndTransportFailureSurvives() {
        val response=decodeNetworkResponseDataModel<List<Int>>("""{"message":null,"negative":false,"payload":"oops","transportFailure":true}""",HttpStatusCode.OK)
        assertTrue(response.negative);assertTrue(response.transportFailure);assertNull(response.payload)
    }
    @Test fun httpFailureCannotBeOverriddenByEnvelopeSuccess() {
        val response=decodeNetworkResponseDataModel<List<Int>>("""{"message":null,"negative":false,"payload":"[1]"}""",HttpStatusCode.ServiceUnavailable)
        assertTrue(response.negative);assertTrue(response.transportFailure)
    }
}
