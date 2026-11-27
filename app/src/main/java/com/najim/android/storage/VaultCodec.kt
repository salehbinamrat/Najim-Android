package com.najim.android.storage

import com.najim.android.auth.LoginProtocol
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.UUID

internal object VaultCodec {
    private const val LEGACY_VERSION = 1
    private const val VERSION = 2

    fun encode(records: List<VaultRecord>): ByteArray = encodeDocument(VaultDocument(records))

    fun encodeDocument(document: VaultDocument): ByteArray {
        val records = document.records
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { out ->
            out.writeInt(VERSION)
            out.writeInt(document.profiles.size)
            for (profile in document.profiles) {
                out.writeUTF(profile.id.toString())
                out.writeUTF(profile.name)
                out.writeUTF(profile.gateway)
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
        return buffer.toByteArray().also { require(it.size <= VaultLimits.MAX_PLAINTEXT) }
    }

    @Throws(IOException::class)
    fun decode(bytes: ByteArray): List<VaultRecord> = decodeDocument(bytes).records

    fun decodeDocument(bytes: ByteArray): VaultDocument {
        require(bytes.size <= VaultLimits.MAX_PLAINTEXT)
        return DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            val version = input.readInt()
            require(version == VERSION || version == LEGACY_VERSION)
            val profiles = if (version == VERSION) {
                val count = input.readInt()
                require(count in 0..VaultDocument.MAX_PROFILES)
                List(count) { NetworkProfile(UUID.fromString(input.readUTF()), input.readUTF(), input.readUTF()) }
            } else emptyList()
            val size = input.readInt()
            require(size in 0..VaultLimits.MAX_RECORDS)
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
