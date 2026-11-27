package com.najim.android.storage

import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Authenticated encryption; AndroidKeyStore supplies a fresh randomized IV on encryption. */
internal object VaultCipher {
    private const val IV_BYTES = 12
    private const val TAG_BYTES = 16
    private const val VERSION: Byte = 1
    private val aad = "com.najim.android/voucher-vault/v1".toByteArray(Charsets.UTF_8)

    fun seal(key: SecretKey, plain: ByteArray): ByteArray {
        require(plain.size <= VaultLimits.MAX_PLAINTEXT)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        require(iv.size == IV_BYTES)
        cipher.updateAAD(aad)
        return byteArrayOf(VERSION) + iv + cipher.doFinal(plain)
    }

    fun open(key: SecretKey, envelope: ByteArray): ByteArray {
        require(envelope.size in (1 + IV_BYTES + TAG_BYTES)..VaultLimits.MAX_ENVELOPE)
        require(envelope[0] == VERSION)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, envelope, 1, IV_BYTES))
        cipher.updateAAD(aad)
        return cipher.doFinal(envelope, 1 + IV_BYTES, envelope.size - 1 - IV_BYTES)
            .also { require(it.size <= VaultLimits.MAX_PLAINTEXT) }
    }
}
