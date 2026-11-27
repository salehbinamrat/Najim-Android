package com.najim.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.najim.android.R
import com.najim.android.network.DiscoveryState
import com.najim.android.storage.NetworkProfile
import com.najim.android.storage.ProfileNotice
import com.najim.android.storage.ProfileState
import com.najim.android.storage.VaultLimits
import com.najim.android.storage.RetentionMode

@Composable
internal fun ProfilePicker(network: DiscoveryState, state: ProfileState, selected: NetworkProfile?,
                           enabled: Boolean, onSelect: (NetworkProfile?) -> Unit,
                           refresh: () -> Boolean, create: (String) -> Boolean,
                           setRetention: (NetworkProfile, RetentionMode) -> Boolean) {
    var name by remember(network) { mutableStateOf("") }
    val gateway = (network as? DiscoveryState.Ready)?.gateway
    val available = state.profiles.filter { it.gateway == gateway }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.profile_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.profile_identity_warning))
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.ready) {
                if (selected != null) {
                    val count = state.cards.count { it.profileId == selected.id }
                    Text(stringResource(R.string.saved_capacity, count, VaultLimits.MAX_RECORDS),
                        style = MaterialTheme.typography.labelLarge)
                    if (count >= VaultLimits.MAX_RECORDS) Text(stringResource(
                        if (selected.retention == RetentionMode.MANUAL) R.string.saved_capacity_full
                        else R.string.saved_auto_full))
                    RetentionPicker(selected, enabled, setRetention)
                }
                Column(Modifier.selectableGroup()) {
                    RadioChoice(stringResource(R.string.profile_none), selected == null, enabled) { onSelect(null) }
                    for (profile in available) RadioChoice(profile.name, selected === profile, enabled) { onSelect(profile) }
                }
                if (gateway != null) {
                    OutlinedTextField(value = name, onValueChange = { if (it.length <= 64) name = it },
                        enabled = enabled, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.profile_name)) })
                    OutlinedButton(onClick = { if (create(name)) name = "" },
                        enabled = enabled && name.isNotBlank()) { Text(stringResource(R.string.profile_create)) }
                }
            }
            OutlinedButton(onClick = { refresh() }, enabled = enabled) {
                Text(stringResource(R.string.profile_refresh))
            }
            ProfileNoticeText(state.notice)
        }
    }
}

@Composable
private fun RetentionPicker(profile: NetworkProfile, enabled: Boolean,
                            setRetention: (NetworkProfile, RetentionMode) -> Boolean) {
    var confirm by remember(profile, enabled) { mutableStateOf(false) }
    Text(stringResource(R.string.saved_retention_title), style = MaterialTheme.typography.titleSmall)
    Column(Modifier.selectableGroup()) {
        RadioChoice(stringResource(R.string.saved_retention_manual), profile.retention == RetentionMode.MANUAL, enabled) {
            if (profile.retention != RetentionMode.MANUAL) setRetention(profile, RetentionMode.MANUAL)
        }
        RadioChoice(stringResource(R.string.saved_retention_auto), profile.retention == RetentionMode.AUTO_OLDEST, enabled) {
            if (profile.retention != RetentionMode.AUTO_OLDEST) confirm = true
        }
    }
    if (confirm && enabled) AlertDialog(onDismissRequest = { confirm = false },
        title = { Text(stringResource(R.string.saved_retention_auto)) },
        text = { Text(stringResource(R.string.saved_retention_confirm, profile.name)) },
        confirmButton = { TextButton(onClick = {
            confirm = false
            setRetention(profile, RetentionMode.AUTO_OLDEST)
        }) { Text(stringResource(R.string.saved_retention_enable)) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.logout_back)) } })
}

@Composable
internal fun ProfileNoticeText(notice: ProfileNotice) {
    val message = when (notice) {
        ProfileNotice.NONE -> null
        ProfileNotice.CREATED -> R.string.profile_created
        ProfileNotice.SAVED -> R.string.profile_saved
        ProfileNotice.FULL -> R.string.profile_full
        ProfileNotice.UNAVAILABLE -> R.string.profile_unavailable
        ProfileNotice.INVALID -> R.string.profile_invalid
        ProfileNotice.DELETED -> R.string.saved_deleted
        ProfileNotice.LOADED -> R.string.saved_loaded
        ProfileNotice.NOT_FOUND -> R.string.saved_not_found
        ProfileNotice.RETENTION_CHANGED -> R.string.saved_retention_changed
    }
    if (message != null) StatusNotice(stringResource(message))
}
