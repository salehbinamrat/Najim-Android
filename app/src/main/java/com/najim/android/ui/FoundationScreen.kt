package com.najim.android.ui

import androidx.compose.foundation.layout.*
import androidx.lifecycle.LifecycleOwner
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.najim.android.R
import com.najim.android.ocr.GalleryState
import com.najim.android.ocr.ScanMode
import com.najim.android.auth.GatewayLoginResult
import com.najim.android.auth.LoginAttemptState
import com.najim.android.auth.LoginProtocol
import com.najim.android.auth.SessionViewState
import com.najim.android.domain.VoucherInput
import com.najim.android.network.DiscoveryState
import com.najim.android.storage.ProfileState
import com.najim.android.storage.NetworkProfile
import com.najim.android.storage.SaveChoice
import com.najim.android.storage.SavedVoucher
import com.najim.android.storage.VaultRecord

@Composable
internal fun FoundationScreen(
    network: DiscoveryState, refreshNetwork: () -> Unit, attempt: LoginAttemptState,
    lifecycleOwner: LifecycleOwner,
    profileState: ProfileState, refreshProfiles: () -> Boolean, createProfile: (String) -> Boolean,
    loadCard: (NetworkProfile, SavedVoucher, (VaultRecord) -> Unit) -> Boolean,
    deleteCard: (NetworkProfile, SavedVoucher) -> Boolean,
    sessionState: SessionViewState, refreshSession: (Boolean, Boolean) -> Boolean, clearSession: () -> Unit,
    logoutSession: (SessionViewState.Snapshot, Boolean) -> Boolean,
    galleryState: GalleryState, chooseImage: (ScanMode) -> Unit, confirmImage: () -> String?, cancelImage: () -> Unit,
    startLogin: (String, String?, LoginProtocol, Boolean, SaveChoice?) -> Boolean, cancelLogin: () -> Unit
) {
    // No rememberSaveable or persistent secrets. Reset consent when inputs, mode or network change.
    var selectedProfile by remember(network, profileState.profiles) { mutableStateOf<NetworkProfile?>(null) }
    var username by remember(network, selectedProfile) { mutableStateOf("") }
    var scanMode by remember { mutableStateOf(ScanMode.OCR) }
    var password by remember(network, selectedProfile) { mutableStateOf("") }
    var usePassword by remember(network, selectedProfile) { mutableStateOf(false) }
    var protocol by remember(network, selectedProfile) { mutableStateOf(LoginProtocol.HTTPS_PAP) }
    var consent by remember(network, username, password, protocol, usePassword) { mutableStateOf(false) }
    var saveApproved by remember(network, selectedProfile, username, password, protocol, usePassword) { mutableStateOf(false) }
    var legacySaveApproved by remember(network, selectedProfile, username, password, protocol, usePassword, saveApproved) { mutableStateOf(false) }
    val loginBusy = attempt == LoginAttemptState.Running || attempt == LoginAttemptState.Cancelling
    val sessionBusy = sessionState == SessionViewState.Loading || sessionState == SessionViewState.Cancelling || sessionState == SessionViewState.LoggingOut
    val busy = loginBusy || sessionBusy || profileState.busy
    val galleryActive = galleryState != GalleryState.Idle
    val validation = VoucherInput.validate(username)
    val validPassword = !usePassword || (password.length <= 128 && password.none { it.isISOControl() } &&
        (protocol != LoginProtocol.HTTP_CHAP || password.all { it.code <= 127 }))
    Scaffold { insets ->
        Column(Modifier.fillMaxSize().padding(insets).imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.foundation_notice))
            NetworkCard(network, refreshNetwork)
            SessionCard(network, sessionState, enabled = !loginBusy && !galleryActive,
                refresh = refreshSession, clear = clearSession, logout = logoutSession)
            ProfilePicker(network, profileState, selectedProfile, enabled = !busy && !galleryActive,
                onSelect = {
                    if (selectedProfile !== it) {
                        username = ""
                        password = ""
                        usePassword = false
                        consent = false
                    }
                    selectedProfile = it
                }, refresh = refreshProfiles, create = createProfile)
            val chosenProfile = selectedProfile
            if (chosenProfile != null) {
                SavedVoucherCard(chosenProfile, profileState.cards.filter { it.profileId == chosenProfile.id },
                    enabled = !busy && !galleryActive,
                    load = { card -> loadCard(chosenProfile, card) { record ->
                        username = record.username
                        password = record.password.orEmpty()
                        usePassword = record.password != null
                        protocol = record.protocol
                        consent = false
                        saveApproved = false
                        legacySaveApproved = false
                    } }, delete = { card ->
                        username = ""
                        password = ""
                        usePassword = false
                        consent = false
                        saveApproved = false
                        legacySaveApproved = false
                        deleteCard(chosenProfile, card)
                    })
            }
            OutlinedTextField(
                value = username,
                onValueChange = { username = VoucherInput.normalize(it) },
                modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy,
                label = { Text(stringResource(R.string.voucher_label)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                isError = username.isNotEmpty() && validation.error != null
            )
            if (username.isNotEmpty() && validation.error != null) {
                Text(stringResource(when (validation.error) {
                    VoucherInput.Error.EMPTY -> R.string.error_empty
                    VoucherInput.Error.WHITESPACE -> R.string.error_whitespace
                    VoucherInput.Error.CONTROL_CHARACTER -> R.string.error_control
                    VoucherInput.Error.TOO_LONG -> R.string.error_length
                    null -> R.string.error_empty
                }))
            }
            Text(stringResource(R.string.scan_mode), style = MaterialTheme.typography.titleMedium)
            for (mode in ScanMode.entries) {
                Row {
                    RadioButton(selected = scanMode == mode, enabled = !busy && !galleryActive,
                        onClick = { scanMode = mode })
                    Text(stringResource(if (mode == ScanMode.OCR) R.string.scan_mode_ocr else R.string.scan_mode_qr),
                        Modifier.padding(top = 12.dp))
                }
            }
            key(scanMode) {
                VoucherScanner(lifecycleOwner, scanMode, enabled = !busy && !galleryActive, onConfirmed = { code ->
                    username = code
                    password = ""
                    consent = false
                })
            }
            GalleryScanner(galleryState, enabled = !busy, choose = { chooseImage(scanMode) }, confirm = {
                confirmImage()?.let { code ->
                    username = code
                    password = ""
                    consent = false
                }
            }, cancel = cancelImage)
            Row {
                Checkbox(checked = usePassword, enabled = !busy, onCheckedChange = {
                    usePassword = it
                    password = ""
                })
                Text(stringResource(R.string.login_use_password), Modifier.padding(top = 12.dp))
            }
            if (usePassword) {
                OutlinedTextField(value = password, onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy,
                    label = { Text(stringResource(R.string.login_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = !validPassword)
                if (!validPassword) Text(stringResource(R.string.login_invalid_input))
            }
            Text(stringResource(R.string.login_protocol), style = MaterialTheme.typography.titleMedium)
            for (mode in LoginProtocol.entries) {
                Row {
                    RadioButton(selected = protocol == mode, enabled = !busy, onClick = { protocol = mode })
                    Text(stringResource(when (mode) {
                        LoginProtocol.HTTPS_PAP -> R.string.login_https
                        LoginProtocol.HTTP_PAP -> R.string.login_http_pap
                        LoginProtocol.HTTP_CHAP -> R.string.login_http_chap
                    }), Modifier.padding(top = 12.dp))
                }
            }
            if (protocol.requiresConsent) {
                Text(stringResource(if (protocol == LoginProtocol.HTTP_PAP) R.string.login_pap_warning
                    else R.string.login_chap_warning), color = MaterialTheme.colorScheme.error)
                Row {
                    Checkbox(checked = consent, enabled = !busy, onCheckedChange = { consent = it })
                    Text(stringResource(R.string.login_consent), Modifier.padding(top = 12.dp))
                }
            }
            if (selectedProfile != null) {
                Row {
                    Checkbox(checked = saveApproved, enabled = !busy && !galleryActive,
                        onCheckedChange = { saveApproved = it })
                    Text(stringResource(R.string.profile_save_card), Modifier.padding(top = 12.dp))
                }
                if (saveApproved && protocol.requiresConsent) {
                    Row {
                        Checkbox(checked = legacySaveApproved, enabled = !busy && !galleryActive,
                            onCheckedChange = { legacySaveApproved = it })
                        Text(stringResource(R.string.profile_save_legacy), Modifier.padding(top = 12.dp))
                    }
                }
            }
            Button(onClick = {
                val choice = if (saveApproved) selectedProfile?.let { SaveChoice(it, legacySaveApproved) } else null
                val approved = consent
                consent = false
                saveApproved = false
                legacySaveApproved = false
                if (startLogin(username, if (usePassword) password else null, protocol, approved, choice)) {
                    username = ""
                    password = ""
                }
            }, modifier = Modifier.fillMaxWidth(), enabled = !busy && !galleryActive && network is DiscoveryState.Ready &&
                validation.error == null && validPassword && (!protocol.requiresConsent || consent) &&
                (!saveApproved || (selectedProfile != null && (!protocol.requiresConsent || legacySaveApproved)))) {
                Text(stringResource(R.string.login_submit))
            }
            if (loginBusy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                OutlinedButton(onClick = cancelLogin, enabled = attempt == LoginAttemptState.Running,
                    modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.login_cancel)) }
            }
            val message = when (attempt) {
                LoginAttemptState.Idle -> null
                LoginAttemptState.Running -> R.string.login_running
                LoginAttemptState.Cancelling -> R.string.login_cancelling
                LoginAttemptState.Cancelled -> R.string.login_cancelled
                is LoginAttemptState.Finished -> resultMessage(attempt.result)
            }
            if (message != null) Text(stringResource(message))
        }
    }
}

private fun resultMessage(result: GatewayLoginResult): Int = when (result) {
    GatewayLoginResult.INVALID_INPUT -> R.string.login_invalid_input
    GatewayLoginResult.NETWORK_CHANGED -> R.string.login_network_changed
    GatewayLoginResult.INSECURE_HTTP_NOT_APPROVED -> R.string.login_consent_required
    GatewayLoginResult.ALREADY_CONNECTED -> R.string.login_existing_session
    GatewayLoginResult.BASELINE_UNKNOWN -> R.string.login_baseline_unknown
    GatewayLoginResult.UNSUPPORTED_PORTAL -> R.string.login_unsupported
    GatewayLoginResult.REQUEST_REJECTED -> R.string.login_rejected
    GatewayLoginResult.TRANSPORT_UNAVAILABLE -> R.string.login_unavailable
    GatewayLoginResult.ACCOUNT_MISMATCH -> R.string.login_account_mismatch
    GatewayLoginResult.SESSION_UNVERIFIED -> R.string.login_unverified
    GatewayLoginResult.MATCHING_SESSION -> R.string.login_matching
    GatewayLoginResult.LEGACY_MATCHING_SESSION -> R.string.login_legacy_matching
    GatewayLoginResult.ROUTER_USER_NOT_FOUND -> R.string.router_user_not_found
    GatewayLoginResult.ROUTER_INVALID_PASSWORD -> R.string.router_invalid_password
    GatewayLoginResult.ROUTER_INVALID_CREDENTIALS -> R.string.router_invalid_credentials
    GatewayLoginResult.ROUTER_DATA_LIMIT -> R.string.router_data_limit
    GatewayLoginResult.ROUTER_TIME_LIMIT -> R.string.router_time_limit
    GatewayLoginResult.ROUTER_ACCOUNT_EXPIRED -> R.string.router_account_expired
    GatewayLoginResult.ROUTER_SESSION_LIMIT -> R.string.router_session_limit
    GatewayLoginResult.ROUTER_ALREADY_LOGGED_IN -> R.string.router_already_logged_in
    GatewayLoginResult.ROUTER_DEVICE_RESTRICTED -> R.string.router_device_restricted
    GatewayLoginResult.ROUTER_RADIUS_TIMEOUT -> R.string.router_radius_timeout
    GatewayLoginResult.ROUTER_NO_VALID_PROFILE -> R.string.router_no_valid_profile
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
