package com.najim.android.auth

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/** Decode only bounded, well-formed UTF-8. Never turn invalid bytes into identity evidence. */
internal object PortalText {
    fun decode(bytes: ByteArray): String? {
        if (bytes.isEmpty() || bytes.size > GatewayTransport.MAX_RESPONSE_BYTES) return null
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) { null }
    }
}
