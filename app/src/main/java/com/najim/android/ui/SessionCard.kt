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
import com.najim.android.auth.LogoutResult
import com.najim.android.auth.SessionStatus
import com.najim.android.auth.SessionViewState
import com.najim.android.network.DiscoveryState

@Composable
internal fun SessionCard(network: DiscoveryState, state: SessionViewState,
                         enabled: Boolean, refresh: (Boolean, Boolean) -> Boolean,
                         clear: () -> Unit, logout: (SessionViewState.Snapshot, Boolean) -> Boolean) {
    var legacy by remember(network) { mutableStateOf(false) }
    var consent by remember(network, legacy) { mutableStateOf(false) }
    var confirmLogout by remember(network, state) { mutableStateOf(false) }
    val busy = state == SessionViewState.Loading || state == SessionViewState.Cancelling || state == SessionViewState.LoggingOut
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
                SessionViewState.LoggingOut -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(stringResource(R.string.logout_running))
                }
                is SessionViewState.LogoutFinished -> Text(stringResource(logoutMessage(state.result)))
                is SessionViewState.Snapshot -> {
                    SessionSnapshot(state.status)
                    Text(stringResource(R.string.session_snapshot_note),
                        style = MaterialTheme.typography.bodySmall)
                    if (state.legacy) Text(stringResource(R.string.session_legacy_warning),
                        color = MaterialTheme.colorScheme.error)
                    if (state.status is SessionStatus.Active) {
                        OutlinedButton(onClick = { confirmLogout = true }, enabled = enabled && network is DiscoveryState.Ready) {
                            Text(stringResource(R.string.logout_action), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            if (state != SessionViewState.Idle && state != SessionViewState.Cancelling && state != SessionViewState.LoggingOut) {
                OutlinedButton(onClick = clear) {
                    Text(stringResource(if (busy) R.string.session_cancel else R.string.session_clear))
                }
            }
        }
    }
    val expected = state as? SessionViewState.Snapshot
    if (confirmLogout && expected != null && expected.status is SessionStatus.Active && enabled) {
        AlertDialog(onDismissRequest = { confirmLogout = false },
            title = { Text(stringResource(R.string.logout_action)) },
            text = { Text(stringResource(if (expected.legacy) R.string.logout_confirm_http else R.string.logout_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    consent = false
                    logout(expected, expected.legacy)
                }) { Text(stringResource(R.string.logout_confirm_button)) }
            },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text(stringResource(R.string.logout_back)) } })
    }
}

private fun logoutMessage(result: LogoutResult): Int = when (result) {
    LogoutResult.CONSENT_REQUIRED -> R.string.login_consent_required
    LogoutResult.NETWORK_CHANGED -> R.string.login_network_changed
    LogoutResult.ALREADY_LOGGED_OUT -> R.string.logout_already
    LogoutResult.ACCOUNT_CHANGED -> R.string.logout_account_changed
    LogoutResult.PREFLIGHT_UNKNOWN -> R.string.logout_preflight_unknown
    LogoutResult.UNVERIFIED -> R.string.logout_unverified
    LogoutResult.STILL_ACTIVE -> R.string.logout_still_active
    LogoutResult.LOGGED_OUT -> R.string.logout_verified
    LogoutResult.LEGACY_LOGGED_OUT -> R.string.logout_legacy_verified
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
