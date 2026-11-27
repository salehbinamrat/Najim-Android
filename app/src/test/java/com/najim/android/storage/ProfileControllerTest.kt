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
}
