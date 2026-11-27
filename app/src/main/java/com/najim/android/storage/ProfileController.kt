package com.najim.android.storage

import com.najim.android.network.DiscoveryState
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean

internal enum class ProfileNotice { NONE, CREATED, SAVED, FULL, UNAVAILABLE, INVALID }

internal class ProfileState(
    val profiles: List<NetworkProfile> = emptyList(),
    val ready: Boolean = false,
    val busy: Boolean = false,
    val notice: ProfileNotice = ProfileNotice.NONE
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
    val busy: Boolean get() = token != null

    fun accepts(profile: NetworkProfile): Boolean =
        !disposed && !busy && state.ready && state.profiles.any { it === profile }

    fun refresh(): Boolean = launch { readCatalog(ProfileNotice.NONE) }

    fun create(network: DiscoveryState, name: String): Boolean {
        if (disposed || busy || !state.ready || network !is DiscoveryState.Ready) return false
        val profile = try { NetworkProfile(UUID.randomUUID(), name.trim(), network.gateway) }
        catch (_: IllegalArgumentException) {
            set(ProfileState(state.profiles, true, notice = ProfileNotice.INVALID))
            return false
        }
        return launch {
            val result = vault.createProfile(profile)
            readCatalog(notice(result, ProfileNotice.CREATED))
        }
    }

    fun save(save: ConfirmedVoucherSave): Boolean {
        if (disposed || busy || !state.ready || state.profiles.none {
                it.id == save.record.profileId && it.gateway == save.record.gateway && it.name == save.profileName
            }) return false
        return launch {
            val result = vault.saveConfirmed(save)
            readCatalog(notice(result, ProfileNotice.SAVED))
        }
    }

    private fun notice(result: VaultResult<Unit>, success: ProfileNotice): ProfileNotice = when (result) {
        is VaultResult.Ok -> success
        VaultResult.Full -> ProfileNotice.FULL
        VaultResult.Unavailable -> ProfileNotice.UNAVAILABLE
    }

    private fun readCatalog(notice: ProfileNotice): ProfileState = when (val result = vault.profiles()) {
        is VaultResult.Ok -> ProfileState(result.value, true, notice = notice)
        else -> ProfileState(notice = ProfileNotice.UNAVAILABLE)
    }

    private fun launch(work: () -> ProfileState): Boolean {
        if (disposed || busy) return false
        val operation = AtomicBoolean(false)
        token = operation
        set(ProfileState(state.profiles, state.ready, busy = true))
        try {
            worker.execute {
                val next = if (operation.get()) null else try { work() }
                    catch (_: RuntimeException) { ProfileState(notice = ProfileNotice.UNAVAILABLE) }
                dispatch {
                    if (token === operation) {
                        token = null
                        if (!disposed && !operation.get() && next != null) set(next)
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
        token?.set(true)
        token = null
        if (!disposed) set(ProfileState())
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
