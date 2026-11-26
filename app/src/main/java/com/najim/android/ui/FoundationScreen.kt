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

@Composable
fun FoundationScreen() {
    // Do not retain credentials in saved instance state or persist them.
    var input by remember { mutableStateOf("") }
    var checked by remember { mutableStateOf(false) }
    val result = VoucherInput.validate(input)
    Scaffold { insets ->
        Column(Modifier.fillMaxSize().padding(insets).imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.foundation_notice))
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
