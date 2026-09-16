package kz.aita

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Public-content invalidation only; never holds account data or pushes unverified documents. */
object PublicContentSignals {
    private val counter=MutableStateFlow(0L)
    val revision=counter.asStateFlow()
    fun changed(){counter.update {if(it==Long.MAX_VALUE)0L else it+1L}}
}
