package kz.aita

import kotlinx.serialization.Serializable

const val SUBSCRIPTION_BASIC_PLAN = "basic"
const val SUBSCRIPTION_LIFETIME_PLAN = "internal_lifetime"
const val SUBSCRIPTION_ACCESS_PAID = "paid"
const val SUBSCRIPTION_ACCESS_TIMED = "timed"
const val SUBSCRIPTION_ACCESS_LIFETIME = "lifetime"
const val SUBSCRIPTION_PROMO_DISCOUNT = "discount"

/** Catalogue fallback only; the backend's physical-location price is authoritative at checkout. */
fun basicStoreSubscriptionPlan(
    priceMinor: Long = 799_000L,
    currencyCode: String = "KZT",
    regionCode: String = "KZ",
    priceVersion: Long = 1L
): StoreSubscriptionPlanDataModel = StoreSubscriptionPlanDataModel(
    id = SUBSCRIPTION_BASIC_PLAN,
    name = listOf(LocalizedStringDataModel("en", "Basic"), LocalizedStringDataModel("ru", "Базовый"), LocalizedStringDataModel("kk", "Базалық"), LocalizedStringDataModel("ky", "Базалык")),
    description = listOf(
        LocalizedStringDataModel("en", "For this location · 21 workers · 1,000 stock items. Each branch subscribes separately."),
        LocalizedStringDataModel("ru", "Для этой точки · 21 сотрудник · 1 000 товаров. Подписка каждого филиала оплачивается отдельно."),
        LocalizedStringDataModel("kk", "Осы нүктеге · 21 қызметкер · 1 000 тауар. Әр филиалға бөлек жазылым қажет."),
        LocalizedStringDataModel("ky", "Бул жай үчүн · 21 кызматкер · 1,000 товар. Ар бир филиалга өзүнчө жазылуу керек.")
    ),
    priceMinor = priceMinor, currencyCode = currencyCode, regionCode = regionCode,
    maxBranches = 1, maxWorkers = 21, maxStockItems = 1000, priceVersion = priceVersion
)

fun lifetimeStoreSubscriptionPlan(regionCode: String, currencyCode: String): StoreSubscriptionPlanDataModel =
    basicStoreSubscriptionPlan(0L, currencyCode, regionCode).copy(
        id = SUBSCRIPTION_LIFETIME_PLAN, hidden = true,
        maxBranches = Int.MAX_VALUE, maxWorkers = Int.MAX_VALUE, maxStockItems = Int.MAX_VALUE,
        name = listOf(LocalizedStringDataModel("en", "Lifetime access"), LocalizedStringDataModel("ru", "Бессрочный доступ"), LocalizedStringDataModel("kk", "Мерзімсіз қолжетімділік"), LocalizedStringDataModel("ky", "Мөөнөтсүз мүмкүнчүлүк")),
        description = listOf(
            LocalizedStringDataModel("en", "Unlocked by a promo code for this location. No renewal charges."),
            LocalizedStringDataModel("ru", "Активирован промокодом для этой точки. Без списаний за продление."),
            LocalizedStringDataModel("kk", "Осы нүктеге промокодпен қосылған. Ұзарту үшін төлем алынбайды."),
            LocalizedStringDataModel("ky", "Бул жай үчүн промокод менен ачылган. Узартуу үчүн төлөм алынбайт.")
        )
    )

/** Membership and this exact store's entitlement are independent checks. Never inherit a parent's access. */
fun StoreSubscriptionStateDataModel.grantsStoreAccess(storeId: String?, nowMillis: Long): Boolean {
    if (storeId.isNullOrBlank() || this.storeId != storeId || status != SUBSCRIPTION_STATUS_ACTIVE) return false
    val started = currentPeriodStartMillis ?: startedAtMillis ?: return false
    if (started > nowMillis) return false
    if (accessKind == SUBSCRIPTION_ACCESS_LIFETIME) {
        return planId == SUBSCRIPTION_LIFETIME_PLAN && currentPeriodEndMillis == null && !autoRenew
    }
    if (accessKind !in setOf(SUBSCRIPTION_ACCESS_PAID, SUBSCRIPTION_ACCESS_TIMED)) return false
    return (currentPeriodEndMillis ?: return false) > nowMillis
}

/** A conservative ASCII code identity: no locale-dependent casing or look-alike characters. */
fun normalizeSubscriptionPromoCode(raw: String): String? = raw.trim().takeIf {
    it.length in 6..96 && it.all { c -> c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c == '-' || c == '_' }
}?.uppercase()

/** Fixed discounts are already in minor units. Percentage rounding never overcharges by a cent. */
fun subscriptionDiscountedPrice(priceMinor: Long, basisPoints: Int?, fixedMinor: Long?): Long {
    require(priceMinor in 0..1_000_000_000_000L)
    require((basisPoints == null) != (fixedMinor == null))
    val discount = if (basisPoints != null) {
        require(basisPoints in 1..10_000)
        (priceMinor * basisPoints) / 10_000L
    } else {
        require(requireNotNull(fixedMinor) > 0L)
        fixedMinor
    }
    return (priceMinor - discount.coerceAtMost(priceMinor)).coerceAtLeast(0L)
}

/** Shared route policy. Recovery/account routes stay available even after paid access expires. */
fun storeSubscriptionRequiredForEndpoint(endpoint: String): Boolean {
    val path = endpoint.substringBefore('?').substringBefore('#').trim('/').lowercase()
    val root = path.substringBefore('/')
    if (root in setOf("stock", "stockbatches", "transactions", "cashregister", "debtors", "analytics", "logs", "operationlogs")) return true
    if (root == "market") return path == "market/seller" || path.startsWith("market/seller/")
    if (root == "workshifts") return path != "workshifts/end"
    if (root == "workers") return path !in setOf("workers/my/get", "workers/requests/my", "workers/my/password", "workers/removal/confirm", "workers/removal/decline", "workers/invitations/decline")
    // Business identity and branch configuration stay manageable before/after paid operation.
    if (root == "stores") return false
    if (root == "payments") return !path.startsWith("payments/balance") && !path.startsWith("payments/topups") && !path.startsWith("payments/webhooks")
    // Relationship routes choose Store or Supplier access using the authoritative database row.
    return false
}

@Serializable
data class StoreSubscriptionQuoteRequestDataModel(val storeId: String, val planId: String = SUBSCRIPTION_BASIC_PLAN, val promoCode: String = "")

@Serializable
data class StoreSubscriptionQuoteDataModel(
    val storeId: String,
    val planId: String = SUBSCRIPTION_BASIC_PLAN,
    val accessKind: String = SUBSCRIPTION_ACCESS_PAID,
    val chargeMinor: Long,
    val regularPriceMinor: Long,
    val currencyCode: String,
    val priceVersion: Long,
    val durationMillis: Long? = null,
    val expectedRevision: Long,
    val validUntilMillis: Long,
    val canAutoRenew: Boolean = true
)
