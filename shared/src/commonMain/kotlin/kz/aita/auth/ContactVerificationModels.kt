package kz.aita.auth

import kotlinx.serialization.Serializable

/** Channels and purposes are independent. PHONE is reserved, NOT an enabled delivery method. */
@Serializable
enum class AitaContactChannel { EMAIL, PHONE }
@Serializable
enum class AitaContactPurpose { REGISTRATION, ACCOUNT_CONTACT, STORE_CONTACT, SUPPLIER_CONTACT }

/** New entities use new:<draft UUID>; the server never treats a client draft as an entity ID. */
@Serializable
data class AitaContactTarget(
    val purpose: AitaContactPurpose,
    val entityId: String,
    val parentId: String = ""
)

@Serializable
data class AitaContactCodeRequest(
    val target: AitaContactTarget,
    val address: String,
    val channel: AitaContactChannel = AitaContactChannel.EMAIL,
    val locale: String = "en"
)

/** Bearer proof is transient request data, never an account/contact field or a login token. */
@Serializable
data class AitaVerifiedContactProof(val flowId: String, val receipt: String)

@Serializable
data class AitaContactVerificationResult(
    val proof: AitaVerifiedContactProof,
    val target: AitaContactTarget,
    val address: String,
    val channel: AitaContactChannel = AitaContactChannel.EMAIL,
    val expiresAtMillis: Long,
    val serverTimeMillis: Long
)

fun aitaContactDraftTarget(purpose: AitaContactPurpose, entityId: String, draftId: String, parentId: String = "") =
    AitaContactTarget(purpose, entityId.ifBlank { "new:$draftId" }, parentId)

private val contactUuid = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
fun canonicalAitaContactTarget(target: AitaContactTarget): AitaContactTarget? {
    val id = target.entityId.trim().lowercase()
    val parent = target.parentId.trim().lowercase()
    val isNew = id.startsWith("new:") && contactUuid.matches(id.removePrefix("new:"))
    if (!isNew && !contactUuid.matches(id)) return null
    if (parent.isNotEmpty() && !contactUuid.matches(parent)) return null
    if (target.purpose == AitaContactPurpose.REGISTRATION && (!isNew || parent.isNotEmpty())) return null
    if (target.purpose == AitaContactPurpose.ACCOUNT_CONTACT && (isNew || parent.isNotEmpty())) return null
    if (target.purpose == AitaContactPurpose.SUPPLIER_CONTACT && parent.isNotEmpty()) return null
    if (target.purpose == AitaContactPurpose.STORE_CONTACT && !isNew && parent.isNotEmpty()) return null
    return target.copy(entityId = id, parentId = parent)
}

/** Bound list size before normalization; blanks are optional contacts, not verification targets. */
fun canonicalAitaContactEmails(values: List<String>): List<String>? {
    if (values.size > 10) return null
    val emails = mutableListOf<String>()
    for (value in values) {
        if (value.isBlank()) continue
        val address = normalizeAitaEmail(value) ?: return null
        if (address !in emails) emails += address
    }
    return emails
}

/** Legacy unchanged contacts remain usable but are never retrospectively labelled verified. */
fun aitaContactEmailsRequiringProof(proposed: List<String>, previous: List<String>): List<String>? {
    val normalized = canonicalAitaContactEmails(proposed) ?: return null
    val existing = previous.mapNotNull(::normalizeAitaEmail).toSet()
    return normalized.filterNot { it in existing }
}

/** Scope/ownership are checked even when a receipt is otherwise cryptographically valid. */
fun aitaContactProofMatches(
    requestedTarget: AitaContactTarget,
    storedTarget: AitaContactTarget,
    requestedActor: String?,
    storedActor: String?,
    requestedAddress: String,
    storedAddress: String,
    channel: AitaContactChannel,
    now: Long,
    expiresAt: Long,
    consumedAt: Long?
): Boolean = channel == AitaContactChannel.EMAIL &&
    canonicalAitaContactTarget(requestedTarget)?.let { it == canonicalAitaContactTarget(storedTarget) } == true &&
    requestedActor == storedActor && normalizeAitaEmail(requestedAddress)?.let { it == normalizeAitaEmail(storedAddress) } == true &&
    now < expiresAt && consumedAt == null
