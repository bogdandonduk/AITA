package kz.aita

import io.ktor.http.HttpMethod
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable

@Serializable data class StorePersonProfile(
    val viewerAccountId:String, val storeId:String, val userId:String, val displayName:String,
    val storeName:List<LocalizedStringDataModel> = emptyList(), val role:String="EMPLOYEE",
    val jobTitle:List<LocalizedStringDataModel> = emptyList(), val joinedAtMillis:Long?=null,
    val photo:ProfilePhotoSnapshot?=null, val statistics:StorePersonStatistics?=null, val checkedAtMillis:Long=0
)
@Serializable data class StorePersonStatistics(
    val fromMillis:Long, val untilMillis:Long, val sales:Long=0, val returns:Long=0, val supplies:Long=0,
    val activeDays:Long=0, val recordedOperations:Long=0, val lastActivityMillis:Long?=null,
    val currencies:List<StorePersonCurrencyTotals> = emptyList(), val days:List<StorePersonDay> = emptyList(), val incompletePriceLines:Long=0
)
@Serializable data class StorePersonCurrencyTotals(
    val currency:String, val sales:String, val returns:String, val supplies:String, val netSales:String,
    val averageSale:String
)
@Serializable data class StorePersonDay(val dateUtc:String,val transactions:Long)

fun validStorePersonProfile(value:StorePersonProfile, viewer:String,store:String,person:String):Boolean =
    value.viewerAccountId==viewer && value.storeId==store && value.userId==person &&
        value.displayName.length<=512 && value.storeName.size<=16 && value.jobTitle.size<=16 &&
        (value.storeName+value.jobTitle).all {it.language.length<=16 && it.value.length<=512} &&
        (value.joinedAtMillis==null || value.joinedAtMillis in 1..value.checkedAtMillis) && value.role in setOf("OWNER","EMPLOYEE","FORMER") && value.checkedAtMillis>0 &&
        (value.photo==null || validProfilePhotoSnapshot(value.photo,person,ProfilePhotoMode.STORE)) &&
        value.statistics.let { stats -> stats==null || (stats.fromMillis>=0 && stats.untilMillis>=stats.fromMillis &&
            stats.untilMillis-stats.fromMillis<=90L*86_400_000 && stats.untilMillis<=value.checkedAtMillis &&
            (stats.lastActivityMillis==null || stats.lastActivityMillis in stats.fromMillis until stats.untilMillis) &&
            listOf(stats.sales,stats.returns,stats.supplies,stats.activeDays,stats.recordedOperations,stats.incompletePriceLines).all {it>=0} &&
            stats.currencies.size<=100 && stats.days.size<=91 && stats.days.map {it.dateUtc}.distinct().size==stats.days.size && stats.currencies.map {it.currency}.distinct().size==stats.currencies.size &&
            stats.days.all {it.transactions>=0 && it.dateUtc.length==10 && runCatching {
                kotlinx.datetime.LocalDate.parse(it.dateUtc).toEpochDays().toLong() in (stats.fromMillis/86_400_000)..((stats.untilMillis-1).coerceAtLeast(0)/86_400_000)
            }.getOrDefault(false)} &&
            stats.currencies.all {c-> c.currency.length<=16 && listOf(c.sales,c.returns,c.supplies,c.netSales,c.averageSale)
                .all {it.length<=64 && it.matches(Regex("-?[0-9]+(\\.[0-9]{1,2})?"))}}) }

fun validStorePersonId(value:String):Boolean = value.length==36 && Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}").matches(value)

object StorePeopleClient {
    suspend fun load(store:String,person:String,days:Int,generation:Long):ResponseDataModel<StorePersonProfile> {
        require(validStorePersonId(store) && validStorePersonId(person) && days in setOf(7,30,90))
        return networkRequest<StorePersonProfile,Unit>(HttpMethod.Get,endpointUrl="stores/$store/people/$person",
            query=mapOf("days" to days),expectedSessionGeneration=generation)
    }
}
object StorePeopleSignals {
    private val mutable=MutableStateFlow(0L)
    val revision=mutable.asStateFlow()
    fun changed(){mutable.update {if(it==Long.MAX_VALUE)0L else it+1}}
}
