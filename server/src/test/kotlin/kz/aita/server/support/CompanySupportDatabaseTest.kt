package kz.aita.server.support

import kz.aita.*
import org.junit.Assume.assumeTrue
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.util.Properties
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.*

/** Opt-in PostgreSQL repository/migration tests. No in-memory authorization substitute.
 * AITA_SUPPORT_TEST_DB_URL must point to a separately provisioned aita_test_* database.
 * Each test creates its own random schema, runs the actual V52 + V103 SQL, then drops only it.
 * This fixture does not exercise Ktor authentication, the entire Flyway chain, or native UI.
 */
class CompanySupportDatabaseTest {
    private fun exec(c: Connection, sql: String) = c.createStatement().use { it.execute(sql) }
    private fun scalar(c: Connection, sql: String): String? = c.createStatement().use { s ->
        s.executeQuery(sql).use { r -> if(r.next()) r.getString(1) else null }
    }
    private fun count(c: Connection, table: String) = requireNotNull(scalar(c,"SELECT count(*) FROM $table")).toLong()
    private fun resource(name: String) = requireNotNull(javaClass.getResourceAsStream("/db/migration/$name"))
        .bufferedReader().use { it.readText() }
    private data class Fixture(val url: String,val properties: Properties,val schema: String,
        val customer: UUID=UUID.randomUUID(),val other: UUID=UUID.randomUUID(),
        val agent: UUID=UUID.randomUUID(),val secondAgent: UUID=UUID.randomUUID(),val supervisor: UUID=UUID.randomUUID(),
        val employment: UUID=UUID.randomUUID(),val secondEmployment: UUID=UUID.randomUUID(),val supervisorEmployment: UUID=UUID.randomUUID())
    private fun fixture(beforeMigration: (Fixture,Connection)->Unit = { _,_-> }, block: (Fixture,Connection)->Unit) {
        val url=System.getenv("AITA_SUPPORT_TEST_DB_URL").orEmpty()
        assumeTrue("Set an isolated AITA_SUPPORT_TEST_DB_URL for real PostgreSQL tests",url.isNotBlank())
        require(url.startsWith("jdbc:postgresql:"))
        val properties=Properties().apply {
            System.getenv("AITA_SUPPORT_TEST_DB_USER")?.let { setProperty("user",it) }
            System.getenv("AITA_SUPPORT_TEST_DB_PASSWORD")?.let { setProperty("password",it) }
            setProperty("connectTimeout","5");setProperty("socketTimeout","20");setProperty("ApplicationName","aita-support-repository-test")
        }
        DriverManager.getConnection(url,properties).use { c ->
            require(scalar(c,"SELECT current_database()")?.startsWith("aita_test_")==true) { "Refusing a non-test database" }
            val f=Fixture(url,properties,"aita_support_"+UUID.randomUUID().toString().replace("-",""))
            exec(c,"CREATE SCHEMA ${f.schema}")
            try {
                exec(c,"SET search_path TO ${f.schema}, public")
                exec(c,"CREATE TABLE users(id UUID PRIMARY KEY,is_active BOOLEAN NOT NULL DEFAULT TRUE,first_name TEXT NOT NULL DEFAULT 'Test',last_name TEXT NOT NULL DEFAULT 'User')")
                exec(c,"INSERT INTO users(id) VALUES ('${f.customer}'),('${f.other}'),('${f.agent}'),('${f.secondAgent}'),('${f.supervisor}')")
                exec(c,resource("V52__add_user_support.sql"))
                beforeMigration(f,c)
                exec(c,resource("V103__company_employment_and_support_workspace.sql"))
                val now=System.currentTimeMillis()-60_000L
                for((user,employment,job) in listOf(Triple(f.agent,f.employment,"support_agent"),Triple(f.secondAgent,f.secondEmployment,"support_agent"),Triple(f.supervisor,f.supervisorEmployment,"support_supervisor"))) {
                    exec(c,"INSERT INTO company_employments(id,user_id,starts_at_millis) VALUES ('$employment','$user',$now)")
                    exec(c,"INSERT INTO company_job_assignments(employment_id,job_id,starts_at_millis) VALUES ('$employment','$job',$now)")
                }
                block(f,c)
            } finally {
                exec(c,"SET search_path TO public");exec(c,"DROP SCHEMA ${f.schema} CASCADE")
            }
        }
    }
    private fun <T> tx(f: Fixture,block: (CompanySupportRepository)->T): T=DriverManager.getConnection(f.url,f.properties).use { c ->
        c.autoCommit=false
        try {
            exec(c,"SET LOCAL search_path TO ${f.schema}, public")
            exec(c,"SET LOCAL statement_timeout='10s'");exec(c,"SET LOCAL lock_timeout='6s'")
            block(CompanySupportRepository(c)).also { c.commit() }
        } catch(failure: Throwable) { c.rollback();throw failure }
    }
    private fun create(f: Fixture,user: UUID=f.customer,key: String=UUID.randomUUID().toString())=tx(f) {
        it.create(user,SupportTicketCreateRequestDataModel("A test question","Please help",clientMessageId=key))
    }
    private fun action(f: Fixture,user: UUID,ticket: SupportTicketDataModel,kind: String="claim",agent: Boolean=true)=tx(f) {
        it.action(user,agent,SupportAgentActionRequest(ticket.id,kind,ticket.revision))
    }
    private fun request(ticket: SupportTicketDataModel,key: String=UUID.randomUUID().toString(),body: String="Reply")=
        SupportMessageSendRequestDataModel(ticket.id,body,clientMessageId=key)
    private fun <T> parallel(first: ()->T,second: ()->T): List<T> {
        val pool=Executors.newFixedThreadPool(2);val start=CountDownLatch(1)
        try {
            val results=listOf(first,second).map { work -> pool.submit(Callable { check(start.await(3,TimeUnit.SECONDS));work() }) }
            start.countDown();return results.map { it.get(15,TimeUnit.SECONDS) }
        } finally { pool.shutdownNow();check(pool.awaitTermination(21,TimeUnit.SECONDS)) }
    }
    private fun rejected(status: Int,block: ()->Unit) { assertEquals(status,assertFailsWith<SupportFailure>(block=block).status) }

    @Test fun ordinaryAccountHasNoCompanyCapabilities()=fixture { f,_ ->
        val access=tx(f) { it.access(f.customer) };assertNull(access.employmentId);assertTrue(access.capabilities.isEmpty())
        rejected(403) { tx(f) { it.tickets(f.customer,true) } }
    }
    @Test fun activeAssignmentGrantsOnlyItsRegisteredCapabilities()=fixture { f,_ ->
        val access=tx(f) { it.access(f.agent) }
        assertEquals(f.employment.toString(),access.employmentId)
        assertEquals(setOf(CompanyCapability.SUPPORT_QUEUE,CompanyCapability.SUPPORT_CLAIM,CompanyCapability.SUPPORT_REPLY,CompanyCapability.SUPPORT_RESOLVE),access.capabilities)
        assertTrue(access.validUntilMillis-access.serverTimeMillis in 1L..45_000L)
    }
    @Test fun suspendedEmploymentImmediatelyDeniesReadsAndReplies()=fixture { f,c ->
        val ticket=action(f,f.agent,create(f));exec(c,"UPDATE company_employments SET status='suspended' WHERE id='${f.employment}'")
        rejected(403) { tx(f) { it.messages(f.agent,UUID.fromString(ticket.id),true) } }
        rejected(403) { tx(f) { it.send(f.agent,true,request(ticket)) } }
    }
    @Test fun endedEmploymentHasNoAuthority()=fixture { f,c ->
        exec(c,"UPDATE company_employments SET status='ended' WHERE id='${f.employment}'")
        assertFalse(tx(f) { it.canAgent(f.agent) })
    }
    @Test fun futureEmploymentDoesNotGrantEarlyAccess()=fixture { f,c ->
        exec(c,"UPDATE company_employments SET starts_at_millis=${System.currentTimeMillis()+60_000} WHERE id='${f.employment}'")
        assertFalse(tx(f) { it.canAgent(f.agent) })
    }
    @Test fun expiredAssignmentDoesNotGrantAccess()=fixture { f,c ->
        exec(c,"UPDATE company_job_assignments SET ends_at_millis=${System.currentTimeMillis()-1000} WHERE employment_id='${f.employment}'")
        assertFalse(tx(f) { it.canAgent(f.agent) })
    }
    @Test fun disabledUserCannotRetainEmploymentAccess()=fixture { f,c ->
        exec(c,"UPDATE users SET is_active=FALSE WHERE id='${f.agent}'")
        assertFalse(tx(f) { it.canAgent(f.agent) })
    }
    @Test fun disabledDepartmentRevokesItsJobs()=fixture { f,c ->
        exec(c,"UPDATE company_departments SET is_active=FALSE WHERE id='support'")
        assertFalse(tx(f) { it.canAgent(f.agent) })
    }
    @Test fun multipleJobsComposeCapabilitiesWithoutAJobTitleShortcut()=fixture { f,c ->
        exec(c,"INSERT INTO company_jobs(id,title) VALUES ('billing_reader','{\"en\":\"Billing reader\"}')")
        exec(c,"INSERT INTO company_job_capabilities VALUES ('billing_reader','support.metrics.read')")
        exec(c,"INSERT INTO company_job_assignments(employment_id,job_id,starts_at_millis) VALUES ('${f.employment}','billing_reader',0)")
        val access=tx(f) { it.access(f.agent) };assertEquals(2,access.jobs.size);assertTrue(CompanyCapability.SUPPORT_METRICS in access.capabilities)
    }
    @Test fun readOnlyAgentCannotClaimOrReply()=fixture { f,c ->
        val ticket=create(f)
        exec(c,"DELETE FROM company_job_capabilities WHERE job_id='support_agent' AND capability_id<>'support.queue.read'")
        assertEquals(ticket.id,tx(f) { it.tickets(f.agent,true).tickets.single().id })
        rejected(403) { action(f,f.agent,ticket) };rejected(403) { tx(f) { it.send(f.agent,true,request(ticket)) } }
    }
    @Test fun customerDoesNotNeedEmploymentOrAStoreSubscription()=fixture { f,_ ->
        val ticket=create(f);assertEquals(ticket.id,tx(f) { it.customerTickets(f.customer).single().id })
        assertEquals("Reply",tx(f) { it.send(f.customer,false,request(ticket)) }.body)
    }
    @Test fun aCustomerCannotReadOrChangeAnotherConversation()=fixture { f,_ ->
        val ticket=create(f)
        rejected(404) { tx(f) { it.messages(f.other,UUID.fromString(ticket.id),false) } }
        rejected(404) { tx(f) { it.send(f.other,false,request(ticket)) } }
        rejected(404) { action(f,f.other,ticket,"close",false) }
    }
    @Test fun agentMustClaimBeforeReplying()=fixture { f,_ ->
        val ticket=create(f);rejected(409) { tx(f) { it.send(f.agent,true,request(ticket)) } }
        val claimed=action(f,f.agent,ticket)
        val reply=tx(f) { it.send(f.agent,true,request(claimed)) };assertEquals("agent",reply.senderRole);assertEquals("Test User",reply.senderDisplayName)
    }
    @Test fun twoAgentsCannotSimultaneouslyClaimTheSameRevision()=fixture { f,_ ->
        val ticket=create(f)
        val results=parallel({ runCatching { action(f,f.agent,ticket) } },{ runCatching { action(f,f.secondAgent,ticket) } })
        assertEquals(1,results.count { it.isSuccess });assertTrue(results.single { it.isFailure }.exceptionOrNull() is SupportFailure)
    }
    @Test fun anotherAgentCannotTakeAnActiveAssigneesTicket()=fixture { f,_ ->
        val ticket=action(f,f.agent,create(f));rejected(409) { action(f,f.secondAgent,ticket) }
        rejected(409) { tx(f) { it.send(f.secondAgent,true,request(ticket)) } }
    }
    @Test fun supervisorCanReassignButStillClaimsBeforeReplying()=fixture { f,_ ->
        val ticket=action(f,f.agent,create(f));rejected(409) { tx(f) { it.send(f.supervisor,true,request(ticket)) } }
        val reassigned=action(f,f.supervisor,ticket);assertEquals(f.supervisor.toString(),reassigned.assignedAgentUserId)
        assertEquals(f.supervisor.toString(),tx(f) { it.send(f.supervisor,true,request(reassigned)) }.senderUserId)
    }
    @Test fun revokedAssigneesWorkBecomesReclaimable()=fixture { f,c ->
        val ticket=action(f,f.agent,create(f));exec(c,"UPDATE company_employments SET status='suspended' WHERE id='${f.employment}'")
        assertEquals(ticket.id,tx(f) { it.tickets(f.secondAgent,true,"unassigned").tickets.single().id })
        assertEquals(f.secondAgent.toString(),action(f,f.secondAgent,ticket).assignedAgentUserId)
    }
    @Test fun anAgentCannotHandleTheirOwnCustomerRequest()=fixture { f,_ ->
        val ticket=create(f,f.agent);rejected(403) { action(f,f.agent,ticket) }
        assertEquals("Reply",tx(f) { it.send(f.agent,false,request(ticket)) }.body)
    }
    @Test fun lostCreateResponseReplaysTheSameConversation()=fixture { f,c ->
        val key=UUID.randomUUID().toString();val a=create(f,key=key);val b=create(f,key=key)
        assertEquals(a.id,b.id);assertEquals(1L,count(c,"support_tickets"));assertEquals(1L,count(c,"support_messages"))
    }
    @Test fun replayAcknowledgesCreationEvenWhenStoreAccessWasLost()=fixture { f,_ ->
        val request=SupportTicketCreateRequestDataModel("Question","Hello",storeId=UUID.randomUUID().toString(),clientMessageId=UUID.randomUUID().toString())
        val a=tx(f) { it.create(f.customer,request) { true } }
        val b=tx(f) { it.create(f.customer,request) { false } };assertEquals(a.id,b.id)
        rejected(403) { tx(f) { it.create(f.customer,request.copy(clientMessageId=UUID.randomUUID().toString())) { false } } }
    }
    @Test fun simultaneousSendReplayMakesOneMessageAndOneCounterChange()=fixture { f,c ->
        val ticket=create(f);val request=request(ticket)
        val results=parallel({ tx(f) { it.send(f.customer,false,request) } },{ tx(f) { it.send(f.customer,false,request) } })
        assertEquals(results.first().id,results.last().id);assertEquals(2L,count(c,"support_messages"))
        assertEquals(2,tx(f) { it.messages(f.customer,UUID.fromString(ticket.id),false).ticket.unreadForAgentCount })
    }
    @Test fun sameIdentityCannotMeanDifferentMessageContent()=fixture { f,c ->
        val ticket=create(f);val request=request(ticket);tx(f) { it.send(f.customer,false,request) }
        assertEquals("support.command_conflict",assertFailsWith<SupportFailure> { tx(f) { it.send(f.customer,false,request.copy(body="Different")) } }.key)
        assertEquals(2L,count(c,"support_messages"))
    }
    @Test fun commandIdentitiesAreScopedToTheirActor()=fixture { f,c ->
        val key=UUID.randomUUID().toString();val a=create(f,key=key);val b=create(f,f.other,key)
        assertNotEquals(a.id,b.id);assertEquals(2L,count(c,"support_commands"))
    }
    @Test fun revokedAgentCannotRecoverSensitiveDataThroughAnOldReplay()=fixture { f,c ->
        val ticket=action(f,f.agent,create(f));val request=request(ticket);tx(f) { it.send(f.agent,true,request) }
        exec(c,"UPDATE company_employments SET status='ended' WHERE id='${f.employment}'")
        rejected(403) { tx(f) { it.send(f.agent,true,request) } }
    }
    @Test fun getDoesNotMarkMessagesReadAndExplicitCursorStopsAtVisibleBoundary()=fixture { f,_ ->
        val ticket=action(f,f.agent,create(f));val newer=tx(f) { it.send(f.customer,false,request(ticket)) }
        val page=tx(f) { it.messages(f.agent,UUID.fromString(ticket.id),true) }
        assertTrue(page.messages.all { it.readByAgentAtMillis==null })
        tx(f) { it.markRead(f.agent,true,SupportReadCursorRequest(ticket.id,page.messages.first().sequence)) }
        val after=tx(f) { it.messages(f.agent,UUID.fromString(ticket.id),true) }
        assertNotNull(after.messages.first().readByAgentAtMillis);assertNull(after.messages.last().readByAgentAtMillis)
        assertEquals(newer.id,after.messages.last().id);assertEquals(1,after.ticket.unreadForAgentCount)
    }
    @Test fun browsingAgentCannotPretendTheyReadAnotherAgentsWork()=fixture { f,_ ->
        val ticket=action(f,f.agent,create(f));val sequence=tx(f) { it.messages(f.secondAgent,UUID.fromString(ticket.id),true).messages.last().sequence }
        rejected(403) { tx(f) { it.markRead(f.secondAgent,true,SupportReadCursorRequest(ticket.id,sequence)) } }
    }
    @Test fun customerReadReceiptOnlyTouchesAgentMessages()=fixture { f,_ ->
        val ticket=action(f,f.agent,create(f));val message=tx(f) { it.send(f.agent,true,request(ticket)) }
        assertNull(message.readByCustomerAtMillis)
        tx(f) { it.markRead(f.customer,false,SupportReadCursorRequest(ticket.id,message.sequence)) }
        val page=tx(f) { it.messages(f.customer,UUID.fromString(ticket.id),false) }
        assertEquals(0,page.ticket.unreadForUserCount);assertNotNull(page.messages.last().readByCustomerAtMillis)
    }
    @Test fun closingPreventsNewMessagesButNotAcknowledgementOfAnExistingOne()=fixture { f,_ ->
        val ticket=create(f);val savedRequest=request(ticket);val sent=tx(f) { it.send(f.customer,false,savedRequest) }
        val current=tx(f) { it.messages(f.customer,UUID.fromString(ticket.id),false).ticket }
        val closed=action(f,f.customer,current,"close",false)
        assertEquals(sent.id,tx(f) { it.send(f.customer,false,savedRequest) }.id)
        rejected(409) { tx(f) { it.send(f.customer,false,request(closed)) } }
        action(f,f.customer,closed,"reopen",false);assertEquals("Reply",tx(f) { it.send(f.customer,false,request(closed)) }.body)
    }
    @Test fun staleActionCannotOverwriteANewerReplyRevision()=fixture { f,_ ->
        val ticket=action(f,f.agent,create(f));tx(f) { it.send(f.customer,false,request(ticket)) }
        assertEquals("support.changed",assertFailsWith<SupportFailure> { action(f,f.agent,ticket,"close") }.key)
    }
    @Test fun firstResponseAndReplyMetricsAreNotCountedAgainOnReplay()=fixture { f,c ->
        val ticket=action(f,f.agent,create(f));val request=request(ticket)
        tx(f) { it.send(f.agent,true,request) };tx(f) { it.send(f.agent,true,request) }
        val metrics=tx(f) { it.metrics(f.supervisor) }
        assertEquals(1L,metrics.repliesLast30Days);assertEquals(1L,metrics.firstResponsesLast30Days)
        assertEquals("1",scalar(c,"SELECT count(*) FROM company_employee_activity WHERE metric_key='first_response_millis'"))
        rejected(403) { tx(f) { it.metrics(f.agent) } }
    }
    @Test fun failureAtCommandRecordingRollsBackMessageCountersAndMetrics()=fixture { f,c ->
        val ticket=action(f,f.agent,create(f))
        exec(c,"CREATE FUNCTION fail_support_command() RETURNS TRIGGER LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test failure'; END $$")
        exec(c,"CREATE TRIGGER fail_support_command BEFORE INSERT ON support_commands FOR EACH ROW EXECUTE FUNCTION fail_support_command()")
        assertFailsWith<SQLException> { tx(f) { it.send(f.agent,true,request(ticket)) } }
        assertEquals(1L,count(c,"support_messages"));assertEquals(0L,tx(f) { it.metrics(f.supervisor).repliesLast30Days })
    }
    @Test fun pagedMessagesRetainChronologicalOrderWithoutOverlap()=fixture { f,_ ->
        val ticket=create(f);repeat(104) { tx(f) { repo -> repo.send(f.customer,false,request(ticket,body="Message $it")) } }
        val latest=tx(f) { it.messages(f.customer,UUID.fromString(ticket.id),false) }
        val older=tx(f) { it.messages(f.customer,UUID.fromString(ticket.id),false,requireNotNull(latest.nextBeforeSequence)) }
        assertEquals(100,latest.messages.size);assertEquals(5,older.messages.size);assertNull(older.nextBeforeSequence)
        val all=older.messages+latest.messages;assertEquals(105,all.map { it.id }.toSet().size);assertEquals(all.map { it.sequence }.sorted(),all.map { it.sequence })
    }
    @Test fun pagedInboxHasNoDuplicateCursorBoundary()=fixture { f,_ ->
        repeat(52) { create(f) };val first=tx(f) { it.tickets(f.customer,false,"all") }
        val second=tx(f) { it.tickets(f.customer,false,"all",first.nextBeforeMillis,first.nextBeforeId) }
        assertEquals(50,first.tickets.size);assertEquals(2,second.tickets.size);assertNull(second.nextBeforeId)
        assertEquals(52,(first.tickets+second.tickets).map { it.id }.toSet().size)
    }
    @Test fun employmentChangesHaveActualAuditSnapshotsAndDatabaseActor()=fixture { f,c ->
        exec(c,"UPDATE company_employments SET status='suspended' WHERE id='${f.employment}'")
        assertEquals("active",scalar(c,"SELECT before_snapshot->>'status' FROM company_employment_events WHERE source_table='company_employments' AND operation='UPDATE' ORDER BY id DESC LIMIT 1"))
        assertEquals("suspended",scalar(c,"SELECT after_snapshot->>'status' FROM company_employment_events WHERE source_table='company_employments' AND operation='UPDATE' ORDER BY id DESC LIMIT 1"))
        assertFalse(scalar(c,"SELECT database_actor FROM company_employment_events ORDER BY id DESC LIMIT 1").isNullOrBlank())
    }
    @Test fun migrationOrdersOldMessagesByOriginalTimeRatherThanHeapOrder()=fixture(beforeMigration={ f,c ->
        val ticket=UUID.randomUUID()
        exec(c,"INSERT INTO support_tickets(id,public_id,user_id,subject,created_at_millis,updated_at_millis) VALUES ('$ticket','OLD-HISTORY','${f.customer}','History',0,0)")
        for(time in listOf(3000,1000,2000)) exec(c,"INSERT INTO support_messages(id,ticket_id,user_id,sender_user_id,body,created_at_millis) VALUES ('${UUID.randomUUID()}','$ticket','${f.customer}','${f.customer}','$time',$time)")
    }) { f,c ->
        val ticket=UUID.fromString(requireNotNull(scalar(c,"SELECT id FROM support_tickets")))
        val messages=tx(f) { it.messages(f.customer,ticket,false).messages }
        assertEquals(listOf(1000L,2000L,3000L),messages.map { it.createdAtMillis })
        assertEquals(listOf(1L,2L,3L),messages.map { it.sequence })
        val sent=tx(f) { it.send(f.customer,false,SupportMessageSendRequestDataModel(ticket.toString(),"New",clientMessageId=UUID.randomUUID().toString())) }
        assertEquals(4L,sent.sequence)
    }

}
