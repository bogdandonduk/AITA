package kz.aita

/** Screen drafts can outlive a cancelled action. Only the fields shown for the currently
 * selected action may enter a command or its permanent order timeline.
 */
data class MarketDeliveryActionDraft(val note: String = "", val courierPublicId: String = "",
    val handoverCode: String = "", val fromMinutes: String = "", val toMinutes: String = "") {
    fun prepare(order: MarketDeliveryOrder, action: MarketDeliveryAction, buyer: Boolean,
        commandId: String, now: Long): MarketDeliveryActionPreparation {
        fun invalid(key: String) = MarketDeliveryActionPreparation.Invalid("delivery.$key")
        if (action == MarketDeliveryAction.REQUEST || marketDiscoveryId(commandId) != commandId ||
            marketDiscoveryId(order.id) != order.id || order.revision !in 0 until Long.MAX_VALUE) return invalid("invalid")
        val needsNote = action in setOf(MarketDeliveryAction.CANCEL, MarketDeliveryAction.DELAY)
        val reason = if (needsNote) note.trim() else ""
        if (needsNote && (reason.isEmpty() || reason.length > 500 || reason.any { it.code < 32 && it != '\n' }))
            return invalid(if (action == MarketDeliveryAction.CANCEL) "reason_required" else "delay_invalid")
        val courier = if (action == MarketDeliveryAction.ASSIGN) courierPublicId.trim() else null
        if (courier != null && (courier.length !in 1..16 || courier.any { it.code < 32 })) return invalid("courier_invalid")
        val proof = if (action == MarketDeliveryAction.DELIVER && !buyer) handoverCode.trim() else null
        if (proof != null && !proof.matches(Regex("[0-9]{6}"))) return invalid("code_invalid")
        var from: Long? = null
        var to: Long? = null
        if (action == MarketDeliveryAction.DELAY) {
            val start = fromMinutes.trim().toLongOrNull() ?: return invalid("delay_invalid")
            val end = toMinutes.trim().toLongOrNull() ?: return invalid("delay_invalid")
            // Zero would already be in the past by the time the server receives this command.
            if (start !in 1..129_600 || end !in start..129_600 || now <= 0 || now > Long.MAX_VALUE - end * 60_000)
                return invalid("delay_invalid")
            from = now + start * 60_000; to = now + end * 60_000
        }
        return MarketDeliveryActionPreparation.Ready(MarketDeliveryCommand(commandId, action, order.id, order.revision,
            courierPublicId = courier, note = reason, handoverCode = proof, estimatedFromMillis = from, estimatedToMillis = to))
    }
}
sealed interface MarketDeliveryActionPreparation {
    data class Ready(val command: MarketDeliveryCommand) : MarketDeliveryActionPreparation
    data class Invalid(val messageKey: String) : MarketDeliveryActionPreparation
}
