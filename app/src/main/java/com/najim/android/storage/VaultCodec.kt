package com.najim.android.storage

import com.najim.android.auth.LoginProtocol
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.UUID

/** Wipe the owned backing array, including old arrays when growing.
 * DataOutputStream and immutable Strings may retain other transient copies.
 */
internal class WipeableVaultBuffer : ByteArrayOutputStream(1024) {
    override fun write(b: Int) {
        reserve(1)
        super.write(b)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        require(off >= 0 && len >= 0 && off <= b.size - len)
        reserve(len)
        super.write(b, off, len)
    }

    private fun reserve(extra: Int) {
        require(extra <= VaultLimits.MAX_PLAINTEXT - count)
        val needed = count + extra
        if (needed > buf.size) {
            val old = buf
            buf = old.copyOf(maxOf(needed, minOf(VaultLimits.MAX_PLAINTEXT, old.size * 2)))
            old.fill(0)
        }
    }

    fun wipe() {
        buf.fill(0)
        reset()
    }
}

internal object VaultCodec {
    private const val LEGACY_VERSION = 1
    private const val PROFILE_VERSION = 2
    private const val VERSION = 3

    fun encode(records: List<VaultRecord>): ByteArray = encodeDocument(VaultDocument(records))

    fun encodeDocument(document: VaultDocument): ByteArray {
        val records = document.records
        val buffer = WipeableVaultBuffer()
        try {
            DataOutputStream(buffer).use { out ->
                out.writeInt(VERSION)
                out.writeInt(document.profiles.size)
                for (profile in document.profiles) {
                    out.writeUTF(profile.id.toString())
                    out.writeUTF(profile.name)
                    out.writeUTF(profile.gateway)
                    out.writeUTF(profile.retention.name)
                }
                out.writeInt(records.size)
                for (record in records) {
                    out.writeUTF(record.id.toString())
                    out.writeUTF(record.profileId.toString())
                    out.writeUTF(record.gateway)
                    out.writeUTF(record.protocol.name)
                    out.writeUTF(record.username)
                    out.writeBoolean(record.password != null)
                    record.password?.let(out::writeUTF)
                }
            }
            require(buffer.size() <= VaultLimits.MAX_PLAINTEXT)
            return buffer.toByteArray()
        } finally {
            buffer.wipe()
        }
    }

    @Throws(IOException::class)
    fun decode(bytes: ByteArray): List<VaultRecord> = decodeDocument(bytes).records

    fun decodeDocument(bytes: ByteArray): VaultDocument {
        require(bytes.size <= VaultLimits.MAX_PLAINTEXT)
        return DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            val version = input.readInt()
            require(version == VERSION || version == PROFILE_VERSION || version == LEGACY_VERSION)
            val profiles = if (version >= PROFILE_VERSION) {
                val count = input.readInt()
                require(count in 0..VaultDocument.MAX_PROFILES)
                List(count) {
                    val id = UUID.fromString(input.readUTF())
                    val name = input.readUTF()
                    val gateway = input.readUTF()
                    val mode = if (version == VERSION) RetentionMode.valueOf(input.readUTF()) else RetentionMode.MANUAL
                    NetworkProfile(id, name, gateway, mode)
                }
            } else emptyList()
            val size = input.readInt()
            val limit = if (version == VERSION) VaultLimits.MAX_DOCUMENT_RECORDS else VaultLimits.MAX_LEGACY_RECORDS
            require(size in 0..limit)
            val records = List(size) {
                val id = UUID.fromString(input.readUTF())
                val profile = UUID.fromString(input.readUTF())
                val gateway = input.readUTF()
                val protocol = LoginProtocol.valueOf(input.readUTF())
                val username = input.readUTF()
                val password = if (input.readBoolean()) input.readUTF() else null
                VaultRecord(id, profile, gateway, protocol, username, password)
            }
            require(input.read() == -1)
            VaultDocument(records, profiles)
        }
    }
}
