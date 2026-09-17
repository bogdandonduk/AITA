package kz.aita.server.profile

import kz.aita.*
import kz.aita.server.Users
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.util.Base64
import java.util.UUID

internal object ModeProfilePhotos:Table("user_mode_profile_photos") {
    val userId=uuid("user_id").references(Users.id,onDelete=ReferenceOption.CASCADE)
    val mode=varchar("mode",16)
    val revision=long("revision")
    val jpeg=binary("jpeg").nullable()
    val updatedAt=long("updated_at_millis")
    override val primaryKey=PrimaryKey(userId,mode)
}
internal class DatabaseModeProfilePhotos(private val scope:ProfilePhotoMode,private val database:Database?=null):ProfilePhotoRepository {
    private fun row(owner:UUID)=ModeProfilePhotos.selectAll().where {
        (ModeProfilePhotos.userId eq owner) and (ModeProfilePhotos.mode eq scope.name)
    }.singleOrNull()
    private fun ResultRow?.photo(owner:UUID)=ProfilePhotoSnapshot(owner.toString(),this?.get(ModeProfilePhotos.revision) ?: 0,
        this?.get(ModeProfilePhotos.jpeg)?.let {Base64.getEncoder().encodeToString(it)},this?.get(ModeProfilePhotos.updatedAt) ?: 0,scope)
    override suspend fun read(owner:UUID)=newSuspendedTransaction(Dispatchers.IO,db=database){row(owner).photo(owner)}
    override suspend fun change(owner:UUID,expected:Long,jpeg:ByteArray?):PhotoWriteResult=newSuspendedTransaction(Dispatchers.IO,db=database) {
        val user=Users.selectAll().where {Users.id eq owner}.forUpdate().singleOrNull()
        if(user==null || !user[Users.isActive])throw PhotoProblem("unavailable")
        val previous=row(owner);val current=previous.photo(owner);val bytes=previous?.get(ModeProfilePhotos.jpeg)
        val same=if(jpeg==null)bytes==null else bytes?.contentEquals(jpeg)==true
        if(expected!=current.revision)return@newSuspendedTransaction PhotoWriteResult(current,
            conflict=!photoRetryAcknowledges(expected,current.revision,same))
        if(same)return@newSuspendedTransaction PhotoWriteResult(current)
        if(current.revision==Long.MAX_VALUE)throw PhotoProblem("unavailable")
        val next=current.revision+1;val now=System.currentTimeMillis()
        if(previous==null)ModeProfilePhotos.insert {
            it[userId]=owner;it[mode]=scope.name;it[revision]=next;it[ModeProfilePhotos.jpeg]=jpeg;it[updatedAt]=now
        } else ModeProfilePhotos.update({(ModeProfilePhotos.userId eq owner) and (ModeProfilePhotos.mode eq scope.name)}) {
            it[revision]=next;it[ModeProfilePhotos.jpeg]=jpeg;it[updatedAt]=now
        }
        PhotoWriteResult(ProfilePhotoSnapshot(owner.toString(),next,jpeg?.let{Base64.getEncoder().encodeToString(it)},now,scope))
    }
}
