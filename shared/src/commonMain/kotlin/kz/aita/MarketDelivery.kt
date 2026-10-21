package kz.aita

import kotlinx.serialization.Serializable

@Serializable enum class MarketDeliveryMode { STANDARD, EXPRESS, PICKUP }
@Serializable enum class MarketDeliveryStatus { REQUESTED, CONFIRMED, PREPARING, READY, ON_THE_WAY, ARRIVED, DELIVERED, CANCELLED }
@Serializable enum class MarketDeliveryAction { REQUEST, CONFIRM, PACK, READY, ASSIGN, ACCEPT_ASSIGNMENT, DISPATCH, ARRIVE, DELIVER, CANCEL, DELAY }

/** Explicit merchant opt-in. Fees are in the same currency as the reviewed goods quote. */
@Serializable data class MarketDeliveryPolicy(
    val branchStoreId: String, val revision: Long = 0,
    val modes: Set<MarketDeliveryMode> = emptySet(), val currency: String = "KZT",
    val standardFeeMinor: Long = 0, val expressFeeMinor: Long = 0,
    val standardMinDays: Int = 2, val standardMaxDays: Int = 5,
    val expressMinMinutes: Int = 45, val expressMaxMinutes: Int = 120,
    val serviceArea: String = "", val instructions: String = ""
) {
    fun fee(mode: MarketDeliveryMode): Long = when (mode) {
        MarketDeliveryMode.STANDARD -> standardFeeMinor
        MarketDeliveryMode.EXPRESS -> expressFeeMinor
        MarketDeliveryMode.PICKUP -> 0
    }
    fun isValid(): Boolean = marketDiscoveryId(branchStoreId) == branchStoreId && revision in 0 until Long.MAX_VALUE &&
        currency in setOf("KZT", "KGS", "TJS", "UZS", "RUB", "USD", "EUR") &&
        standardFeeMinor in 0..100_000_000_000 && expressFeeMinor in 0..100_000_000_000 &&
        standardMinDays in 1..60 && standardMaxDays in standardMinDays..90 &&
        expressMinMinutes in 10..720 && expressMaxMinutes in expressMinMinutes..1440 &&
        serviceArea.length <= 400 && instructions.length <= 1000 &&
        (modes.none { it != MarketDeliveryMode.PICKUP } || serviceArea.isNotBlank())
}

@Serializable data class MarketDeliveryAddress(
    val recipient: String = "", val phone: String = "", val address: String = "", val note: String = ""
) {
    fun isValid(mode: MarketDeliveryMode): Boolean = recipient.trim().length in 1..120 &&
        phone.matches(Regex("\\+?[0-9 ()-]{7,25}")) && phone.count(Char::isDigit) in 7..15 &&
        address.length <= 500 && (mode == MarketDeliveryMode.PICKUP || address.trim().length >= 5) && note.length <= 500 &&
        listOf(recipient, phone, address, note).none { it.any { c -> c.code < 32 && c != '\n' } }
}

@Serializable data class MarketDeliveryLine(val line: MarketShoppingLine, val unitPriceMinor: Long)

/** A delivery request is a separate acknowledged order. It does not charge a card or book a
 * fiscal sale. Merchant confirmation and fulfilment remain explicit; the shopping list is kept. */
@Serializable data class MarketDeliveryRequest(
    val storefrontId: String, val shoppingRevision: Long, val policyRevision: Long,
    val mode: MarketDeliveryMode, val address: MarketDeliveryAddress,
    val lines: List<MarketDeliveryLine>, val currency: String, val feeMinor: Long
)

@Serializable data class MarketCourierPosition(
    val latitude: Double, val longitude: Double, val accuracyMeters: Double,
    val capturedAtMillis: Long, val receivedAtMillis: Long = 0
) {
    fun isValid(now: Long): Boolean = latitude.isFinite() && latitude in -90.0..90.0 &&
        longitude.isFinite() && longitude in -180.0..180.0 && accuracyMeters.isFinite() &&
        accuracyMeters in 0.0..1000.0 && capturedAtMillis in (now - 120_000)..(now + 30_000)
    fun isFresh(now: Long): Boolean = receivedAtMillis > 0 && now >= receivedAtMillis && now - receivedAtMillis <= 120_000
}

@Serializable data class MarketDeliveryEvent(
    val action: MarketDeliveryAction, val status: MarketDeliveryStatus, val atMillis: Long,
    val note: String = "", val estimatedFromMillis: Long? = null, val estimatedToMillis: Long? = null
)

@Serializable data class MarketDeliveryOrder(
    val id: String, val buyerId: String, val branchStoreId: String, val shopName: String,
    val request: MarketDeliveryRequest, val revision: Long = 0,
    val status: MarketDeliveryStatus = MarketDeliveryStatus.REQUESTED,
    val createdAtMillis: Long, val updatedAtMillis: Long,
    val estimatedFromMillis: Long, val estimatedToMillis: Long,
    val courierId: String? = null, val courierName: String? = null, val courierAccepted: Boolean = false,
    val position: MarketCourierPosition? = null,
    // Only the buyer receives the handover code, only while fulfilment is active.
    val handoverCode: String? = null,
    val events: List<MarketDeliveryEvent> = emptyList(),
    val contactsRemoved: Boolean = false
) {
    val terminal: Boolean get() = status == MarketDeliveryStatus.DELIVERED || status == MarketDeliveryStatus.CANCELLED
    val itemsMinor: Long? get() = marketDeliveryItemsTotal(request.lines)
    val totalMinor: Long? get() = itemsMinor?.takeIf { request.feeMinor >= 0 && it <= Long.MAX_VALUE - request.feeMinor }?.plus(request.feeMinor)
}

/** Account deletion keeps fulfilment totals and history but removes personal contact details. */
fun MarketDeliveryOrder.withoutDeletedAccountContacts(account: String): MarketDeliveryOrder {
    require(terminal)
    val buyerDeleted = buyerId == account
    return copy(request = if (buyerDeleted) request.copy(address = MarketDeliveryAddress()) else request,
        contactsRemoved = contactsRemoved || buyerDeleted, courierName = courierName.takeUnless { courierId == account },
        position = null, handoverCode = null,
        events = if (buyerDeleted || courierId == account) events.map { it.copy(note = "") } else events)
}

fun marketDeliveryItemsTotal(lines: List<MarketDeliveryLine>): Long? {
    if (lines.isEmpty() || lines.size > MARKET_SHOPPING_MAX_LINES || lines.map { it.line.offerId }.distinct().size != lines.size) return null
    var sum = 0L
    for (row in lines) {
        val value = marketShoppingSubtotal(row.unitPriceMinor, row.line.units) ?: return null
        if (sum > Long.MAX_VALUE - value) return null
        sum += value
    }
    return sum
}

fun parseDeliveryFeeMinor(raw: String): Long? {
    val value=raw.trim().replace(',', '.')
    if (!value.matches(Regex("[0-9]{1,10}(\\.[0-9]{1,2})?"))) return null
    val pieces=value.split('.')
    val whole=pieces[0].toLongOrNull() ?: return null
    val fraction=pieces.getOrNull(1).orEmpty().padEnd(2,'0').toLongOrNull() ?: return null
    return (whole*100 + fraction).takeIf { it <= 100_000_000_000L }
}

@Serializable data class MarketDeliveryCheckoutDraft(val mode: MarketDeliveryMode = MarketDeliveryMode.STANDARD,
    val address: MarketDeliveryAddress = MarketDeliveryAddress())

@Serializable data class MarketDeliveryCommand(
    val commandId: String, val action: MarketDeliveryAction, val orderId: String? = null,
    val expectedRevision: Long = 0, val request: MarketDeliveryRequest? = null,
    val courierPublicId: String? = null, val note: String = "", val handoverCode: String? = null,
    val estimatedFromMillis: Long? = null, val estimatedToMillis: Long? = null
)
@Serializable data class MarketDeliveryOutcome(val commandId: String, val order: MarketDeliveryOrder, val replayed: Boolean = false)
@Serializable data class MarketDeliveryPage(val accountId: String, val view: String, val storeId: String? = null,
    val orders: List<MarketDeliveryOrder> = emptyList(), val nextBefore: String? = null, val checkedAtMillis: Long)
@Serializable data class MarketCourierLocationUpdate(val assignmentRevision: Long, val position: MarketCourierPosition)

/** Server and UI share the transition graph. Permission checks and handover proof stay server-side. */
fun MarketDeliveryOrder.availableActions(buyer: Boolean, manager: Boolean, courier: Boolean): Set<MarketDeliveryAction> = buildSet {
    if (terminal) return@buildSet
    if (manager) {
        when (status) {
            MarketDeliveryStatus.REQUESTED -> add(MarketDeliveryAction.CONFIRM)
            MarketDeliveryStatus.CONFIRMED -> add(MarketDeliveryAction.PACK)
            MarketDeliveryStatus.PREPARING -> add(MarketDeliveryAction.READY)
            MarketDeliveryStatus.READY -> if (request.mode != MarketDeliveryMode.PICKUP && courierAccepted) add(MarketDeliveryAction.DISPATCH)
            else -> Unit
        }
        if (status != MarketDeliveryStatus.REQUESTED && request.mode != MarketDeliveryMode.PICKUP)
            add(MarketDeliveryAction.ASSIGN)
        if (status != MarketDeliveryStatus.REQUESTED) add(MarketDeliveryAction.DELAY)
        if (status !in setOf(MarketDeliveryStatus.ON_THE_WAY, MarketDeliveryStatus.ARRIVED)) add(MarketDeliveryAction.CANCEL)
    }
    if (buyer) {
        if (status in setOf(MarketDeliveryStatus.REQUESTED, MarketDeliveryStatus.CONFIRMED, MarketDeliveryStatus.PREPARING, MarketDeliveryStatus.READY)) add(MarketDeliveryAction.CANCEL)
        if (status == MarketDeliveryStatus.ARRIVED || (status == MarketDeliveryStatus.READY && request.mode == MarketDeliveryMode.PICKUP)) add(MarketDeliveryAction.DELIVER)
    }
    if (courier) {
        if (!courierAccepted) add(MarketDeliveryAction.ACCEPT_ASSIGNMENT)
        if (courierAccepted && status == MarketDeliveryStatus.READY) add(MarketDeliveryAction.DISPATCH)
        if (courierAccepted && status == MarketDeliveryStatus.ON_THE_WAY) add(MarketDeliveryAction.ARRIVE)
        if (courierAccepted && status == MarketDeliveryStatus.ARRIVED) add(MarketDeliveryAction.DELIVER)
    }
}

fun MarketDeliveryAction.nextStatus(current: MarketDeliveryStatus) = when (this) {
    MarketDeliveryAction.CONFIRM -> MarketDeliveryStatus.CONFIRMED
    MarketDeliveryAction.PACK -> MarketDeliveryStatus.PREPARING
    MarketDeliveryAction.READY -> MarketDeliveryStatus.READY
    MarketDeliveryAction.DISPATCH -> MarketDeliveryStatus.ON_THE_WAY
    MarketDeliveryAction.ARRIVE -> MarketDeliveryStatus.ARRIVED
    MarketDeliveryAction.DELIVER -> MarketDeliveryStatus.DELIVERED
    MarketDeliveryAction.CANCEL -> MarketDeliveryStatus.CANCELLED
    else -> current
}
