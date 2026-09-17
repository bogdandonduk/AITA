package kz.aita.server.profile

import kz.aita.*
import kz.aita.server.*
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Base64
import java.util.UUID

internal data class StorePersonGrant(val identity:Boolean,val analytics:Boolean)
/** Mode is a presentation boundary, NOT a substitute for an actual shared-store authorization. */
internal fun storePersonGrant(access:Boolean,currentColleague:Boolean,historicalActor:Boolean,self:Boolean,manager:Boolean)=
    StorePersonGrant(access && (currentColleague || historicalActor && (self || manager)),
        access && (currentColleague || historicalActor) && (self || manager))
internal interface StorePeopleRepository {
    suspend fun read(viewer:UUID,store:UUID,person:UUID,days:Int):StorePersonProfile?
}
internal class DatabaseStorePeople(private val database:Database?=null):StorePeopleRepository {
    override suspend fun read(viewer:UUID,store:UUID,person:UUID,days:Int):StorePersonProfile? =
        newSuspendedTransaction(Dispatchers.IO,db=database,transactionIsolation=java.sql.Connection.TRANSACTION_REPEATABLE_READ) {
            require(days in setOf(7,30,90))
            exec("SET LOCAL statement_timeout = '5000ms'")
            // Deny before querying the subject. No cross-company directory, and no personal contact/security fields.
            if(!userHasStoreAccessInsideTransaction(viewer,store))return@newSuspendedTransaction null
            val place=Stores.select(Stores.name).where {(Stores.id eq store) and (Stores.isActive eq true)}.singleOrNull()
                ?: return@newSuspendedTransaction null
            val root=storePeopleRootInsideTransaction(store) ?: return@newSuspendedTransaction null
            val manager=storePeopleCanAnalyzeInsideTransaction(viewer,store)
            val current=userHasStoreAccessInsideTransaction(person,store)
            val history=if(current)false else
                !Transactions.select(Transactions.id).where {(Transactions.storeId eq store) and (Transactions.userId eq person)}.limit(1).empty() ||
                !OperationLogs.select(OperationLogs.id).where {(OperationLogs.storeId eq store) and (OperationLogs.actorUserId eq person)}.limit(1).empty()
            val grant=storePersonGrant(true,current,history,viewer==person,manager)
            if(!grant.identity)return@newSuspendedTransaction null
            val user=Users.select(Users.firstName,Users.lastName,Users.isActive).where {Users.id eq person}.singleOrNull()
                ?: return@newSuspendedTransaction null
            val memberships=StoreWorkerMemberships.select(StoreWorkerMemberships.jobTitle,StoreWorkerMemberships.jobTitleLocalized,
                StoreWorkerMemberships.acceptedAtMillis,StoreWorkerMemberships.isActive,StoreWorkerMemberships.storeId)
                .where {(StoreWorkerMemberships.userId eq person) and (StoreWorkerMemberships.storeId inList listOf(store,root))}
                .orderBy(StoreWorkerMemberships.isActive to SortOrder.DESC,StoreWorkerMemberships.acceptedAtMillis to SortOrder.DESC).toList()
            val member=memberships.firstOrNull {it[StoreWorkerMemberships.isActive] && it[StoreWorkerMemberships.storeId]==store}
                ?: memberships.firstOrNull {it[StoreWorkerMemberships.isActive]} ?: memberships.firstOrNull()
            val owner=storePeopleIsOwnerInsideTransaction(person,store)
            val image=ModeProfilePhotos.selectAll().where {(ModeProfilePhotos.userId eq person) and (ModeProfilePhotos.mode eq "STORE")}.singleOrNull()
            val photo=image?.let {ProfilePhotoSnapshot(person.toString(),it[ModeProfilePhotos.revision],
                it[ModeProfilePhotos.jpeg]?.let {bytes->Base64.getEncoder().encodeToString(bytes)},it[ModeProfilePhotos.updatedAt],ProfilePhotoMode.STORE)}
            val now=System.currentTimeMillis();val from=(now-days.toLong()*86_400_000).coerceAtLeast(0)
            val stats=if(grant.analytics) statistics(store,person,from,now) else null
            StorePersonProfile(viewer.toString(),store.toString(),person.toString(),
                listOf(user[Users.firstName],user[Users.lastName]).filter {it.isNotBlank()}.joinToString(" ").take(512),
                place[Stores.name],if(!current || !user[Users.isActive])"FORMER" else if(owner)"OWNER" else "EMPLOYEE",
                member?.get(StoreWorkerMemberships.jobTitleLocalized)?.takeIf {it.isNotEmpty()}
                    ?: member?.get(StoreWorkerMemberships.jobTitle)?.takeIf {it.isNotBlank()}?.let {listOf(LocalizedStringDataModel("en",it))}.orEmpty(),
                member?.get(StoreWorkerMemberships.acceptedAtMillis)?.takeIf {it>0}, photo,stats,now)
        }

    private fun org.jetbrains.exposed.sql.Transaction.statistics(store:UUID,person:UUID,from:Long,until:Long):StorePersonStatistics {
        // IDs have been parsed as UUIDs and boundaries are server-generated longs. No arbitrary SQL input.
        val scope="store_id = '$store'::uuid AND user_id = '$person'::uuid AND time_millis >= $from AND time_millis < $until"
        var sales=0L;var returns=0L;var supplies=0L;var active=0L;var last:Long?=null;var operations=0L;var incomplete=0L
        exec("""SELECT count(*) FILTER (WHERE type IN ('purchase','sale')), count(*) FILTER (WHERE type='return'),
            count(*) FILTER (WHERE type IN ('supply','accept')), count(DISTINCT time_millis / 86400000), max(time_millis)
            FROM transactions WHERE $scope""") {rs->if(rs.next()){
            sales=rs.getLong(1);returns=rs.getLong(2);supplies=rs.getLong(3);active=rs.getLong(4)
            last=rs.getLong(5).takeUnless {rs.wasNull()}
        }}
        exec("SELECT count(*), max(created_at_millis) FROM operation_logs WHERE store_id='$store'::uuid AND actor_user_id='$person'::uuid AND created_at_millis >= $from AND created_at_millis < $until") {rs->
            if(rs.next()){operations=rs.getLong(1);val t=rs.getLong(2).takeUnless {rs.wasNull()};last=listOfNotNull(last,t).maxOrNull()}
        }
        val totals=mutableListOf<StorePersonCurrencyTotals>()
        exec("""WITH line_totals AS (
            SELECT t.id,t.type,
              CASE WHEN upper(trim(item->>'currencyCode')) ~ '^[A-Z0-9]{3,12}$'
                THEN upper(trim(item->>'currencyCode')) ELSE '?' END AS currency,
              CASE WHEN jsonb_typeof(item->'quantity')='number' AND jsonb_typeof(item->'pricePerUnit')='number'
                THEN CASE WHEN (item->>'quantity')::numeric >= 0 AND (item->>'pricePerUnit')::numeric >= 0
                  THEN round((item->>'quantity')::numeric * (item->>'pricePerUnit')::numeric,2) END ELSE NULL END AS amount
            FROM transactions t CROSS JOIN LATERAL jsonb_array_elements(CASE WHEN jsonb_typeof(t.goods_in_transaction)='array' THEN t.goods_in_transaction ELSE '[]'::jsonb END) item
            WHERE $scope AND t.type IN ('purchase','sale','return','supply','accept')
          ) SELECT currency,coalesce(sum(amount) FILTER (WHERE type IN ('purchase','sale')),0),
            coalesce(sum(amount) FILTER (WHERE type='return'),0),
            coalesce(sum(amount) FILTER (WHERE type IN ('supply','accept')),0),
            count(DISTINCT id) FILTER (WHERE type IN ('purchase','sale') AND amount IS NOT NULL), count(*) FILTER (WHERE amount IS NULL) FROM line_totals GROUP BY currency ORDER BY currency LIMIT 101""", explicitStatementType=org.jetbrains.exposed.sql.statements.StatementType.SELECT) {rs->
            while(rs.next()){
                val a=rs.getBigDecimal(2);val b=rs.getBigDecimal(3);val c=rs.getBigDecimal(4);val n=rs.getLong(5);incomplete+=rs.getLong(6)
                fun money(v:BigDecimal)=v.setScale(2,RoundingMode.HALF_UP).toPlainString().also {check(it.length<=64)}
                totals+=StorePersonCurrencyTotals(rs.getString(1),money(a),money(b),money(c),money(a-b),
                    money(if(n>0)a.divide(BigDecimal.valueOf(n),2,RoundingMode.HALF_UP) else BigDecimal.ZERO))
            }
        }
        check(totals.size<=100)
        val days=mutableListOf<StorePersonDay>()
        exec("""SELECT to_char(to_timestamp(time_millis / 1000.0) AT TIME ZONE 'UTC','YYYY-MM-DD'),count(*)
            FROM transactions WHERE $scope GROUP BY 1 ORDER BY 1""") {rs->while(rs.next())days+=StorePersonDay(rs.getString(1),rs.getLong(2))}
        return StorePersonStatistics(from,until,sales,returns,supplies,active,operations,last,totals,days,incomplete)
    }
}

/** An invalidation contains no picture or personal details, and the bus applies its existing store ACL. */
internal suspend fun publishStorePersonChanged(person:UUID) {
    val stores=newSuspendedTransaction(Dispatchers.IO) {
        val member=StoreWorkerMemberships.select(StoreWorkerMemberships.storeId).where {
            (StoreWorkerMemberships.userId eq person) and (StoreWorkerMemberships.isActive eq true)
        }.map {it[StoreWorkerMemberships.storeId]}
        val owned=Stores.select(Stores.id,Stores.ownerUserIds).where {Stores.isActive eq true}
            .filter {person.toString() in it[Stores.ownerUserIds]}.map {it[Stores.id]}
        val legacy=StoreUsers.select(StoreUsers.storeId).where {StoreUsers.userId eq person}.map {it[StoreUsers.storeId]}
        (member+owned+legacy).distinct()
    }
    stores.forEach {RealtimeServerBus.publish(entity="store-people",storeId=it.toString())}
}
