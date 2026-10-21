package kz.aita

import io.ktor.http.HttpMethod
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlin.math.round

const val SHIFT_REPORT_X = "X"
const val SHIFT_REPORT_Z = "Z"

@Serializable
data class ShiftReportTotals(
    val currencyCode: String,
    val saleCount: Int = 0, val returnCount: Int = 0, val supplyCount: Int = 0,
    val sales: Double = 0.0, val returns: Double = 0.0, val supplies: Double = 0.0,
    val saleCash: Double = 0.0, val saleCard: Double = 0.0,
    val returnCash: Double = 0.0, val returnCard: Double = 0.0,
    val discount: Double = 0.0, val debt: Double = 0.0,
    val paymentsKnown: Boolean = true
)

@Serializable
data class ShiftReport(
    val shift: WorkshiftDataModel,
    val kind: String,
    val generatedAtMillis: Long,
    val throughMillis: Long,
    val transactionCount: Int,
    val totals: List<ShiftReportTotals>,
    // Local-only receipts may be awaiting upload; this is never called a final cloud total.
    val localOnly: Boolean = false
)

/** Historical transaction.workshiftId is a numeric legacy field, not Workshifts' UUID.
 * Use the persisted actor and the employee's shift interval, never that unrelated number.
 * End is exclusive, so consecutive shifts cannot count the same boundary receipt twice. */
fun buildShiftReport(shift: WorkshiftDataModel, kind: String, transactions: List<TransactionDataModel>,
    nowMillis: Long, localOnly: Boolean = false): ShiftReport {
    require(shift.id.isNotBlank() && shift.storeId.isNotBlank() && shift.workerUserId.isNotBlank())
    require(shift.startedAtMillis > 0 && nowMillis >= shift.startedAtMillis)
    require((kind == SHIFT_REPORT_X && shift.isActive && shift.endedAtMillis == null) ||
        (kind == SHIFT_REPORT_Z && !shift.isActive && shift.endedAtMillis != null))
    val through = shift.endedAtMillis ?: nowMillis
    require(through >= shift.startedAtMillis && through <= nowMillis)
    val receipts = transactions.filter { it.storeId == shift.storeId && it.actorUserId == shift.workerUserId &&
        it.timeMillis >= shift.startedAtMillis && (if (kind == SHIFT_REPORT_X) it.timeMillis <= through else it.timeMillis < through) }
    val currencies = receipts.flatMap { it.goodsInTransaction.map { line -> line.currencyCode.orEmpty().trim().uppercase() } }.distinct().sorted()
    fun Double.moneyRound() = round(this * 100.0) / 100.0
    val totals = currencies.map { currency ->
        val matching = receipts.filter { tx -> tx.goodsInTransaction.any { it.currencyCode.orEmpty().trim().uppercase() == currency } }
        val sales = matching.filter { it.type == "purchase" }; val returns = matching.filter { it.type == "return" }
        val supplies = matching.filter { it.type == "accept" }
        fun lines(tx: TransactionDataModel) = tx.goodsInTransaction.filter { it.currencyCode.orEmpty().trim().uppercase() == currency }
        fun sum(txs: List<TransactionDataModel>) = txs.sumOf { tx -> lines(tx).sumOf { it.pricePerUnit * it.quantity } }.moneyRound()
        // Old mixed-currency receipts do not specify a currency per payment. Do not invent an allocation.
        val known = matching.all { tx -> tx.goodsInTransaction.map { it.currencyCode.orEmpty().trim().uppercase() }.distinct().size == 1 }
        ShiftReportTotals(currency, sales.size, returns.size, supplies.size, sum(sales), sum(returns), sum(supplies),
            if (known) sales.sumOf { it.paidCash }.moneyRound() else 0.0,
            if (known) sales.sumOf { it.paidCard }.moneyRound() else 0.0,
            if (known) returns.sumOf { it.paidCash }.moneyRound() else 0.0,
            if (known) returns.sumOf { it.paidCard }.moneyRound() else 0.0,
            sales.sumOf { tx -> lines(tx).sumOf { it.quickDiscountAmount() } }.moneyRound(),
            if (known) sales.sumOf { tx -> (lines(tx).sumOf { it.pricePerUnit * it.quantity } - tx.paidCash - tx.paidCard).coerceAtLeast(0.0) }.moneyRound() else 0.0,
            known)
    }
    return ShiftReport(shift, kind, nowMillis, through, receipts.size, totals, localOnly)
}

data class ClosedShiftReportOffer(val owner: MarketRequestScope, val shift: WorkshiftDataModel)

object ShiftReports {
    private val cacheMutex = Mutex()
    private val offer = MutableStateFlow<ClosedShiftReportOffer?>(null)
    val closedOffer = offer.asStateFlow()
    fun dismissOffer() { offer.value = null }
    private fun key(owner: MarketRequestScope) = "shift-reports-v1:${owner.accountId}:${owner.storeId}"
    private suspend fun saved(owner: MarketRequestScope): List<WorkshiftDataModel> =
        readRequiredJsonJournal(key(owner))?.let { jsonBase.decodeFromString(it) } ?: emptyList()

    suspend fun rememberShift(owner: MarketRequestScope, shift: WorkshiftDataModel, offerReport: Boolean = false) {
        if (!owner.isCurrent() || shift.storeId != owner.storeId || shift.workerUserId != owner.accountId) return
        try {
            cacheMutex.withLock {
                val merged = (listOf(shift) + saved(owner).filterNot { it.id == shift.id }).sortedByDescending { it.startedAtMillis }.take(100)
                if (owner.isCurrent()) writeJsonCacheText(key(owner), jsonBase.encodeToString(merged))
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { /* A storage failure must not turn a completed shift into a failed close. */ }
        if (offerReport && owner.isCurrent() && !shift.isActive && shift.endedAtMillis != null)
            offer.value = ClosedShiftReportOffer(owner, shift)
    }

    suspend fun cachedShifts(owner: MarketRequestScope): List<WorkshiftDataModel> {
        if (!owner.isCurrent()) return emptyList()
        val values = try { cacheMutex.withLock { saved(owner) } } catch (c: CancellationException) { throw c } catch (_: Exception) { emptyList() }
        val current = activeWorkshiftState.payloadValue
        return (listOfNotNull(current) + values).filter { it.storeId == owner.storeId && it.workerUserId == owner.accountId }
            .distinctBy { it.id }.sortedByDescending { it.startedAtMillis }
    }

    suspend fun loadShifts(owner: MarketRequestScope): ResponseDataModel<List<WorkshiftDataModel>> {
        if (!owner.isCurrent() || owner.storeId == null) return cloudSessionExpiredResponse()
        val response = networkRequest<List<WorkshiftDataModel>, Unit>(HttpMethod.Get, endpointUrl = "workshifts/reports",
            headers = mapOf("store_id" to owner.storeId), expectedSessionGeneration = owner.generation)
        if (!owner.isCurrent()) return cloudSessionExpiredResponse()
        if (!response.negative && response.payload?.any { it.storeId != owner.storeId || it.id.isBlank() ||
                it.workerUserId.isBlank() || it.startedAtMillis <= 0 ||
                (it.endedAtMillis != null && it.endedAtMillis < it.startedAtMillis) } == true)
            return ResponseDataModel(eventMessage("shift.unavailable"), null, true, 502)
        if (!response.negative) response.payload.orEmpty().filter { it.workerUserId == owner.accountId }.forEach { rememberShift(owner, it) }
        return response
    }

    suspend fun load(owner: MarketRequestScope, shift: WorkshiftDataModel, kind: String): ResponseDataModel<ShiftReport> {
        if (!owner.isCurrent() || owner.storeId != shift.storeId) return cloudSessionExpiredResponse()
        val response = networkRequest<ShiftReport, Unit>(HttpMethod.Get, endpointUrl = "workshifts/reports/${shift.id}",
            query = mapOf("kind" to kind), headers = mapOf("store_id" to shift.storeId), expectedSessionGeneration = owner.generation)
        if (!owner.isCurrent()) return cloudSessionExpiredResponse()
        val report = response.payload
        if (!response.negative && report != null && report.matches(shift, kind)) {
            try { writeJsonCacheText(key(owner) + ":$kind:${shift.id}", jsonBase.encodeToString(report)) }
            catch (c: CancellationException) { throw c } catch (_: Exception) { }
            return response
        }
        // Explicit denial is not an offline condition. Never hide it behind a cached report.
        if (response.httpStatusCode == 403 || response.httpStatusCode == 404 || response.httpStatusCode == 409) return response.copy(payload = null)
        if (response.transportFailure || response.httpStatusCode == 401 || (response.httpStatusCode ?: 0) >= 500) {
            val cached = try { readRequiredJsonJournal(key(owner) + ":$kind:${shift.id}")?.let { jsonBase.decodeFromString<ShiftReport>(it) } }
                catch (c: CancellationException) { throw c } catch (_: Exception) { null }
            if (cached != null && cached.matches(shift, kind) && owner.isCurrent())
                return ResponseDataModel(response.message, cached.copy(localOnly = true), false, response.httpStatusCode)
            val receipts = transactionsState.payloadValue
            if (receipts != null && shift.workerUserId == owner.accountId && owner.isCurrent()) {
                val local = runCatching { buildShiftReport(shift, kind, receipts, getCurrentTimeMillis(), localOnly = true) }.getOrNull()
                if (local != null) return ResponseDataModel(response.message, local, false, response.httpStatusCode)
            }
        }
        return response.copy(payload = null, negative = true)
    }

    private fun ShiftReport.matches(requested: WorkshiftDataModel, requestedKind: String): Boolean =
        shift.id == requested.id && shift.storeId == requested.storeId && shift.workerUserId == requested.workerUserId &&
            kind == requestedKind && transactionCount >= 0 && throughMillis >= shift.startedAtMillis &&
            generatedAtMillis >= throughMillis &&
            (if (kind == SHIFT_REPORT_Z) !shift.isActive && shift.endedAtMillis == throughMillis
             else kind == SHIFT_REPORT_X && shift.isActive && shift.endedAtMillis == null) &&
            totals.all { total -> listOf(total.sales, total.returns, total.supplies, total.saleCash, total.saleCard,
                total.returnCash, total.returnCard, total.discount, total.debt).all { it.isFinite() && it >= 0.0 } }
}
