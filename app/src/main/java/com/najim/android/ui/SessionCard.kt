package com.najim.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalDensity
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
    var confirmHttp by remember(network, state) { mutableStateOf(false) }
    var confirmLogout by remember(network, state) { mutableStateOf(false) }
    val busy = state == SessionViewState.Loading || state == SessionViewState.Cancelling || state == SessionViewState.LoggingOut
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.session_title), style = MaterialTheme.typography.titleLarge)
            Button(onClick = { refresh(false, false) },
                enabled = enabled && !busy && network is DiscoveryState.Ready,
                modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.session_refresh_https))
            }
            OutlinedButton(onClick = { confirmHttp = true },
                enabled = enabled && !busy && network is DiscoveryState.Ready,
                modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.session_refresh_http))
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
                is SessionViewState.LogoutFinished -> StatusNotice(stringResource(logoutMessage(state.result)))
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
    if (confirmHttp && enabled && !busy && network is DiscoveryState.Ready) {
        AlertDialog(onDismissRequest = { confirmHttp = false },
            title = { Text(stringResource(R.string.session_refresh_http)) },
            text = { Text(stringResource(R.string.session_http_read_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmHttp = false
                    refresh(true, true)
                }) { Text(stringResource(R.string.session_http_read_button)) }
            },
            dismissButton = { TextButton(onClick = { confirmHttp = false }) { Text(stringResource(R.string.logout_back)) } })
    }
    val expected = state as? SessionViewState.Snapshot
    if (confirmLogout && expected != null && expected.status is SessionStatus.Active && enabled) {
        AlertDialog(onDismissRequest = { confirmLogout = false },
            title = { Text(stringResource(R.string.logout_action)) },
            text = { Text(stringResource(if (expected.legacy) R.string.logout_confirm_http else R.string.logout_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
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
            Surface(shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
                Column(Modifier.fillMaxWidth().padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    StatusNotice(stringResource(R.string.session_active))
                    Text(stringResource(R.string.session_account), style = MaterialTheme.typography.labelMedium)
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(SessionDisplay.maskAccount(status.account.username),
                            style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
            val m = status.account.metrics
            MetricGroup(R.string.session_balance_group, listOf(
                MetricValue(R.string.session_remaining, m.remainingTotal?.reported),
                MetricValue(R.string.session_time_left, SessionDisplay.duration(m.timeLeftSeconds)),
                MetricValue(R.string.session_remaining_download, m.remainingDownload?.reported),
                MetricValue(R.string.session_remaining_upload, m.remainingUpload?.reported)
            ))
            MetricGroup(R.string.session_usage_group, listOf(
                MetricValue(R.string.session_download, m.downloaded?.reported),
                MetricValue(R.string.session_upload, m.uploaded?.reported),
                MetricValue(R.string.session_uptime, SessionDisplay.duration(m.uptimeSeconds))
            ))
        }
        else -> StatusNotice(stringResource(when (status) {
            SessionStatus.NetworkChanged -> R.string.login_network_changed
            SessionStatus.ConsentRequired -> R.string.login_consent_required
            SessionStatus.Unavailable -> R.string.session_unavailable
            SessionStatus.Unknown -> R.string.session_unknown
            SessionStatus.LoggedOut -> R.string.session_logged_out
            is SessionStatus.Active -> R.string.session_active
        }))
    }
}

private class MetricValue(val label: Int, val value: String?)

@Composable
private fun MetricGroup(title: Int, metrics: List<MetricValue>) {
    Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns = AdaptiveLayout.metricColumns(maxWidth.value, LocalDensity.current.fontScale)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (group in metrics.chunked(columns)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (metric in group) MetricTile(metric, Modifier.weight(1f))
                    if (columns == 2 && group.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MetricTile(metric: MetricValue, modifier: Modifier) {
    Surface(modifier = modifier, shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(metric.label), style = MaterialTheme.typography.labelLarge)
            val value = metric.value
            if (value == null) Text(stringResource(R.string.session_not_reported),
                style = MaterialTheme.typography.bodyMedium)
            else CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(value, style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}
