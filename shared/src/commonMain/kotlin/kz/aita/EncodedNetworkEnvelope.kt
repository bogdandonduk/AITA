package kz.aita

import kotlinx.serialization.Serializable

/** Required markers avoid mistaking an ordinary JSON payload for an envelope. */
@PublishedApi
@Serializable
internal data class EncodedNetworkEnvelope(
    val message: String?,
    val negative: Boolean,
    val payload: String? = null,
    val transportFailure: Boolean = false
)
