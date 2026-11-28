package com.najim.android.network

/** A UI action belongs to the selection and form lifetime that the user actually saw.
 * The generation also invalidates A -> B -> A transitions before recomposition.
 * This is ephemeral authorization, not a persistent or cryptographic network identity.
 */
internal class NetworkActionScope(val network: DiscoveryState, private val generation: Int) {
    fun permits(current: DiscoveryState, currentGeneration: Int, started: Boolean): Boolean =
        started && network is DiscoveryState.Ready &&
            network == current && generation == currentGeneration
}
