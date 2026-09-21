package kz.aita

/** Browser WebSockets can carry subprotocols, but cannot carry an Authorization header.
 * Credentials stay out of URLs, history and request-path logs. Only the public protocol
 * name is ever echoed by the server; the credential is verified by the normal JWT policy.
 */
const val AITA_REALTIME_PROTOCOL = "aita.realtime.v1"
const val AITA_REALTIME_AUTH_PROTOCOL_PREFIX = "aita.auth."

fun realtimeProtocolAccessToken(protocols: List<String>): String? {
    val offered = protocols.flatMap { it.split(',') }.map { it.trim() }
    if (AITA_REALTIME_PROTOCOL !in offered) return null
    val credentials = offered.filter { it.startsWith(AITA_REALTIME_AUTH_PROTOCOL_PREFIX) }
    if (credentials.size != 1) return null
    return credentials.single().removePrefix(AITA_REALTIME_AUTH_PROTOCOL_PREFIX).takeIf { token ->
        token.length in 16..4096 && token.count { it == '.' } == 2 &&
            token.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it in "-_." }
    }
}
