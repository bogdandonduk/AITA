package kz.aita.server

import kz.aita.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.json.jsonb
import java.time.Instant
import java.util.UUID

internal object WorkerPlacementCommands : Table("worker_placement_commands") {
    val id = uuid("id")
    val actorId = uuid("actor_id")
    val subjectUserId = uuid("subject_user_id")
    val request = jsonb("request", Json, WorkerPlacementCommand.serializer())
    val result = jsonb("result", Json, StoreWorkerDataModel.serializer())
    override val primaryKey = PrimaryKey(id)
}

internal class WorkerPlacementFailure(val key: String, val status: Int = 409) : RuntimeException(key)

internal fun placeWorkerInsideTransaction(actor: UUID, command: WorkerPlacementCommand): StoreWorkerDataModel {
    fun fail(key: String, status: Int = 409): Nothing = throw WorkerPlacementFailure(key, status)
    fun uuid(value: String) = runCatching { UUID.fromString(value) }.getOrNull() ?: fail("invalid", 400)
    val operation = uuid(command.operationId)
    val source = uuid(command.sourceStoreId)
    val destination = uuid(command.destinationStoreId)
    val membership = uuid(command.membershipId)
    val root = activeRootStoreIdForAccessInsideTransaction(source) ?: fail("changed")
    if (source == destination || destination == root || activeRootStoreIdForAccessInsideTransaction(destination) != root) fail("invalid", 400)
    // Serialize all assignments in this company, including retries with a lost response.
    Stores.select(Stores.id).where { Stores.id eq root }.forUpdate().single()
    fun mayPlace(permissions: List<String>) = actorCanManageExistingWorkerPermissionsInsideTransaction(actor, source, permissions) &&
        userHasStorePermissionInsideTransaction(actor, destination, STORE_PERMISSION_WORKERS_INVITE) &&
        (isStoreOwnerInsideTransaction(actor, destination) || activeWorkerPermissionsInsideTransaction(actor, destination).containsAll(permissions)) &&
        (source == root || userHasStorePermissionInsideTransaction(actor, source, STORE_PERMISSION_WORKERS_REMOVE))
    val existing = WorkerPlacementCommands.selectAll().where { WorkerPlacementCommands.id eq operation }.singleOrNull()
    if (existing != null) {
        if (existing[WorkerPlacementCommands.actorId] != actor || existing[WorkerPlacementCommands.request] != command) fail("changed")
        if (!mayPlace(existing[WorkerPlacementCommands.result].permissions)) fail("denied", 403)
        return existing[WorkerPlacementCommands.result]
    }
    val row = StoreWorkerMemberships.selectAll().where {
        (StoreWorkerMemberships.id eq membership) and (StoreWorkerMemberships.storeId eq source) and (StoreWorkerMemberships.isActive eq true)
    }.forUpdate().singleOrNull() ?: fail("changed")
    val worker = row[StoreWorkerMemberships.userId]
    val permissions = normalizeStorePermissionIds(row[StoreWorkerMemberships.permissions])
    if (!mayPlace(permissions)) fail("denied", 403)
    if (StoreWorkerMemberships.select(StoreWorkerMemberships.id).where {
        (StoreWorkerMemberships.userId eq worker) and (StoreWorkerMemberships.storeId eq destination) and (StoreWorkerMemberships.isActive eq true)
    }.count() > 0) fail("duplicate")
    if (StoreWorkerRequests.select(StoreWorkerRequests.id).where {
        (StoreWorkerRequests.requesterUserId eq worker) and (StoreWorkerRequests.storeId inList listOf(source, destination)) and
            (StoreWorkerRequests.status inList listOf(WORKER_REQUEST_STATUS_PENDING, WORKER_REQUEST_STATUS_INVITED))
    }.count() > 0) fail("pending")
    if (source != root && Workshifts.select(Workshifts.id).where {
        (Workshifts.workerUserId eq worker) and (Workshifts.storeId eq source) and (Workshifts.isActive eq true) and Workshifts.endedAtMillis.isNull()
    }.count() > 0) fail("shift")
    val now = System.currentTimeMillis()
    val id = UUID.randomUUID()
    if (source != root) {
        StoreWorkerMemberships.update({ StoreWorkerMemberships.id eq membership }) {
            it[isActive] = false; it[updatedAt] = Instant.now()
        }
        // This table is a legacy access mirror. Leaving it behind would retain the old branch's access.
        StoreUsers.deleteWhere { (StoreUsers.userId eq worker) and (StoreUsers.storeId eq source) }
    }
    StoreWorkerMemberships.insert {
        it[StoreWorkerMemberships.id] = id; it[storeId] = destination; it[userId] = worker
        it[roleId] = row[StoreWorkerMemberships.roleId]; it[StoreWorkerMemberships.permissions] = permissions
        it[jobTitle] = row[StoreWorkerMemberships.jobTitle]; it[jobTitleLocalized] = row[StoreWorkerMemberships.jobTitleLocalized]
        it[salary] = row[StoreWorkerMemberships.salary]; it[salaryCurrencyCode] = row[StoreWorkerMemberships.salaryCurrencyCode]
        it[workshiftPasswordHash] = row[StoreWorkerMemberships.workshiftPasswordHash]
        it[requestedAtMillis] = now; it[acceptedAtMillis] = now; it[acceptedByUserId] = actor; it[isActive] = true
    }
    if (StoreUsers.selectAll().where { (StoreUsers.storeId eq destination) and (StoreUsers.userId eq worker) }.empty()) {
        StoreUsers.insert { it[storeId] = destination; it[userId] = worker }
    }
    if (source != root) Users.update({ (Users.id eq worker) and (Users.activeStoreId eq source) }) { it[activeStoreId] = destination }
    val result = StoreWorkerMemberships.innerJoin(Stores, { StoreWorkerMemberships.storeId }, { Stores.id })
        .innerJoin(Users, { StoreWorkerMemberships.userId }, { Users.id }).selectAll()
        .where { StoreWorkerMemberships.id eq id }.single().toStoreWorkerDataModel()
    WorkerPlacementCommands.insert {
        it[WorkerPlacementCommands.id] = operation; it[actorId] = actor; it[subjectUserId] = worker
        it[request] = command; it[WorkerPlacementCommands.result] = result
    }
    insertServerNotificationInsideTransaction(worker, destination,
        title = EventMessageReference("worker.placement.title"),
        message = EventMessageReference(if (source == root) "worker.placement.assigned" else "worker.placement.moved",
            children = mapOf("store" to listOf(eventNamedFact(result.storeName.extractLocalizedString("en").orEmpty(), "event.fallback.store")))),
        type = NotificationType.Positive, category = "workers",
        metadata = mapOf("operationId" to "worker_placement_$operation", "workerId" to id.toString()), nowMillis = now)
    listOf(source, destination).forEach { store ->
        insertOperationLogInsideTransaction(actor, store, "transferred", "worker", id.toString(), workerPlacementMessage("done"),
            metadata = mapOf("operationId" to command.operationId, "sourceStoreId" to source.toString(),
                "destinationStoreId" to destination.toString(), "workerUserId" to worker.toString(), "previousMembershipId" to membership.toString()),
            now = now, required = true)
    }
    return result
}
