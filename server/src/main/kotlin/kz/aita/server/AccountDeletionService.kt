package kz.aita.server

import kz.aita.*
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.util.UUID

/** Caller holds the user row lock and validates password + configured second factor first.
 * Keep a tombstone rather than CASCADE deleting other people's business history. */
internal fun accountDeletionBlockerInside(userId: UUID): String? {
    if (Stores.selectAll().where { Stores.isActive eq true }.any { userId.toString() in it[Stores.ownerUserIds] }) return "owned_business"
    if (Suppliers.selectAll().where { Suppliers.isActive eq true }.any { row ->
        runCatching { jsonBase.decodeFromString<List<String>>(row[Suppliers.userIds].orEmpty()) }.getOrDefault(emptyList()).contains(userId.toString())
    }) return "owned_business"
    if (Manufacturers.selectAll().where { Manufacturers.isActive eq true }.any { row ->
        runCatching { jsonBase.decodeFromString<List<String>>(row[Manufacturers.userIds].orEmpty()) }.getOrDefault(emptyList()).contains(userId.toString())
    }) return "owned_business"
    if (UserWallets.selectAll().where { UserWallets.userId eq userId }.any { it[UserWallets.balanceMinor] != 0L || it[UserWallets.reservedMinor] != 0L }) return "balance"
    if (UserBalances.selectAll().where { UserBalances.userId eq userId }.any { it[UserBalances.value].toDoubleOrNull()?.let { v -> v != 0.0 } != false }) return "balance"
    if (Workshifts.selectAll().where { (Workshifts.workerUserId eq userId) and (Workshifts.isActive eq true) and Workshifts.endedAtMillis.isNull() }.any()) return "workshift"
    return null
}
internal fun eraseAccountPersonalDataInside(userId: UUID) {
    val tx=TransactionManager.current()
    // UUID is parsed by authentication; table/column identifiers below are fixed program constants.
    val owner="'$userId'::uuid"
    tx.exec("UPDATE users SET is_active=false, deleted_at_millis=(extract(epoch FROM clock_timestamp())*1000)::bigint, " +
        "email='deleted-$userId@deleted.invalid', phone_number='deleted-${userId.toString().replace("-", "").take(24)}', first_name='', last_name='', " +
        "password_hash='', country_locale='', worker_ids=NULL, supplier_ids=NULL, active_store_id=NULL, app_mode_id=NULL WHERE id=$owner")
    // Email updates synchronize primary aliases via trigger: remove aliases AFTER the tombstone update.
    for ((table,column) in listOf(
        "auth_login_emails" to "user_id", "auth_one_time_challenges" to "user_id", "auth_login_challenges" to "user_id",
        "auth_recovery_codes" to "user_id", "auth_security_profiles" to "user_id", "auth_phone_alias_challenges" to "user_id",
        "user_profile_photos" to "user_id", "user_mode_profile_photos" to "user_id", "account_app_states" to "owner_user_id",
        "buyer_saved_offers" to "user_id", "buyer_saved_shops" to "user_id", "buyer_saved_shop_states" to "user_id",
        "buyer_shopping_lists" to "user_id", "user_notifications" to "user_id", "support_tickets" to "user_id",
        "auth_contact_verifications" to "actor_user_id", "security_session_events" to "user_id", "refresh_sessions" to "user_id",
        "store_users" to "user_id")) tx.exec("DELETE FROM $table WHERE $column=$owner")
    tx.exec("DELETE FROM auth_contact_verifications WHERE contact_purpose='REGISTRATION' AND applied_entity_id=$owner")
    tx.exec("UPDATE store_worker_memberships SET is_active=false, workshift_password_hash=NULL WHERE user_id=$owner")
    // Legacy deployments may still have this retired table; new migrated databases do not.
    tx.exec("DO \$\$ BEGIN IF to_regclass('workers') IS NOT NULL THEN UPDATE workers SET is_active=false, first_name='',last_name='', phone_number='deleted-'||left(id::text,24),email='deleted-'||id::text||'@deleted.invalid' WHERE user_id=$owner; END IF; END \$\$")
    // Existing accounting records and immutable audit trails deliberately keep the former actor ID.
}
