package kz.aita.server.auth

import kz.aita.auth.*
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table

internal const val AUTH_PURPOSE_CONTACT = "CONTACT_VERIFICATION"
internal const val AUTH_PURPOSE_CONTACT_NOTICE = "ACCOUNT_EMAIL_CHANGE_NOTICE"

internal class AitaContactDeliveryUnavailableException : IllegalStateException("CONTACT_EMAIL_DELIVERY_UNAVAILABLE")

internal class AitaContactVerificationRequiredException : IllegalStateException("CONTACT_EMAIL_CONFIRMATION_REQUIRED")

internal object AuthContactVerifications : Table("auth_contact_verifications") {
    val challengePublicId = uuid("challenge_public_id")
    val actorUserId = uuid("actor_user_id").nullable()
    val channel = varchar("channel", 16)
    val contactPurpose = varchar("contact_purpose", 32)
    val entityId = varchar("entity_id", 80)
    val parentId = varchar("parent_id", 36)
    val address = varchar("address", 254)
    val scopeHash = char("scope_hash", 64)
    val authorizationHash = char("authorization_hash", 64).nullable()
    val receiptHash = char("receipt_hash", 64).nullable()
    val receiptCiphertext = text("receipt_ciphertext").nullable()
    val receiptExpiresAtMillis = long("receipt_expires_at_millis").nullable()
    val appliedEntityId = uuid("applied_entity_id").nullable()
    val appliedAtMillis = long("applied_at_millis").nullable()
    val createdAtMillis = long("created_at_millis")
    override val primaryKey = PrimaryKey(challengePublicId)
}

internal object AuthContactChangeNotices : Table("auth_contact_change_notices") {
    val challengePublicId = uuid("challenge_public_id")
    val userId = uuid("user_id")
    val address = varchar("address", 254)
    val createdAtMillis = long("created_at_millis")
    override val primaryKey = PrimaryKey(challengePublicId)
}

internal fun ResultRow.contactTarget() = AitaContactTarget(
    AitaContactPurpose.valueOf(this[AuthContactVerifications.contactPurpose]),
    this[AuthContactVerifications.entityId], this[AuthContactVerifications.parentId]
)
