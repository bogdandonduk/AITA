package kz.aita.server.support

import kz.aita.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.security.MessageDigest
import java.sql.Connection
import java.sql.ResultSet
import java.util.UUID

internal class SupportFailure(val key: String, val status: Int = 409) : RuntimeException(key)
internal fun supportFail(key: String, status: Int = 409): Nothing = throw SupportFailure(key, status)
internal fun supportUuid(raw: String): UUID = runCatching { UUID.fromString(raw) }.getOrNull()
    ?.takeIf { it.toString().equals(raw, true) } ?: supportFail("support.invalid", 400)
internal fun supportHash(vararg fields: String?): String {
    val input = fields.joinToString("") { it?.let { value -> "${value.length}:$value" } ?: "-1:" }
    return MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }
}

/** The caller owns one PostgreSQL transaction. No commits, external providers, or nested connections. */
internal class CompanySupportRepository(private val db: Connection, private val clock: () -> Long = System::currentTimeMillis) {
    init { check(!db.autoCommit) }
    private val now: Long get() = clock()
    private var authorityLocked = false
    private fun authorityLock() {
        if (!authorityLocked) {
            rows("SELECT pg_advisory_xact_lock_shared(hashtextextended('aita-company-authority-v1',0))") { true }
            authorityLocked = true
        }
    }
    private fun <T> rows(sql: String, vararg args: Any?, map: (ResultSet) -> T): List<T> =
        db.prepareStatement(sql).use { p ->
            args.forEachIndexed { i, v -> p.setObject(i + 1, v) }
            p.executeQuery().use { rs -> buildList { while (rs.next()) add(map(rs)) } }
        }
    private fun execute(sql: String, vararg args: Any?): Int = db.prepareStatement(sql).use { p ->
        args.forEachIndexed { i, v -> p.setObject(i + 1, v) }; p.executeUpdate()
    }
    private fun ResultSet.nullLong(key: String): Long? = getLong(key).takeUnless { wasNull() }
    private fun ResultSet.uuid(key: String): UUID? = getString(key)?.let(UUID::fromString)

    fun access(user: UUID): CompanyAccessDataModel {
        authorityLock()
        val now = clock()
        val employment = rows("""
            SELECT e.id,e.ends_at_millis FROM company_employments e JOIN users u ON u.id=e.user_id
            WHERE e.user_id=? AND e.status='active' AND u.is_active AND e.starts_at_millis<=?
              AND (e.ends_at_millis IS NULL OR e.ends_at_millis>?) ORDER BY e.id LIMIT 1
        """.trimIndent(), user, now, now) { it.getString("id") to it.nullLong("ends_at_millis") }.singleOrNull()
            ?: return CompanyAccessDataModel(user.toString(), serverTimeMillis = now, validUntilMillis = now + 45_000L)
        val jobs = rows("""
            SELECT j.id,j.title,a.ends_at_millis FROM company_job_assignments a
            JOIN company_jobs j ON j.id=a.job_id
            LEFT JOIN company_departments d ON d.id=j.department_id
            WHERE a.employment_id=? AND a.is_active AND j.is_active AND (d.id IS NULL OR d.is_active)
              AND a.starts_at_millis<=? AND (a.ends_at_millis IS NULL OR a.ends_at_millis>?)
            ORDER BY j.id
        """.trimIndent(), UUID.fromString(employment.first), now, now) {
            CompanyJobDataModel(it.getString("id"), jsonBase.decodeFromString(it.getString("title"))) to it.nullLong("ends_at_millis")
        }
        val caps = jobs.flatMap { (job, _) -> rows("SELECT capability_id FROM company_job_capabilities WHERE job_id=?", job.id) { it.getString(1) } }.toSet()
        val until = (listOf(now + 45_000L) + listOfNotNull(employment.second) + jobs.mapNotNull { it.second }).min()
        return CompanyAccessDataModel(user.toString(), employment.first, jobs.map { it.first }.distinctBy { it.id }, caps, now, until)
    }
    private fun requireCapability(user: UUID, capability: String): CompanyAccessDataModel = access(user).also {
        if (capability !in it.capabilities || it.employmentId == null) supportFail("support.denied", 403)
    }
    fun canAgent(user: UUID): Boolean = CompanyCapability.SUPPORT_QUEUE in access(user).capabilities

    private fun ticketRow(r: ResultSet) = SupportTicketDataModel(
        id = r.getString("id"), publicId = r.getString("public_id"), userId = r.getString("user_id"),
        storeId = r.getString("store_id"), subject = r.getString("subject"), category = r.getString("category"),
        priority = r.getString("priority"), status = r.getString("status"), assignedAgentUserId = r.getString("assigned_agent_user_id"),
        lastMessage = r.getString("last_message"), lastMessageAtMillis = r.getLong("last_message_at_millis"),
        lastCustomerMessageAtMillis = r.nullLong("last_customer_message_at_millis"), lastAgentMessageAtMillis = r.nullLong("last_agent_message_at_millis"),
        unreadForUserCount = r.getInt("unread_for_user_count"), unreadForAgentCount = r.getInt("unread_for_agent_count"),
        metadata = jsonBase.decodeFromString(r.getString("meta")), createdAtMillis = r.getLong("created_at_millis"),
        updatedAtMillis = r.getLong("updated_at_millis"), closedAtMillis = r.nullLong("closed_at_millis"),
        isActive = r.getBoolean("is_active"), revision = r.getLong("revision"))
    private fun messageRow(r: ResultSet) = SupportMessageDataModel(
        id = r.getString("id"), ticketId = r.getString("ticket_id"), userId = r.getString("user_id"),
        senderUserId = r.getString("sender_user_id"), senderRole = r.getString("sender_role"), senderDisplayName = r.getString("sender_display_name"),
        body = r.getString("body"), attachments = jsonBase.decodeFromString(r.getString("attachments")), metadata = jsonBase.decodeFromString(r.getString("meta")),
        clientMessageId = r.getString("client_message_id"), createdAtMillis = r.getLong("created_at_millis"),
        editedAtMillis = r.nullLong("edited_at_millis"), readByCustomerAtMillis = r.nullLong("read_by_customer_at_millis"),
        readByAgentAtMillis = r.nullLong("read_by_agent_at_millis"), isActive = r.getBoolean("is_active"), sequence = r.getLong("sequence"))

    private fun findTicket(id: UUID, lock: Boolean = false): SupportTicketDataModel =
        rows("SELECT * FROM support_tickets WHERE id=? AND is_active" + if (lock) " FOR UPDATE" else "", id, map = ::ticketRow)
            .singleOrNull() ?: supportFail("support.missing", 404)
    private fun authorizedTicket(user: UUID, id: UUID, agent: Boolean, lock: Boolean = false): SupportTicketDataModel {
        if (agent) requireCapability(user, CompanyCapability.SUPPORT_QUEUE)
        val ticket = findTicket(id, lock)
        if (!agent && ticket.userId != user.toString()) supportFail("support.missing", 404)
        return ticket
    }
    fun tickets(user: UUID, agent: Boolean, filter: String = "open", beforeMillis: Long? = null, beforeId: String? = null): SupportTicketPage {
        if (agent) requireCapability(user, CompanyCapability.SUPPORT_QUEUE)
        val where = mutableListOf("is_active"); val args = mutableListOf<Any?>()
        if (!agent) { where += "user_id=?"; args += user }
        when (filter) {
            "open" -> where += "status<>'closed'"
            "closed" -> where += "status='closed'"
            "mine" -> { where += "assigned_agent_user_id=? AND status<>'closed'"; args += user }
            "unassigned" -> {
                where += "status<>'closed' AND (assigned_agent_user_id IS NULL OR assigned_agent_user_id NOT IN (SELECT user_id FROM company_support_agent_access))"
            }
            "all" -> Unit
            else -> supportFail("support.invalid", 400)
        }
        if ((beforeMillis == null) != (beforeId == null)) supportFail("support.invalid", 400)
        if (beforeMillis != null) { where += "(updated_at_millis,id)<(?,?)"; args += beforeMillis; args += supportUuid(requireNotNull(beforeId)) }
        val result = rows("SELECT * FROM support_tickets WHERE ${where.joinToString(" AND ")} ORDER BY updated_at_millis DESC,id DESC LIMIT 51", *args.toTypedArray(), map = ::ticketRow)
        val visible = result.take(50); val last = visible.lastOrNull().takeIf { result.size > 50 }
        return SupportTicketPage(visible, last?.id, last?.updatedAtMillis)
    }
    fun customerTickets(user: UUID): List<SupportTicketDataModel> =
        rows("SELECT * FROM support_tickets WHERE user_id=? AND is_active ORDER BY updated_at_millis DESC,id DESC LIMIT 100",user,map=::ticketRow)
    fun messages(user: UUID, id: UUID, agent: Boolean, before: Long? = null): SupportMessagePage {
        val ticket = authorizedTicket(user, id, agent)
        if (before != null && before <= 0L) supportFail("support.invalid", 400)
        val result = if (before == null) rows("SELECT * FROM support_messages WHERE ticket_id=? AND is_active ORDER BY sequence DESC LIMIT 101", id, map = ::messageRow)
            else rows("SELECT * FROM support_messages WHERE ticket_id=? AND is_active AND sequence<? ORDER BY sequence DESC LIMIT 101", id, before, map = ::messageRow)
        val visible = result.take(100)
        return SupportMessagePage(ticket, visible.reversed(), visible.lastOrNull()?.sequence.takeIf { result.size>100 })
    }
    private fun cleanBody(text: String): String = text.trim().also {
        if (it.isEmpty() || it.length>4000 || '\u0000' in it) supportFail("support.invalid", 400)
    }
    private fun cleanMeta(meta: Map<String,String>) = meta.filterKeys { it in setOf("clientLanguage","clientPlatform") }.mapValues {
        if ('\u0000' in it.value) supportFail("support.invalid",400)
        it.value.take(64).let { text -> if(text.lastOrNull()?.isHighSurrogate()==true) text.dropLast(1) else text }
    }.toSortedMap()
    private fun command(user: UUID, raw: String?, hash: String): Pair<String, Pair<UUID,UUID?>?> {
        val key = raw?.takeIf { it.matches(Regex("[A-Za-z0-9_:-]{8,128}")) } ?: supportFail("support.command_required", 400)
        rows("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", "support-command:$user:$key") { true }
        val previous = rows("SELECT ticket_id,message_id,request_hash FROM support_commands WHERE actor_user_id=? AND command_id=?", user,key) {
            if (it.getString("request_hash") != hash) supportFail("support.command_conflict")
            requireNotNull(it.uuid("ticket_id")) to it.uuid("message_id")
        }.singleOrNull()
        return key to previous
    }
    private fun recordCommand(user: UUID, key: String, hash: String, ticket: UUID, message: UUID?) {
        execute("INSERT INTO support_commands(actor_user_id,command_id,request_hash,ticket_id,message_id,created_at_millis) VALUES(?,?,?,?,?,?)",user,key,hash,ticket,message,now)
    }
    private fun addEvent(ticket: SupportTicketDataModel, user: UUID, employment: String?, type: String, message: UUID? = null,
        nextAssignee: String? = ticket.assignedAgentUserId, nextStatus: String = ticket.status) {
        val event = rows("""INSERT INTO support_ticket_events(ticket_id,actor_user_id,employment_id,event_type,message_id,
            previous_assignee_user_id,next_assignee_user_id,previous_status,next_status,occurred_at_millis)
            VALUES(?,?,?,?,?,?,?,?,?,?) RETURNING id""", supportUuid(ticket.id),user,employment?.let(::supportUuid),type,message,
            ticket.assignedAgentUserId?.let(::supportUuid),nextAssignee?.let(::supportUuid),ticket.status,nextStatus,now) { it.getLong(1) }.single()
        if (employment != null) execute("INSERT INTO company_employee_activity(employment_id,actor_user_id,metric_key,source_id,occurred_at_millis) VALUES(?,?,?,?,?) ON CONFLICT DO NOTHING",
            supportUuid(employment),user,type,event.toString(),now)
    }
    private fun insertMessage(ticket: UUID, customer: UUID, user: UUID, body: String, role: String, key: String, meta: Map<String,String>, attachments: List<String>): SupportMessageDataModel {
        val display = rows("SELECT first_name,last_name FROM users WHERE id=? AND is_active",user) { listOf(it.getString(1),it.getString(2)).filterNotNull().filter(String::isNotBlank).joinToString(" ") }
            .singleOrNull() ?: supportFail("support.denied",403)
        return rows("""INSERT INTO support_messages(id,ticket_id,user_id,sender_user_id,sender_role,sender_display_name,
            body,attachments,meta,client_message_id,created_at_millis,read_by_customer_at_millis,read_by_agent_at_millis)
            VALUES(?,?,?,?,?,?,?,?::jsonb,?::jsonb,?,?,?,?) RETURNING *""",
            UUID.randomUUID(),ticket,customer,user,role,display,body,jsonBase.encodeToString(attachments),jsonBase.encodeToString(meta),
            "v2:$user:$key",now,now.takeIf { role=="customer" },now.takeIf { role=="agent" },map = ::messageRow).single()
    }
    fun create(user: UUID, request: SupportTicketCreateRequestDataModel, storeAllowed: (UUID) -> Boolean = { false }): SupportTicketDataModel {
        val body = cleanBody(request.initialMessage)
        val category = request.category.takeIf { it in setOf("general","billing","technical","operations","account") } ?: "general"
        if ('\u0000' in request.subject) supportFail("support.invalid",400)
        val subject = request.subject.trim().ifBlank { body }.take(96)
            .let { if(it.lastOrNull()?.isHighSurrogate()==true) it.dropLast(1) else it }
        val store = request.storeId?.let(::supportUuid)
        val hash = supportHash("create",subject,body,category,store?.toString(),jsonBase.encodeToString(cleanMeta(request.metadata)))
        val (key,previous) = command(user,request.clientMessageId,hash)
        if (previous != null) return authorizedTicket(user,previous.first,false)
        if(store!=null && !storeAllowed(store)) supportFail("support.denied",403)
        val id = UUID.randomUUID()
        execute("""INSERT INTO support_tickets(id,public_id,user_id,store_id,subject,category,priority,status,
            last_message,last_message_at_millis,last_customer_message_at_millis,unread_for_agent_count,meta,created_at_millis,updated_at_millis)
            VALUES(?,?,?,?,?,?,'normal','open',?,?,?,1,?::jsonb,?,?)""",
            id,"S-${id.toString().replace("-", "").uppercase()}",user,store,subject,category,body.take(500),now,now,
            jsonBase.encodeToString(cleanMeta(request.metadata)),now,now)
        val message = insertMessage(id,user,user,body,"customer",key,cleanMeta(request.metadata),emptyList())
        recordCommand(user,key,hash,id,supportUuid(message.id))
        return findTicket(id).also { addEvent(it,user,null,"created",supportUuid(message.id)) }
    }
    fun send(user: UUID, agent: Boolean, request: SupportMessageSendRequestDataModel): SupportMessageDataModel {
        // Check current employment before returning even an idempotent replay to an ex-employee.
        val access = if (agent) requireCapability(user,CompanyCapability.SUPPORT_REPLY) else null
        val id = supportUuid(request.ticketId); val body = cleanBody(request.body)
        if (request.attachments.size>5 || request.attachments.any { it.length>1024 || '\u0000' in it }) supportFail("support.invalid",400)
        val meta = cleanMeta(request.metadata)
        val hash = supportHash("send",agent.toString(),id.toString(),body,jsonBase.encodeToString(meta),jsonBase.encodeToString(request.attachments))
        val (key,previous) = command(user,request.clientMessageId,hash)
        val ticket = authorizedTicket(user,id,agent,lock=true)
        if (previous != null) {
            if (previous.first!=id) supportFail("support.command_conflict")
            return rows("SELECT * FROM support_messages WHERE id=?",previous.second,map = ::messageRow).single()
        }
        if (!supportCanReply(ticket,user.toString(),agent,access?.capabilities.orEmpty())) supportFail("support.reply_unavailable")
        val message = insertMessage(id,supportUuid(ticket.userId),user,body,if(agent) "agent" else "customer",key,meta,request.attachments)
        val roleTime = if(agent) "last_agent_message_at_millis" else "last_customer_message_at_millis"
        val unread = if(agent) "unread_for_user_count" else "unread_for_agent_count"
        execute("UPDATE support_tickets SET last_message=?,last_message_at_millis=?,${roleTime}=?,${unread}=${unread}+1,updated_at_millis=?,revision=revision+1 WHERE id=?",
            body.take(500),now,now,now,id)
        addEvent(ticket,user,access?.employmentId,if(agent) "reply" else "customer_message",supportUuid(message.id))
        if (agent && ticket.lastAgentMessageAtMillis==null) execute("""INSERT INTO company_employee_activity
            (employment_id,actor_user_id,metric_key,source_id,value,occurred_at_millis) VALUES(?,?,'first_response_millis',?,?,?) ON CONFLICT DO NOTHING""",
            supportUuid(requireNotNull(access?.employmentId)),user,ticket.id,(now-ticket.createdAtMillis).coerceAtLeast(0L),now)
        recordCommand(user,key,hash,id,supportUuid(message.id)); return message
    }
    fun action(user: UUID, agent: Boolean, request: SupportAgentActionRequest): SupportTicketDataModel {
        val capability = when(request.action) {
            "claim","release" -> CompanyCapability.SUPPORT_CLAIM
            "close","reopen" -> CompanyCapability.SUPPORT_RESOLVE
            else -> supportFail("support.invalid",400)
        }
        val access = if(agent) requireCapability(user,capability) else null
        if (!agent && request.action !in setOf("close","reopen")) supportFail("support.denied",403)
        val ticket = authorizedTicket(user,supportUuid(request.ticketId),agent,lock=true)
        if(request.expectedRevision>=0 && request.expectedRevision!=ticket.revision) supportFail("support.changed")
        if(agent && ticket.userId==user.toString()) supportFail("support.own_ticket",403)
        val mine = ticket.assignedAgentUserId==user.toString()
        val manage = CompanyCapability.SUPPORT_MANAGE in access?.capabilities.orEmpty()
        var assignee = ticket.assignedAgentUserId; var status=ticket.status
        when(request.action) {
            "claim" -> {
                if(ticket.status=="closed") supportFail("support.reply_unavailable")
                if(assignee!=null && !mine && !manage && canAgent(supportUuid(assignee))) supportFail("support.claimed")
                assignee=user.toString()
            }
            "release" -> { if(!mine && !manage) supportFail("support.denied",403); assignee=null }
            "close","reopen" -> {
                if(agent && !mine && !manage) supportFail("support.denied",403)
                status=if(request.action=="close") "closed" else "open"
            }
        }
        if(status==ticket.status && assignee==ticket.assignedAgentUserId) return ticket
        execute("UPDATE support_tickets SET assigned_agent_user_id=?,status=?,closed_at_millis=?,updated_at_millis=?,revision=revision+1 WHERE id=?",
            assignee?.let(::supportUuid),status,now.takeIf { status=="closed" },now,supportUuid(ticket.id))
        addEvent(ticket,user,access?.employmentId,request.action,nextAssignee=assignee,nextStatus=status)
        return findTicket(supportUuid(ticket.id))
    }
    fun markRead(user: UUID, agent: Boolean, request: SupportReadCursorRequest): Pair<SupportTicketDataModel,Boolean> {
        if(request.throughSequence<=0L) supportFail("support.invalid",400)
        val ticket = authorizedTicket(user,supportUuid(request.ticketId),agent,lock=true)
        if(agent && ticket.assignedAgentUserId!=user.toString()) supportFail("support.denied",403)
        val column = if(agent) "read_by_agent_at_millis" else "read_by_customer_at_millis"
        val role = if(agent) "sender_role='customer'" else "sender_role<>'customer'"
        val changed = execute("UPDATE support_messages SET ${column}=? WHERE ticket_id=? AND is_active AND ${role} AND ${column} IS NULL AND sequence<=?",
            now,supportUuid(ticket.id),request.throughSequence)>0
        if(changed) execute("UPDATE support_tickets SET ${if(agent) "unread_for_agent_count" else "unread_for_user_count"}=(SELECT count(*) FROM support_messages WHERE ticket_id=? AND is_active AND ${role} AND ${column} IS NULL) WHERE id=?",
            supportUuid(ticket.id),supportUuid(ticket.id))
        return ticket to changed
    }
    fun metrics(user: UUID): SupportTeamMetrics {
        requireCapability(user,CompanyCapability.SUPPORT_METRICS)
        fun count(sql: String,vararg args: Any?)=rows(sql,*args) { it.getLong(1) }.single()
        val cutoff=now-30L*24*60*60*1000
        val response = rows("SELECT count(*),avg(value) FROM company_employee_activity WHERE metric_key='first_response_millis' AND occurred_at_millis>=?",cutoff) {
            it.getLong(1) to it.getBigDecimal(2)?.toLong()
        }.single()
        return SupportTeamMetrics(count("SELECT count(*) FROM support_tickets WHERE is_active AND status<>'closed'"),
            count("SELECT count(*) FROM support_tickets WHERE is_active AND status<>'closed' AND (assigned_agent_user_id IS NULL OR assigned_agent_user_id NOT IN (SELECT user_id FROM company_support_agent_access))"),
            count("SELECT count(*) FROM support_tickets WHERE is_active AND status<>'closed' AND assigned_agent_user_id=?",user),
            count("SELECT count(*) FROM company_employee_activity WHERE metric_key='reply' AND occurred_at_millis>=?",cutoff),
            count("SELECT count(*) FROM company_employee_activity WHERE metric_key='close' AND occurred_at_millis>=?",cutoff),response.first,response.second)
    }
}
