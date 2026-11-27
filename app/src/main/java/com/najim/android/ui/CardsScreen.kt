package com.najim.android.ui

import androidx.compose.runtime.Composable
import com.najim.android.network.DiscoveryState
import com.najim.android.storage.*

@Composable
internal fun CardsScreen(network: DiscoveryState, state: ProfileState, selected: NetworkProfile?,
                         enabled: Boolean, select: (NetworkProfile?) -> Unit,
                         refresh: () -> Boolean, create: (String) -> Boolean,
                         setRetention: (NetworkProfile, RetentionMode) -> Boolean,
                         load: (NetworkProfile, SavedVoucher, (VaultRecord) -> Unit) -> Boolean,
                         delete: (NetworkProfile, SavedVoucher) -> Boolean,
                         onLoaded: (VaultRecord) -> Unit, onDeleting: () -> Unit) {
    ProfilePicker(network, state, selected, enabled, select, refresh, create, setRetention)
    if (selected != null) {
        SavedVoucherCard(selected, state.cards.filter { it.profileId == selected.id },
            enabled = enabled,
            load = { card -> load(selected, card, onLoaded) },
            delete = { card ->
                onDeleting()
                delete(selected, card)
            })
    }
}
