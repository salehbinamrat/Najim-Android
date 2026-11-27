package com.najim.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.najim.android.R
import com.najim.android.auth.SessionDisplay
import com.najim.android.auth.SessionStatus
import com.najim.android.auth.SessionViewState
import com.najim.android.network.DiscoveryState

@Composable
internal fun SessionCard(network: DiscoveryState, state: SessionViewState,
                         enabled: Boolean, refresh: (Boolean, Boolean) -> Boolean,
                         clear: () -> Unit) {
    var legacy by remember(network) { mutableStateOf(false) }
    var consent by remember(network, legacy) { mutableStateOf(false) }
    val busy = state == SessionViewState.Loading || state == SessionViewState.Cancelling
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.session_title), style = MaterialTheme.typography.titleLarge)
            Row {
                Checkbox(checked = legacy, enabled = enabled && !busy, onCheckedChange = {
                    legacy = it
                    consent = false
                    clear()
                })
                Text(stringResource(R.string.session_legacy_option), Modifier.padding(top = 12.dp))
            }
            if (legacy) {
                Text(stringResource(R.string.session_legacy_warning), color = MaterialTheme.colorScheme.error)
                Row {
                    Checkbox(checked = consent, enabled = enabled && !busy,
                        onCheckedChange = { consent = it })
                    Text(stringResource(R.string.session_consent), Modifier.padding(top = 12.dp))
                }
            }
            Button(onClick = {
                val approved = consent
                consent = false
                refresh(legacy, approved)
            }, enabled = enabled && !busy && network is DiscoveryState.Ready && (!legacy || consent),
                modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.session_refresh))
            }
            when (state) {
                SessionViewState.Idle -> Text(stringResource(R.string.session_idle))
                SessionViewState.Loading, SessionViewState.Cancelling -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(stringResource(if (state == SessionViewState.Loading)
                        R.string.session_loading else R.string.session_cancelling))
                }
                is SessionViewState.Snapshot -> {
                    SessionSnapshot(state.status)
                    Text(stringResource(R.string.session_snapshot_note),
                        style = MaterialTheme.typography.bodySmall)
                    if (state.legacy) Text(stringResource(R.string.session_legacy_warning),
                        color = MaterialTheme.colorScheme.error)
                }
            }
            if (state != SessionViewState.Idle && state != SessionViewState.Cancelling) {
                OutlinedButton(onClick = clear) {
                    Text(stringResource(if (busy) R.string.session_cancel else R.string.session_clear))
                }
            }
        }
    }
}

@Composable
private fun SessionSnapshot(status: SessionStatus) {
    when (status) {
        is SessionStatus.Active -> {
            Text(stringResource(R.string.session_active), style = MaterialTheme.typography.titleMedium)
            Metric(R.string.session_account, SessionDisplay.maskAccount(status.account.username))
            val m = status.account.metrics
            Metric(R.string.session_remaining, m.remainingTotal?.reported)
            Metric(R.string.session_time_left, SessionDisplay.duration(m.timeLeftSeconds))
            Metric(R.string.session_uptime, SessionDisplay.duration(m.uptimeSeconds))
            Metric(R.string.session_download, m.downloaded?.reported)
            Metric(R.string.session_upload, m.uploaded?.reported)
            Metric(R.string.session_remaining_download, m.remainingDownload?.reported)
            Metric(R.string.session_remaining_upload, m.remainingUpload?.reported)
        }
        else -> Text(stringResource(when (status) {
            SessionStatus.NetworkChanged -> R.string.login_network_changed
            SessionStatus.ConsentRequired -> R.string.login_consent_required
            SessionStatus.Unavailable -> R.string.session_unavailable
            SessionStatus.Unknown -> R.string.session_unknown
            SessionStatus.LoggedOut -> R.string.session_logged_out
            is SessionStatus.Active -> R.string.session_active
        }))
    }
}

@Composable
private fun Metric(label: Int, value: String?) {
    HorizontalDivider()
    Text(stringResource(label), style = MaterialTheme.typography.labelLarge)
    if (value == null) Text(stringResource(R.string.session_not_reported),
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    else CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}
