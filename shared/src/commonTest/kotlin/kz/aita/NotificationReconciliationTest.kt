package kz.aita

import kotlin.test.*

class NotificationReconciliationTest {
    private fun n(id: String, time: Long=10, saved: Boolean=true) = NotificationDataModel("Message $id",NotificationType.Neutral,
        id=id,userId="user",createdAtMillis=time,isSavedOnServer=saved)
    @Test fun newArrivalsSurviveAnOlderInFlightSnapshot() {
        val old=n("a");val added=n("b",20);assertEquals(listOf(added,old),mergeNotificationSnapshot(listOf(old),listOf(added,old),listOf(old)))
    }
    @Test fun pendingLocalHistorySurvivesRefresh() {
        val local=n("local",saved=false);assertTrue(local in mergeNotificationSnapshot(listOf(local),listOf(local),listOf(n("a"))))
    }
    @Test fun localReadNeverFlashesUnreadAfterRefresh() {
        val before=n("a");val read=before.copy(readAtMillis=20);assertEquals(20L,mergeNotificationSnapshot(listOf(before),listOf(read),listOf(before)).single().readAtMillis)
    }
    @Test fun genuinelyNewEventRevisionIsUnread() {
        val read=n("a").copy(readAtMillis=20);val newer=n("a",30);assertEquals(newer,mergeNotificationSnapshot(listOf(read),listOf(read),listOf(newer)).single())
    }
    @Test fun olderSnapshotDoesNotRevertNewerMessageBody() {
        val old=n("a");val newer=n("a",30).copy(message="Updated");assertEquals(newer,mergeNotificationSnapshot(listOf(old),listOf(newer),listOf(old)).single())
    }
    @Test fun authoritativeRemovalDropsOnlyAnUnchangedSavedRow() {
        val row=n("a");assertTrue(mergeNotificationSnapshot(listOf(row),listOf(row),emptyList()).isEmpty())
    }
    @Test fun readAckCannotEraseConcurrentArrivalsOrAlterUnrelatedRows() {
        val rows=listOf(n("b",20),n("a"));val ack=listOf(n("a").copy(readAtMillis=30));val actual=applyNotificationReadAcknowledgement(rows,setOf("a"),ack)
        assertEquals(rows.first(),actual.first());assertEquals(30L,actual.last().readAtMillis);assertEquals(2,actual.size)
    }
    @Test fun lateReadAckCannotMarkANewerRevisionRead() {
        val newer=n("a",30);assertEquals(listOf(newer),applyNotificationReadAcknowledgement(listOf(newer),setOf("a"),listOf(n("a").copy(readAtMillis=20))))
    }
    @Test fun refreshIsStableUnderEquivalentOrdering() {
        val rows=listOf(n("b",20),n("a"));assertEquals(rows,mergeNotificationSnapshot(rows,rows,rows.reversed()))
    }
}
