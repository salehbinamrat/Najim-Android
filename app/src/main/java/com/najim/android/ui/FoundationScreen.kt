package com.najim.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.najim.android.R
import com.najim.android.domain.VoucherInput
import com.najim.android.network.DiscoveryState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

@Composable
fun FoundationScreen(network: DiscoveryState, refreshNetwork: () -> Unit) {
    // Do not retain credentials in saved instance state or persist them.
    var input by remember { mutableStateOf("") }
    var checked by remember { mutableStateOf(false) }
    val result = VoucherInput.validate(input)
    Scaffold { insets ->
        Column(Modifier.fillMaxSize().padding(insets).imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.foundation_notice))
            NetworkCard(network, refreshNetwork)
            OutlinedTextField(
                value = input,
                onValueChange = { input = VoucherInput.normalize(it); checked = false },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text(stringResource(R.string.voucher_label)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                isError = checked && result.error != null
            )
            Button(onClick = { checked = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.check_input))
            }
            if (checked) {
                val message = when (result.error) {
                    VoucherInput.Error.EMPTY -> R.string.error_empty
                    VoucherInput.Error.WHITESPACE -> R.string.error_whitespace
                    VoucherInput.Error.CONTROL_CHARACTER -> R.string.error_control
                    VoucherInput.Error.TOO_LONG -> R.string.error_length
                    null -> R.string.format_valid
                }
                Text(stringResource(message))
            }
        }
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
