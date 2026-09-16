package kz.aita.server.profile

import kz.aita.ProfilePhotoSnapshot
import kz.aita.server.Users
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.Base64
import java.util.UUID

internal data class PhotoWriteResult(val photo:ProfilePhotoSnapshot,val conflict:Boolean=false)
internal interface ProfilePhotoRepository {
    suspend fun read(owner:UUID):ProfilePhotoSnapshot
    suspend fun change(owner:UUID,expected:Long,jpeg:ByteArray?):PhotoWriteResult
}
internal object ProfilePhotos:Table("user_profile_photos") {
    val userId=uuid("user_id").references(Users.id, onDelete=ReferenceOption.CASCADE)
    val revision=long("revision")
    val jpeg=binary("jpeg").nullable()
    val updatedAt=long("updated_at_millis")
    override val primaryKey=PrimaryKey(userId)
}
internal class DatabaseProfilePhotos:ProfilePhotoRepository {
    private fun ResultRow?.photo(owner:UUID)=ProfilePhotoSnapshot(owner.toString(),this?.get(ProfilePhotos.revision) ?: 0,
        this?.get(ProfilePhotos.jpeg)?.let {Base64.getEncoder().encodeToString(it)},this?.get(ProfilePhotos.updatedAt) ?: 0)
    override suspend fun read(owner:UUID)=newSuspendedTransaction(Dispatchers.IO) {
        ProfilePhotos.selectAll().where {ProfilePhotos.userId eq owner}.singleOrNull().photo(owner)
    }
    override suspend fun change(owner:UUID,expected:Long,jpeg:ByteArray?):PhotoWriteResult=newSuspendedTransaction(Dispatchers.IO) {
        // Serialize even the first insert. Lock order is always user -> photo, matching security/profile changes.
        val user=Users.selectAll().where {Users.id eq owner}.forUpdate().singleOrNull()
        if(user==null || !user[Users.isActive])throw PhotoProblem("unavailable")
        val row=ProfilePhotos.selectAll().where {ProfilePhotos.userId eq owner}.singleOrNull()
        val current=row.photo(owner);val previous=row?.get(ProfilePhotos.jpeg)
        val same=if(jpeg==null)previous==null else previous?.contentEquals(jpeg)==true
        // Idempotent retry after a lost response, without applying the old edit over a newer picture.
        if(expected!=current.revision) return@newSuspendedTransaction PhotoWriteResult(current,
            conflict=!photoRetryAcknowledges(expected,current.revision,same))
        if(same)return@newSuspendedTransaction PhotoWriteResult(current)
        if(current.revision==Long.MAX_VALUE)throw PhotoProblem("unavailable")
        val now=System.currentTimeMillis();val next=current.revision+1
        if(row==null)ProfilePhotos.insert {
            it[userId]=owner;it[revision]=next;it[ProfilePhotos.jpeg]=jpeg;it[updatedAt]=now
        } else ProfilePhotos.update({ProfilePhotos.userId eq owner}) {
            it[revision]=next;it[ProfilePhotos.jpeg]=jpeg;it[updatedAt]=now
        }
        PhotoWriteResult(ProfilePhotoSnapshot(owner.toString(),next,jpeg?.let{Base64.getEncoder().encodeToString(it)},now))
    }
}

internal fun photoRetryAcknowledges(expected:Long,current:Long,same:Boolean):Boolean =
    expected>=0 && expected<Long.MAX_VALUE && current==expected+1 && same
