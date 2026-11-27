package com.najim.android.storage

import com.najim.android.network.DiscoveryState
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

internal enum class ProfileNotice { NONE, CREATED, SAVED, FULL, UNAVAILABLE, INVALID, DELETED, LOADED, NOT_FOUND, RETENTION_CHANGED }

internal class ProfileState(
    val profiles: List<NetworkProfile> = emptyList(),
    val ready: Boolean = false,
    val busy: Boolean = false,
    val notice: ProfileNotice = ProfileNotice.NONE,
    val cards: List<SavedVoucher> = emptyList(),
    val totalRecords: Int = cards.size
) {
    override fun toString() = "ProfileState(redacted)"
}

/** Owner-thread controller. Worker must be serial; no credential or profile persistence on UI thread. */
internal class ProfileController(
    private val worker: Executor,
    private val dispatch: ((() -> Unit) -> Unit),
    private val vault: VoucherVault,
    private val publish: (ProfileState) -> Unit
) {
    private var state = ProfileState()
    private var token: AtomicBoolean? = null
    private var disposed = false
    private var refreshPending = false
    val busy: Boolean get() = token != null

    fun accepts(profile: NetworkProfile): Boolean =
        !disposed && !busy && state.ready && state.profiles.any { it === profile }

    fun refresh(): Boolean = launch { readCatalog(ProfileNotice.NONE) }

    /** Network/lifecycle caller: coalesce repeated invalidations without queuing extra decryptions. */
    fun reload() {
        if (disposed) return
        clear()
        if (busy) refreshPending = true else refresh()
    }

    fun create(network: DiscoveryState, name: String): Boolean {
        if (disposed || busy || !state.ready || network !is DiscoveryState.Ready) return false
        val profile = try { NetworkProfile(UUID.randomUUID(), name.trim(), network.gateway) }
        catch (_: IllegalArgumentException) {
            set(ProfileState(state.profiles, true, notice = ProfileNotice.INVALID, cards = state.cards, totalRecords = state.totalRecords))
            return false
        }
        return launch {
            val result = vault.createProfile(profile)
            readCatalog(notice(result, ProfileNotice.CREATED))
        }
    }

    fun save(save: ConfirmedVoucherSave): Boolean {
        if (disposed || busy || !state.ready || state.profiles.none {
                it.id == save.record.profileId && it.gateway == save.record.gateway && it.name == save.profileName &&
                    it.retention == save.retention
            }) return false
        return launch {
            val result = vault.saveConfirmed(save)
            readCatalog(notice(result, ProfileNotice.SAVED))
        }
    }

    fun setRetention(network: DiscoveryState, profile: NetworkProfile, mode: RetentionMode): Boolean {
        if (!accepts(profile) || network !is DiscoveryState.Ready || network.gateway != profile.gateway) return false
        return launch {
            readCatalog(notice(vault.setRetention(profile, mode), ProfileNotice.RETENTION_CHANGED))
        }
    }

    private fun acceptsCard(network: DiscoveryState, profile: NetworkProfile, card: SavedVoucher): Boolean =
        accepts(profile) && network is DiscoveryState.Ready && network.gateway == profile.gateway &&
            card.profileId == profile.id && state.cards.any { it === card }

    fun loadCard(network: DiscoveryState, profile: NetworkProfile, card: SavedVoucher,
                 fill: (VaultRecord) -> Unit): Boolean {
        if (!acceptsCard(network, profile, card)) return false
        val catalog = state
        var record: VaultRecord? = null
        return launch(onAccepted = {
            record?.let(fill)
            record = null
        }) {
            val result = vault.readSelected(profile, card.id)
            record = (result as? VaultResult.Ok)?.value
            val message = when {
                result !is VaultResult.Ok -> ProfileNotice.UNAVAILABLE
                record == null -> ProfileNotice.NOT_FOUND
                else -> ProfileNotice.LOADED
            }
            if (message == ProfileNotice.LOADED) ProfileState(catalog.profiles, true,
                notice = message, cards = catalog.cards, totalRecords = catalog.totalRecords)
            else readCatalog(message)
        }
    }

    fun deleteCard(network: DiscoveryState, profile: NetworkProfile, card: SavedVoucher): Boolean {
        if (!acceptsCard(network, profile, card)) return false
        return launch {
            val result = vault.remove(profile.id, card.id)
            readCatalog(when (result) {
                is VaultResult.Ok -> if (result.value) ProfileNotice.DELETED else ProfileNotice.NOT_FOUND
                else -> ProfileNotice.UNAVAILABLE
            })
        }
    }

    private fun notice(result: VaultResult<Unit>, success: ProfileNotice): ProfileNotice = when (result) {
        is VaultResult.Ok -> success
        VaultResult.Full -> ProfileNotice.FULL
        VaultResult.Unavailable -> ProfileNotice.UNAVAILABLE
    }

    private fun readCatalog(notice: ProfileNotice): ProfileState = when (val result = vault.catalog()) {
        is VaultResult.Ok -> ProfileState(result.value.profiles, true, notice = notice, cards = result.value.cards, totalRecords = result.value.totalRecords)
        else -> ProfileState(notice = ProfileNotice.UNAVAILABLE)
    }

    private fun launch(onAccepted: () -> Unit = {}, work: () -> ProfileState): Boolean {
        if (disposed || busy) return false
        val operation = AtomicBoolean(false)
        token = operation
        set(ProfileState(state.profiles, state.ready, busy = true, cards = state.cards, totalRecords = state.totalRecords))
        try {
            worker.execute {
                val next = if (operation.get()) null else try { work() }
                    catch (_: RuntimeException) { ProfileState(notice = ProfileNotice.UNAVAILABLE) }
                dispatch {
                    if (token === operation) {
                        token = null
                        if (!disposed) {
                            if (!operation.get() && next != null) {
                                set(next)
                                onAccepted()
                            }
                            if (refreshPending) {
                                refreshPending = false
                                refresh()
                            } else if (operation.get()) set(ProfileState())
                        }
                    }
                }
            }
        } catch (_: RejectedExecutionException) {
            token = null
            set(ProfileState(notice = ProfileNotice.UNAVAILABLE))
            return false
        }
        return true
    }

    /** Queued operations are cancelled; an atomic write already started may finish. */
    fun clear() {
        refreshPending = false
        token?.set(true)
        // Retain worker ownership until completion; a later reload is coalesced.
        if (!disposed) set(ProfileState(busy = busy))
    }

    fun dispose() {
        disposed = true
        clear()
        state = ProfileState()
    }

    private fun set(next: ProfileState) {
        state = next
        publish(next)
    }
}
