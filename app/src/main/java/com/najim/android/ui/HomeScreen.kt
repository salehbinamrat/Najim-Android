package com.najim.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.najim.android.R
import com.najim.android.auth.SessionViewState
import com.najim.android.network.DiscoveryState

@Composable
internal fun HomeScreen(network: DiscoveryState, refreshNetwork: () -> Unit,
                        session: SessionViewState, enabled: Boolean,
                        refreshSession: (Boolean, Boolean) -> Boolean, clearSession: () -> Unit,
                        logoutSession: (SessionViewState.Snapshot, Boolean) -> Boolean,
                        openLogin: () -> Unit) {
    NetworkCard(network, refreshNetwork)
    SessionCard(network, session, enabled = enabled,
        refresh = refreshSession, clear = clearSession, logout = logoutSession)
    Button(onClick = openLogin, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.nav_login))
    }
}

@Composable
private fun NetworkCard(network: DiscoveryState, refresh: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.network_title), style = MaterialTheme.typography.titleMedium)
            val message = when (network) {
                DiscoveryState.NoWifi -> R.string.network_no_wifi
                DiscoveryState.AmbiguousWifi -> R.string.network_ambiguous
                DiscoveryState.NoGateway -> R.string.network_no_gateway
                DiscoveryState.AmbiguousGateway -> R.string.network_ambiguous_gateway
                DiscoveryState.Unavailable -> R.string.network_unavailable
                is DiscoveryState.Ready -> R.string.network_gateway
            }
            Text(stringResource(message))
            if (network is DiscoveryState.Ready) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(network.gateway)
                }
                Text(stringResource(R.string.network_not_authenticated))
            }
            OutlinedButton(onClick = refresh) { Text(stringResource(R.string.network_refresh)) }
        }
    }
}
