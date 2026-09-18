package kz.aita

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

const val APP_STATE_MAX_BYTES = 262_144
const val APP_STATE_DEVICE_MAX_BYTES = 2_097_152

@Serializable data class AppStateScope(val mode: Int, val store: String? = null) {
    fun valid() = mode in 0..3 && (store == null || Regex("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}").matches(store))
    val key: String get() = "$mode:${store ?: "no-store"}"
}

/** UI drafts only: never credentials, carts, queued transactions or derived catalogues. */
@Serializable data class AppStateDocument(
    val schema: Int = 1,
    val navigation: Map<String, List<String>> = emptyMap(),
    val hosts: Map<String, Map<String, String>> = emptyMap(),
    val drafts: Map<String, String> = emptyMap()
)
@Serializable data class AccountAppState(
    val revision: Long = 0,
    val enabled: Boolean = true,
    val document: AppStateDocument? = null,
    val updatedAtMillis: Long = 0
)
@Serializable data class AppStateWrite(val scope: AppStateScope, val expectedRevision: Long,
    val enabled: Boolean, val document: AppStateDocument? = null)
@Serializable data class AppStateResult(val state: AccountAppState, val conflict: Boolean = false)

fun appStateSafeKey(key: String): Boolean {
    val lower = key.lowercase()
    return key.length in 1..512 && listOf("password", "secret", "token", "auth", "confirmation", "payment",
        "receipt", "closeDebt", "selectedids", "selectedgoods", "sortorder", "subscription", "security", "submit", "delete", "pending", "transaction", "onetimecode", "verificationcode").none { it.lowercase() in lower }
}
fun appStateSafeRoute(route: String): Boolean = route.endsWith("NavigationScreenModelRoute") &&
    appStateSafeKey(route) && "Splash" !in route && "Transaction" !in route && "UserAccount" !in route &&
    "AddEditWorker" !in route && "AddEditStore" !in route

fun AppStateDocument.valid(maxBytes: Int = APP_STATE_MAX_BYTES): Boolean = schema == 1 && navigation.keys.all { it in setOf("main", "stockLeft", "stockRight", "menuLeft", "menuRight") } &&
    navigation.values.all { it.size <= 2 && it.all(::appStateSafeRoute) } &&
    hosts.size <= 64 && hosts.all { (route, fields) -> appStateSafeRoute(route) && fields.size <= 128 &&
        fields.all { (key, value) -> appStateSafeKey(key) && value.length <= 32768 } } &&
    drafts.size <= 256 && drafts.all { (key, value) -> appStateSafeKey(key) && value.length <= 65536 } &&
    jsonBase.encodeToString(this).encodeToByteArray().size <= maxBytes

/** A response may acknowledge our write, but must never replace edits made while it was in flight. */
fun appStateCanRestore(localDirty: Boolean, capturedChange: Long, currentChange: Long) = !localDirty && capturedChange == currentChange

enum class AppStateMerge { SAME, ACKNOWLEDGE, RESTORE, CONFLICT }
fun appStateMerge(local: AccountAppState, remote: AccountAppState, dirty: Boolean, untouched: Boolean, newDevice: Boolean): AppStateMerge = when {
    local.revision == remote.revision -> AppStateMerge.SAME
    local.document == remote.document && local.enabled == remote.enabled -> AppStateMerge.ACKNOWLEDGE
    untouched && (!dirty || newDevice) -> AppStateMerge.RESTORE
    else -> AppStateMerge.CONFLICT
}
