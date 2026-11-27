package com.najim.android.storage

/** Explicit, one-attempt opt-in. Never persisted. */
internal class SaveChoice(val profile: NetworkProfile, val legacyAcknowledged: Boolean) {
    override fun toString() = "SaveChoice(redacted)"
}
