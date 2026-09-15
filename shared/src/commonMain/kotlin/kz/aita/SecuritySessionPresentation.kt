package kz.aita

/** Public session history is a device timeline, not a dump of authentication audit payloads.
 * Keep original audit data in PostgreSQL; only explicitly safe presentation fields leave it.
 * Old cached history is filtered by the client using this same allow-list.
 */
fun securitySessionDisplayMetadata(metadata: Map<String, String>): Map<String, String> {
    val language = metadata["localeLanguage"]?.trim().orEmpty()
    return if (Regex("[A-Za-z]{2,3}([-_][A-Za-z0-9]{2,8}){0,2}").matches(language)) {
        mapOf("localeLanguage" to language)
    } else emptyMap()
}
