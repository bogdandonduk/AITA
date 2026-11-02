package kz.aita

import kotlinx.datetime.*
import kotlin.time.Instant
import kotlinx.serialization.encodeToString
import kotlin.test.*

class RecordDatesTest {
    private val utc = TimeZone.UTC
    private fun ms(value: String) = Instant.parse(value).toEpochMilliseconds()
    private data class Row(val id: String, val created: Long, val modified: Long)
    private fun sorted(rows: List<Row>, options: RecordListOptions) =
        recordDateList(rows, options, { it.created }, { it.modified }, { it.id }, utc).map { it.id }

    @Test fun dateBoundsAreInclusiveInTheViewersTimezoneIncludingDstChanges() {
        val options = RecordListOptions(fromDate = "2026-11-01", throughDate = "01.11.2026")
        val zone = TimeZone.of("America/New_York")
        assertFalse(recordMatchesDate(ms("2026-11-01T03:59:59Z"), 0, options, zone))
        assertTrue(recordMatchesDate(ms("2026-11-01T04:00:00Z"), 0, options, zone))
        assertTrue(recordMatchesDate(ms("2026-11-02T04:59:59Z"), 0, options, zone))
        assertFalse(recordMatchesDate(ms("2026-11-02T05:00:00Z"), 0, options, zone))
    }
    @Test fun modifiedSearchUsesModifiedDateAndUnknownDatesAreNotInvented() {
        val options = RecordListOptions(dateField = "modified", fromDate = "2026-11-02", throughDate = "2026-11-02")
        assertTrue(recordMatchesDate(ms("2020-01-01T00:00:00Z"), ms("2026-11-02T12:00:00Z"), options, utc))
        assertFalse(recordMatchesDate(ms("2026-11-02T12:00:00Z"), 0, options, utc))
        assertTrue(recordMatchesDate(0, 0, RecordListOptions(), utc))
        assertFalse(options.copy(fromDate = "2026-11-03").valid())
        assertFalse(RecordListOptions(fromDate = "31.02.2026").valid())
    }
    @Test fun sortingIsStableAndUnknownDatesStayLastInEitherDirection() {
        val rows = listOf(Row("unknown",0,0), Row("b",10,30), Row("a",10,20), Row("c",20,10))
        assertEquals(listOf("a","b","c","unknown"),sorted(rows,RecordListOptions(sort="created",ascending=true)))
        assertEquals(listOf("c","a","b","unknown"),sorted(rows,RecordListOptions(sort="created")))
        assertEquals(listOf("b","a","c","unknown"),sorted(rows,RecordListOptions(sort="modified")))
        assertEquals(rows.map {it.id},sorted(rows,RecordListOptions()))
    }
    private fun store(id: String, parent: String? = null, created: Long) = StoreDataModel(
        id=id,parentStoreId=parent,userIds=listOf("owner"),storeTypeIds=emptyList(),name=emptyList(),alias=emptyList(),
        description=emptyList(),companyForms=emptyList(),location=LocationDataModel(),phoneNumbers=emptyList(),
        emails=emptyList(),countryLocales=emptyList(),createdAt=created)
    @Test fun matchingBranchesKeepTheirParentContextWithoutAdmittingUnmatchedSiblings() {
        val old=ms("2020-01-01T00:00:00Z");val now=ms("2026-11-02T12:00:00Z")
        val root=store("root",created=old).copy(branches=listOf(store("old","root",old),store("new","root",now)))
        val result=listOf(root).storeDirectoryByDates(RecordListOptions(fromDate="2026-11-02",throughDate="2026-11-02"))
        assertEquals("root",result.single().id)
        assertEquals(listOf("new"),result.single().branches.map {it.id})
        assertEquals(2,root.branches.size)
    }
    @Test fun oldCachedRecordsRemainReadableAndNewDatesSurviveSerialization() {
        val worker = jsonBase.decodeFromString<StoreWorkerDataModel>("""{"id":"job","storeId":"store","userId":"worker","acceptedAtMillis":123}""")
        assertEquals(0L, worker.updatedAtMillis)
        val dated=worker.copy(createdAtMillis=100,updatedAtMillis=200)
        assertEquals(dated,jsonBase.decodeFromString<StoreWorkerDataModel>(jsonBase.encodeToString(dated)))
        val buyer=jsonBase.decodeFromString<StoreBuyer>("""{"id":"b","storeId":"s","name":"Buyer","updatedAtMillis":42}""")
        assertEquals(0L,buyer.createdAtMillis)
        assertEquals(42L,buyer.updatedAtMillis)
    }
    @Test fun workshiftPasswordPolicyRejectsTheTwoCharacterShortcut() {
        assertFalse("5!".checkAsPassword())
        assertFalse("password!".checkAsPassword())
        assertFalse("password1".checkAsPassword())
        assertTrue("example5!".checkAsPassword())
    }
    @Test fun everyDateControlHasAllSupportedTranslations() {
        for (key in listOf("created","registered","modified","unknown","tools","field","from","through","sort","default","ascending","descending","invalid")) {
            val english=recordDateText(key,"en")
            for (language in listOf("ru","kk","ky","tg","uz")) assertNotEquals(english,recordDateText(key,language))
        }
    }
}
