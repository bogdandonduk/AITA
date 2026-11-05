package kz.aita

import kotlin.test.*

class MarketDeliveryActionDraftTest {
    private fun id(n: Int) = "00000000-0000-0000-0000-${n.toString().padStart(12,'0')}"
    private fun order() = MarketDeliveryOrder(id(1),id(2),id(3),"Shop",
        MarketDeliveryRequest(id(3),1,1,MarketDeliveryMode.PICKUP,MarketDeliveryAddress("Buyer","+77000000000"),
            listOf(MarketDeliveryLine(MarketShoppingLine(id(4),id(3),"Tea","Shop",1,
                MarketShoppingBasis(currencyCode="KZT",unitId="piece",pricedAmount=1.0)),10000)),"KZT",0),
        createdAtMillis=1000,updatedAtMillis=1000,estimatedFromMillis=2000,estimatedToMillis=3000)
    private fun ready(draft: MarketDeliveryActionDraft, action: MarketDeliveryAction, buyer: Boolean = false, now: Long = 1000) =
        assertIs<MarketDeliveryActionPreparation.Ready>(draft.prepare(order(),action,buyer,id(8),now)).command
    private fun invalid(draft: MarketDeliveryActionDraft, action: MarketDeliveryAction, key: String) {
        assertEquals("delivery.$key",assertIs<MarketDeliveryActionPreparation.Invalid>(draft.prepare(order(),action,false,id(8),1000)).messageKey)
    }
    @Test fun cancelledDraftFieldsCannotLeakIntoAnotherActionOrItsTimeline() {
        val dirty=MarketDeliveryActionDraft("Private discarded cancellation reason","12345678","987654","30","60")
        for(action in listOf(MarketDeliveryAction.CONFIRM,MarketDeliveryAction.PACK,MarketDeliveryAction.READY,
            MarketDeliveryAction.ACCEPT_ASSIGNMENT,MarketDeliveryAction.DISPATCH,MarketDeliveryAction.ARRIVE)) {
            val command=ready(dirty,action)
            assertEquals("",command.note);assertNull(command.courierPublicId);assertNull(command.handoverCode)
            assertNull(command.estimatedFromMillis);assertNull(command.estimatedToMillis)
            assertEquals(order().id,command.orderId);assertEquals(order().revision,command.expectedRevision)
        }
        val received=ready(dirty,MarketDeliveryAction.DELIVER,buyer=true)
        assertNull(received.handoverCode);assertEquals("",received.note)
        val assigned=ready(dirty,MarketDeliveryAction.ASSIGN)
        assertEquals("12345678",assigned.courierPublicId);assertEquals("",assigned.note);assertNull(assigned.handoverCode)
    }
    @Test fun missingReasonsAndMalformedHandoverProofStayLocalWithoutAnOutboxCommand() {
        for(reason in listOf("", "  ", "x".repeat(501), "bad\u0000reason"))
            invalid(MarketDeliveryActionDraft(note=reason),MarketDeliveryAction.CANCEL,"reason_required")
        assertEquals("Changed plans",ready(MarketDeliveryActionDraft(note="  Changed plans  "),MarketDeliveryAction.CANCEL).note)
        for(code in listOf("", "12345", "1234567", "１２３４５６", "abcdef"))
            invalid(MarketDeliveryActionDraft(handoverCode=code),MarketDeliveryAction.DELIVER,"code_invalid")
        assertEquals("123456",ready(MarketDeliveryActionDraft(handoverCode=" 123456 "),MarketDeliveryAction.DELIVER).handoverCode)
        invalid(MarketDeliveryActionDraft(),MarketDeliveryAction.ASSIGN,"courier_invalid")
        invalid(MarketDeliveryActionDraft(courierPublicId="1".repeat(17)),MarketDeliveryAction.ASSIGN,"courier_invalid")
    }
    @Test fun reschedulingRequiresAPlausibleFutureWindowAndDoesNotBorrowHiddenProofFields() {
        val draft=MarketDeliveryActionDraft("Traffic", "unused", "123456", "30", "60")
        val command=ready(draft,MarketDeliveryAction.DELAY)
        assertEquals(1_801_000L,command.estimatedFromMillis);assertEquals(3_601_000L,command.estimatedToMillis)
        assertEquals("Traffic",command.note);assertNull(command.handoverCode);assertNull(command.courierPublicId)
        for((from,to) in listOf("" to "60","0" to "60","-1" to "60","61" to "60","30" to "129601","1.5" to "60"))
            invalid(draft.copy(fromMinutes=from,toMinutes=to),MarketDeliveryAction.DELAY,"delay_invalid")
        invalid(draft.copy(note=""),MarketDeliveryAction.DELAY,"delay_invalid")
        assertIs<MarketDeliveryActionPreparation.Invalid>(draft.prepare(order(),MarketDeliveryAction.DELAY,false,id(8),Long.MAX_VALUE))
    }
    @Test fun checkoutCannotAccidentallyUseTheOrderActionForm() {
        invalid(MarketDeliveryActionDraft(),MarketDeliveryAction.REQUEST,"invalid")
    }
}
