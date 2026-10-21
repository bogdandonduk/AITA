package kz.aita.server

import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.*
import io.ktor.server.response.header
import io.ktor.server.routing.*
import kz.aita.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.UUID

internal fun shiftReportCanReadInsideTransaction(user: UUID, store: UUID, worker: UUID): Boolean =
    userHasStoreAccessInsideTransaction(user, store) &&
        (user == worker || userHasStorePermissionInsideTransaction(user, store, STORE_PERMISSION_ANALYTICS_VIEW))

internal fun shiftReportsInsideTransaction(user: UUID, store: UUID): List<WorkshiftDataModel>? {
    if (!userHasStoreAccessInsideTransaction(user, store)) return null
    val manager = userHasStorePermissionInsideTransaction(user, store, STORE_PERMISSION_ANALYTICS_VIEW)
    return Workshifts.innerJoin(Stores, { Workshifts.storeId }, { Stores.id })
        .innerJoin(Users, { Workshifts.workerUserId }, { Users.id }).selectAll()
        .where { (Workshifts.storeId eq store) and (if (manager) Op.TRUE else Workshifts.workerUserId eq user) }
        .orderBy(Workshifts.startedAtMillis, SortOrder.DESC).limit(100).map { it.toWorkshiftDataModel() }
}

internal fun shiftReportInsideTransaction(user: UUID, store: UUID, shiftId: UUID, kind: String, now: Long): ShiftReport? {
    val row = Workshifts.innerJoin(Stores, { Workshifts.storeId }, { Stores.id })
        .innerJoin(Users, { Workshifts.workerUserId }, { Users.id }).selectAll()
        .where { (Workshifts.id eq shiftId) and (Workshifts.storeId eq store) }.singleOrNull() ?: return null
    if (!shiftReportCanReadInsideTransaction(user, store, row[Workshifts.workerUserId])) return null
    val shift = row.toWorkshiftDataModel()
    if ((kind == SHIFT_REPORT_X && (!shift.isActive || shift.endedAtMillis != null)) ||
        (kind == SHIFT_REPORT_Z && (shift.isActive || shift.endedAtMillis == null))) return null
    val through = shift.endedAtMillis ?: now
    val transactions = Transactions.selectAll().where {
        (Transactions.storeId eq store) and (Transactions.userId eq row[Workshifts.workerUserId]) and
            (Transactions.timeMillis greaterEq shift.startedAtMillis) and
            (if (kind == SHIFT_REPORT_X) Transactions.timeMillis lessEq through else Transactions.timeMillis less through)
    }.map { it.toTransactionDataModel() }
    return buildShiftReport(shift, kind, transactions, now)
}

internal fun Route.shiftReportRoutes() {
    authenticate("auth-jwt") {
        get("/workshifts/reports") {
            val user = call.checkPrincipal() ?: return@get
            val store = call.request.header("store_id")?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?: return@get call.respondAitaUnauthorized()
            call.response.header("Cache-Control", "private, no-store")
            val result = newSuspendedTransaction(aitaServerIoContext) { shiftReportsInsideTransaction(user, store) }
            if (result == null) call.genericResponseNoPayload(HttpStatusCode.Forbidden, eventMessage("shift.unavailable"))
            else call.genericResponse(HttpStatusCode.OK, result)
        }
        get("/workshifts/reports/{shiftId}") {
            val user = call.checkPrincipal() ?: return@get
            val store = call.request.header("store_id")?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            val shift = call.parameters["shiftId"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            val kind = call.request.queryParameters["kind"]
            call.response.header("Cache-Control", "private, no-store")
            if (store == null || shift == null || kind !in setOf(SHIFT_REPORT_X, SHIFT_REPORT_Z)) {
                call.genericResponseNoPayload(HttpStatusCode.BadRequest, eventMessage("shift.unavailable")); return@get
            }
            val result = newSuspendedTransaction(aitaServerIoContext) { shiftReportInsideTransaction(user, store, shift, kind!!, System.currentTimeMillis()) }
            if (result == null) call.genericResponseNoPayload(HttpStatusCode.Forbidden, eventMessage("shift.unavailable"))
            else call.genericResponse(HttpStatusCode.OK, result)
        }
    }
}
