package com.najim.android.storage

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** Worker thread only. Single app process, private no-backup directory, no plaintext files. */
internal object AndroidVault {
    fun create(context: Context): VoucherVault {
        val file = AtomicFile(File(context.applicationContext.noBackupFilesDir, "voucher-vault.v1"))
        return VoucherVault(AtomicVaultBackend(file), AndroidVaultKeys())
    }
}

private class AndroidVaultKeys : VaultKeys {
    private val alias = "najim.voucher-vault.v1"
    private fun store() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    override fun existing(): SecretKey? {
        val store = store()
        if (!store.containsAlias(alias)) return null
        return store.getKey(alias, null) as? SecretKey ?: error("Unexpected vault key")
    }

    override fun create(): SecretKey {
        check(existing() == null)
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build())
        }.generateKey()
    }
}

private class AtomicVaultBackend(private val file: AtomicFile) : VaultBackend {
    override fun read(): ByteArray? {
        val stream = try { file.openRead() } catch (failure: FileNotFoundException) {
            // A recoverable legacy backup may exist; never silently replace an inaccessible file.
            val base = file.baseFile
            if (base.exists() || File(base.path + ".bak").exists() || File(base.path + ".new").exists()) throw failure
            return null
        }
        return stream.use {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (true) {
                val count = it.read(buffer)
                if (count == -1) break
                if (count == 0 || count > VaultLimits.MAX_ENVELOPE - output.size()) throw IOException("Invalid vault size")
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
    }

    override fun replace(envelope: ByteArray) {
        require(envelope.size <= VaultLimits.MAX_ENVELOPE)
        val stream = file.startWrite()
        try {
            stream.write(envelope)
            file.finishWrite(stream)
        } catch (failure: Exception) {
            file.failWrite(stream)
            throw failure
        }
        // AtomicFile finishWrite can report failure via logging instead of throwing.
        val committed = read() ?: throw IOException("Vault write unavailable")
        if (!committed.contentEquals(envelope)) throw IOException("Vault write not verified")
    }
}
