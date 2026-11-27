package com.najim.android.storage

import com.najim.android.network.DiscoveryState
import com.najim.android.auth.GatewayLoginResult
import com.najim.android.auth.LoginProtocol
import java.util.concurrent.Executor
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import org.junit.Assert.*
import org.junit.Test

class ProfileControllerTest {
    private val wifi = DiscoveryState.Ready(7L, "10.0.0.1")
    private class Fixture {
        val work = ArrayDeque<Runnable>()
        val callbacks = ArrayDeque<() -> Unit>()
        var state = ProfileState()
        var bytes: ByteArray? = null
        var key: SecretKey? = null
        val vault = VoucherVault(object : VaultBackend {
            override fun read() = bytes
            override fun replace(envelope: ByteArray) { bytes = envelope.copyOf() }
        }, object : VaultKeys {
            override fun existing() = key
            override fun create() = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey().also { key = it }
        })
        val controller = ProfileController(Executor { work.addLast(it) }, { callbacks.addLast(it) },
            vault, { state = it })
        fun finish() { work.removeFirst().run(); callbacks.removeFirst().invoke() }
    }
    @Test fun catalogAndCreationRunOnWorker() {
        val f = Fixture()
        assertTrue(f.controller.refresh())
        assertFalse(f.controller.refresh())
        f.finish()
        assertTrue(f.state.ready)
        assertTrue(f.controller.create(wifi, "Network"))
        assertNull(f.bytes)
        f.finish()
        assertEquals(ProfileNotice.CREATED, f.state.notice)
        assertTrue(f.controller.accepts(f.state.profiles.single()))
    }
    @Test fun clearCancelsQueuedCreationAndInvalidatesSelection() {
        val f = Fixture()
        f.controller.refresh()
        f.finish()
        f.controller.create(wifi, "Network")
        f.controller.clear()
        f.finish()
        assertNull(f.bytes)
        assertFalse(f.state.ready)
    }
    @Test fun confirmedSaveWritesOnlyOnWorker() {
        val f = Fixture()
        f.controller.refresh()
        f.finish()
        f.controller.create(wifi, "Network")
        f.finish()
        val profile = f.state.profiles.single()
        val save = requireNotNull(ConfirmedVoucherSave.authorize(profile, wifi, wifi, "0012", null,
            LoginProtocol.HTTPS_PAP, GatewayLoginResult.MATCHING_SESSION, true))
        assertTrue(f.controller.save(save))
        assertTrue((f.vault.list(profile.id) as VaultResult.Ok).value.isEmpty())
        f.finish()
        assertEquals(ProfileNotice.SAVED, f.state.notice)
        assertEquals("0012", (f.vault.list(profile.id) as VaultResult.Ok).value.single().username)
    }
    @Test fun clearCancelsQueuedSaveBeforeWrite() {
        val f = Fixture()
        f.controller.refresh()
        f.finish()
        f.controller.create(wifi, "Network")
        f.finish()
        val profile = f.state.profiles.single()
        val save = requireNotNull(ConfirmedVoucherSave.authorize(profile, wifi, wifi, "0012", null,
            LoginProtocol.HTTPS_PAP, GatewayLoginResult.MATCHING_SESSION, true))
        assertTrue(f.controller.save(save))
        f.controller.clear()
        f.finish()
        assertTrue((f.vault.list(profile.id) as VaultResult.Ok).value.isEmpty())
        assertFalse(f.state.ready)
    }
    private fun savedFixture(): Fixture {
        val f = Fixture()
        f.controller.refresh()
        f.finish()
        f.controller.create(wifi, "Network")
        f.finish()
        val p = f.state.profiles.single()
        val save = requireNotNull(ConfirmedVoucherSave.authorize(p, wifi, wifi, "000123", "secret",
            LoginProtocol.HTTPS_PAP, GatewayLoginResult.MATCHING_SESSION, true))
        f.controller.save(save)
        f.finish()
        return f
    }
    @Test fun catalogExposesOnlyMaskedAccount() {
        val f = savedFixture()
        val card = f.state.cards.single()
        assertEquals("•••• 0123", card.maskedAccount)
        assertEquals(f.state.profiles.single().id, card.profileId)
        assertEquals("SavedVoucher(redacted)", card.toString())
        assertEquals("ProfileState(redacted)", f.state.toString())
    }
    @Test fun explicitLoadOnlyFillsAfterWorker() {
        val f = savedFixture()
        var loaded: VaultRecord? = null
        assertTrue(f.controller.loadCard(wifi, f.state.profiles.single(), f.state.cards.single()) { loaded = it })
        assertNull(loaded)
        f.finish()
        assertEquals("000123", loaded?.username)
        assertEquals("secret", loaded?.password)
        assertEquals(ProfileNotice.LOADED, f.state.notice)
    }
    @Test fun wrongGatewayAndStaleCardCannotLoad() {
        val f = savedFixture()
        val p = f.state.profiles.single()
        val c = f.state.cards.single()
        assertFalse(f.controller.loadCard(wifi.copy(gateway = "10.0.0.2"), p, c) { error("must not fill") })
        val copy = SavedVoucher(c.id, c.profileId, c.maskedAccount, c.protocol)
        assertFalse(f.controller.loadCard(wifi, p, copy) { error("must not fill") })
        assertTrue(f.work.isEmpty())
    }
    @Test fun wrongProfileCannotLoadOrDeleteEvenAtSameGateway() {
        val f = savedFixture()
        assertTrue(f.controller.create(wifi, "Other"))
        f.finish()
        val other = f.state.profiles.single { it.name == "Other" }
        val c = f.state.cards.single()
        assertFalse(f.controller.loadCard(wifi, other, c) { error("must not fill") })
        assertFalse(f.controller.deleteCard(wifi, other, c))
    }
    @Test fun clearSuppressesCompletedButUndeliveredLoad() {
        val f = savedFixture()
        var filled = false
        f.controller.loadCard(wifi, f.state.profiles.single(), f.state.cards.single()) { filled = true }
        f.work.removeFirst().run()
        f.controller.clear()
        f.callbacks.removeFirst().invoke()
        assertFalse(filled)
        assertTrue(f.state.cards.isEmpty())
    }
    @Test fun deletionRemovesOnlyRequestedCard() {
        val f = savedFixture()
        val p = f.state.profiles.single()
        val c = f.state.cards.single()
        assertTrue(f.controller.deleteCard(wifi, p, c))
        assertFalse(f.controller.deleteCard(wifi, p, c))
        f.finish()
        assertEquals(ProfileNotice.DELETED, f.state.notice)
        assertTrue(f.state.cards.isEmpty())
        assertTrue((f.vault.list(p.id) as VaultResult.Ok).value.isEmpty())
        assertEquals(p.id, f.state.profiles.single().id)
    }
    @Test fun clearCancelsQueuedDeletion() {
        val f = savedFixture()
        val p = f.state.profiles.single()
        f.controller.deleteCard(wifi, p, f.state.cards.single())
        f.controller.clear()
        f.finish()
        assertEquals(1, (f.vault.list(p.id) as VaultResult.Ok).value.size)
    }
    @Test fun repeatedReloadsCoalesceIntoOneFollowUp() {
        val f = savedFixture()
        assertTrue(f.controller.refresh())
        repeat(50) { f.controller.reload() }
        assertEquals(1, f.work.size)
        assertFalse(f.state.ready)
        assertTrue(f.state.busy)
        f.finish()
        assertEquals(1, f.work.size)
        f.finish()
        assertTrue(f.state.ready)
        assertFalse(f.state.busy)
        assertEquals(1, f.state.cards.size)
        assertTrue(f.work.isEmpty())
    }

    @Test fun stopCancelsPendingReloadWithoutBackgroundRead() {
        val f = savedFixture()
        f.controller.reload()
        f.controller.reload()
        f.controller.clear()
        f.finish()
        assertTrue(f.work.isEmpty())
        assertFalse(f.state.ready)
        assertFalse(f.state.busy)
    }

    @Test fun reloadAfterCancelledSaveCannotResurrectOldOperation() {
        val f = savedFixture()
        val profile = f.state.profiles.single()
        val save = requireNotNull(ConfirmedVoucherSave.authorize(profile, wifi, wifi, "9999", null,
            LoginProtocol.HTTPS_PAP, GatewayLoginResult.MATCHING_SESSION, true))
        assertTrue(f.controller.save(save))
        f.controller.reload()
        f.finish()
        f.finish()
        assertEquals(1, f.state.cards.size)
        assertEquals(ProfileNotice.NONE, f.state.notice)
    }

    @Test fun cancelledLoadDoesNotDeliverCredentialDuringReload() {
        val f = savedFixture()
        var filled = false
        assertTrue(f.controller.loadCard(wifi, f.state.profiles.single(), f.state.cards.single()) { filled = true })
        f.work.removeFirst().run()
        f.controller.reload()
        f.callbacks.removeFirst().invoke()
        assertFalse(filled)
        f.finish()
        assertTrue(f.state.ready)
    }
}
