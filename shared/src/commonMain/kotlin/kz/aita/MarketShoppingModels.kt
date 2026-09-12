package kz.aita

import kotlinx.serialization.Serializable

const val MARKET_SHOPPING_MAX_LINES = 50
const val MARKET_SHOPPING_MAX_UNITS = 999
const val MARKET_QUOTE_ESTIMATED = "estimated"
const val MARKET_QUOTE_QUANTITY = "confirm_quantity"
const val MARKET_QUOTE_PRICE = "confirm_price"
const val MARKET_QUOTE_UNAVAILABLE = "unavailable"
const val MARKET_QUOTE_CHANGED = "selling_unit_changed"

/** The unit the buyer actually selected, not a price promise. Never silently reinterpret a list. */
@Serializable
data class MarketShoppingBasis(
    val gtin: String? = null,
    val currencyCode: String,
    val unitId: String,
    val pricedAmount: Double
)

fun MarketShoppingBasis.isValidMarketBasis(): Boolean =
    (gtin == null || (marketCanonicalGtin(gtin) == gtin)) &&
        currencyCode.matches(Regex("[A-Z]{3}")) && unitId.isNotBlank() && unitId.length <= 120 &&
        pricedAmount.isFinite() && pricedAmount > 0.0 && pricedAmount <= 1_000_000.0

fun MarketOffer.shoppingBasis(): MarketShoppingBasis? {
    if (priceMinor == null || priceMinor < 0L) return null
    return MarketShoppingBasis(gtin?.let(::marketCanonicalGtin), currencyCode ?: return null,
        unitId ?: return null, pricedAmount ?: return null).takeIf { it.isValidMarketBasis() }
}

/** Account-owned intent. Public labels are retained even when the merchant withdraws the listing.
 * units means multiples of pricedAmount (e.g. 3 x 500 g), NOT an unlabelled stock quantity.
 */
@Serializable
data class MarketShoppingLine(
    val offerId: String,
    val storeId: String,
    val title: String,
    val shopName: String,
    val units: Int,
    val basis: MarketShoppingBasis,
    val unitName: List<LocalizedStringDataModel> = emptyList(),
    val updatedAtMillis: Long = 0L
)

@Serializable
data class MarketShoppingQuotedLine(
    val line: MarketShoppingLine,
    val offer: MarketOffer? = null,
    val unitPriceMinor: Long? = null,
    val subtotalMinor: Long? = null,
    val status: String = MARKET_QUOTE_UNAVAILABLE
)

@Serializable
data class MarketShoppingSnapshot(
    val userId: String,
    val revision: Long = 0L,
    val lines: List<MarketShoppingQuotedLine> = emptyList(),
    val checkedAtMillis: Long = 0L
)

/** An absolute desired quantity, not a replayable increment. 0 removes the specific line. */
@Serializable
data class MarketShoppingCommand(
    val commandId: String,
    val expectedRevision: Long,
    val offerId: String,
    val units: Int,
    val basis: MarketShoppingBasis? = null
)

@Serializable
data class MarketShoppingOutcome(
    val commandId: String,
    val accepted: Boolean,
    val appliedRevision: Long? = null,
    val replayed: Boolean = false,
    val errorKey: String? = null,
    val snapshot: MarketShoppingSnapshot
)

@Serializable
data class PendingMarketShoppingCommand(val accountId: String, val command: MarketShoppingCommand)

/** One atomic local KV value: an unresolved command is never cleared independently of its ack. */
@Serializable
data class MarketShoppingJournal(
    val accountId: String,
    val snapshot: MarketShoppingSnapshot? = null,
    val pending: PendingMarketShoppingCommand? = null,
    val localRevision: Long = 0L
)

fun MarketShoppingJournal.prepare(command: PendingMarketShoppingCommand): MarketShoppingJournal {
    require(command.accountId == accountId)
    check(pending == null || pending == command) { "An earlier shopping-list change is unresolved" }
    return if (pending == command) this else copy(pending = command, localRevision = localRevision + 1L)
}

fun MarketShoppingSnapshot?.acceptShoppingSnapshot(next: MarketShoppingSnapshot): MarketShoppingSnapshot {
    if (this == null || userId != next.userId) return next
    return if (next.revision > revision || (next.revision == revision && next.checkedAtMillis >= checkedAtMillis)) next else this
}

fun MarketShoppingJournal.withSnapshot(next: MarketShoppingSnapshot): MarketShoppingJournal {
    require(next.userId == accountId)
    val accepted = snapshot.acceptShoppingSnapshot(next)
    return if (accepted == snapshot) this else copy(snapshot = accepted, localRevision = localRevision + 1L)
}

fun MarketShoppingJournal.acknowledge(command: PendingMarketShoppingCommand, outcome: MarketShoppingOutcome): MarketShoppingJournal {
    require(command.accountId == accountId && outcome.snapshot.userId == accountId)
    require(outcome.commandId == command.command.commandId)
    // Even a late ack may refresh data, but it can never retire another command.
    val nextSnapshot = snapshot.acceptShoppingSnapshot(outcome.snapshot)
    val nextPending = if (pending == command) null else pending
    return if (snapshot == nextSnapshot && pending == nextPending) this else copy(snapshot = nextSnapshot,
        pending = nextPending, localRevision = localRevision + 1L)
}

/** Pure integer arithmetic; quantities/currencies are never guessed or converted. */
fun marketShoppingSubtotal(unitPriceMinor: Long?, units: Int): Long? {
    val price = unitPriceMinor?.takeIf { it >= 0L } ?: return null
    if (units !in 1..MARKET_SHOPPING_MAX_UNITS || price > Long.MAX_VALUE / units) return null
    return price * units
}

data class MarketShoppingGroup(
    val storeId: String, val shopName: String, val currencyCode: String,
    val lines: List<MarketShoppingQuotedLine>, val pricedSubtotalMinor: Long?,
    val unpricedLines: Int, val confirmationLines: Int
)

fun MarketShoppingSnapshot.shoppingGroups(): List<MarketShoppingGroup> = lines
    .groupBy { it.line.storeId to it.line.basis.currencyCode }
    .map { (key, values) ->
        var sum = 0L
        var overflow = false
        values.forEach { row -> row.subtotalMinor?.let { amount ->
            if (amount < 0L || sum > Long.MAX_VALUE - amount) overflow = true else sum += amount
        } }
        MarketShoppingGroup(key.first, values.first().offer?.storefront?.displayName ?: values.first().line.shopName,
            key.second, values, if (overflow || values.all { it.subtotalMinor == null }) null else sum,
            values.count { it.subtotalMinor == null }, values.count { it.status != MARKET_QUOTE_ESTIMATED })
    }.sortedWith(compareBy<MarketShoppingGroup> { it.shopName.lowercase() }.thenBy { it.storeId }.thenBy { it.currencyCode })
