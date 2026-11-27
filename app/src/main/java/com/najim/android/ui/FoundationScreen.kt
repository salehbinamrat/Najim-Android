package com.najim.android.ui

import androidx.compose.foundation.layout.*
import androidx.lifecycle.LifecycleOwner
import androidx.activity.compose.BackHandler
import com.najim.android.settings.AppearanceMode
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
import com.najim.android.storage.RetentionMode

@Composable
internal fun FoundationScreen(
    network: DiscoveryState, refreshNetwork: () -> Unit, attempt: LoginAttemptState,
    lifecycleOwner: LifecycleOwner,
    appearance: AppearanceMode, appearanceReady: Boolean, appearanceSaving: Boolean,
    appearanceFailed: Boolean, changeAppearance: (AppearanceMode) -> Unit,
    feedbackEnabled: Boolean, feedbackReady: Boolean, feedbackSaving: Boolean,
    feedbackFailed: Boolean, changeFeedback: (Boolean) -> Unit, scanAccepted: () -> Unit,
    speechEnabled: Boolean, speechAvailable: Boolean, speechSaving: Boolean,
    speechFailed: Boolean, changeSpeech: (Boolean) -> Unit,
    readSessionAloud: (SessionViewState.Snapshot) -> Boolean, silenceAudio: () -> Unit,
    profileState: ProfileState, refreshProfiles: () -> Boolean, createProfile: (String) -> Boolean,
    setRetention: (NetworkProfile, RetentionMode) -> Boolean,
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
    var passwordVisible by remember(network, selectedProfile, usePassword) { mutableStateOf(false) }
    var protocol by remember(network, selectedProfile) { mutableStateOf(LoginProtocol.HTTPS_PAP) }
    var consent by remember(network, username, password, protocol, usePassword) { mutableStateOf(false) }
    var saveApproved by remember(network, selectedProfile, username, password, protocol, usePassword) { mutableStateOf(false) }
    var legacySaveApproved by remember(network, selectedProfile, username, password, protocol, usePassword, saveApproved) { mutableStateOf(false) }
    val loginBusy = attempt == LoginAttemptState.Running || attempt == LoginAttemptState.Cancelling
    val sessionBusy = sessionState == SessionViewState.Loading || sessionState == SessionViewState.Cancelling || sessionState == SessionViewState.LoggingOut
    var voiceBusy by remember(network, selectedProfile) { mutableStateOf(false) }
    val otherBusy = loginBusy || sessionBusy || profileState.busy
    val busy = otherBusy || voiceBusy
    val galleryActive = galleryState != GalleryState.Idle
    var section by remember { mutableStateOf(if (galleryActive) NajimSection.LOGIN else NajimSection.HOME) }
    LaunchedEffect(attempt) {
        if (section == NajimSection.LOGIN && attempt is LoginAttemptState.Finished &&
            attempt.result in setOf(GatewayLoginResult.MATCHING_SESSION, GatewayLoginResult.LEGACY_MATCHING_SESSION,
                GatewayLoginResult.ALREADY_CONNECTED)) {
            passwordVisible = false
            section = NajimSection.HOME
        }
    }
    val validation = VoucherInput.validate(username)
    val validPassword = !usePassword || (password.isNotEmpty() && password.length <= 128 && password.none { it.isISOControl() } &&
        (protocol != LoginProtocol.HTTP_CHAP || password.all { it.code <= 127 }))
    fun navigate(next: NajimSection) {
        if (busy || galleryActive || next == section) return
        if (section == NajimSection.LOGIN) {
            username = ""
            password = ""
            usePassword = false
            protocol = LoginProtocol.HTTPS_PAP
            consent = false
            saveApproved = false
            legacySaveApproved = false
        }
        section = next
    }
    BackHandler(enabled = section != NajimSection.HOME && !busy && !galleryActive) {
        navigate(NajimSection.HOME)
    }
    Scaffold(bottomBar = {
        NajimNavigation(section, enabled = !busy && !galleryActive, select = ::navigate)
    }) { insets ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(insets).imePadding(), contentAlignment = Alignment.TopCenter) {
        val horizontalPadding = AdaptiveLayout.sidePadding(maxWidth.value).dp
        Column(Modifier.widthIn(max = 600.dp).fillMaxWidth().verticalScroll(key(section) { rememberScrollState() })
            .padding(horizontal = horizontalPadding, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            NajimHeader()
            when (section) {
                NajimSection.HOME -> {
                    HomeScreen(network, refreshNetwork, sessionState, !busy && !galleryActive,
                        refreshSession, clearSession, logoutSession, readSessionAloud, openLogin = { navigate(NajimSection.LOGIN) })
                }
                NajimSection.CARDS -> {
                    CardsScreen(network, profileState, selectedProfile, !busy && !galleryActive,
                        select = {
                            if (selectedProfile !== it) {
                                username = ""
                                password = ""
                                usePassword = false
                                consent = false
                            }
                            selectedProfile = it
                        }, refresh = refreshProfiles, create = createProfile, setRetention = setRetention,
                        load = loadCard, delete = deleteCard,
                        onLoaded = { record ->
                            username = record.username
                            password = record.password.orEmpty()
                            usePassword = record.password != null
                            protocol = record.protocol
                            consent = false
                            saveApproved = false
                            legacySaveApproved = false
                            section = NajimSection.LOGIN
                        }, onDeleting = {
                            username = ""
                            password = ""
                            usePassword = false
                            consent = false
                            saveApproved = false
                            legacySaveApproved = false
                        })
                }
                NajimSection.LOGIN -> {
            val currentProfile = selectedProfile
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.login_profile_heading), style = MaterialTheme.typography.labelLarge)
                    Text(if (currentProfile == null) stringResource(R.string.profile_none)
                        else currentProfile.name, style = MaterialTheme.typography.titleMedium)
                    OutlinedButton(onClick = { navigate(NajimSection.CARDS) }, enabled = !busy && !galleryActive,
                        modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.nav_cards)) }
                }
            }
            Text(stringResource(R.string.login_mode_title), style = MaterialTheme.typography.titleMedium)
            Column(Modifier.selectableGroup()) {
                RadioChoice(stringResource(R.string.login_mode_username), !usePassword, !busy && !galleryActive) {
                    usePassword = false
                    password = ""
                    passwordVisible = false
                }
                RadioChoice(stringResource(R.string.login_mode_password), usePassword, !busy && !galleryActive) {
                    if (!usePassword) password = ""
                    usePassword = true
                    passwordVisible = false
                }
            }
            OutlinedTextField(
                value = username,
                onValueChange = { username = VoucherInput.normalize(it) },
                modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy,
                label = { Text(stringResource(R.string.voucher_label)) },
                visualTransformation = VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
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
            key(network, selectedProfile) {
                VoiceInputPanel(lifecycleOwner, enabled = !otherBusy && !galleryActive,
                    onListening = { voiceBusy = it }, silenceAudio = silenceAudio,
                    onAccepted = { code ->
                        username = code
                        password = ""
                        passwordVisible = false
                        consent = false
                        saveApproved = false
                        legacySaveApproved = false
                    })
                ScanPanel(lifecycleOwner, scanMode, { scanMode = it }, !busy, galleryState,
                    chooseImage, confirmImage, cancelImage, onCode = { code ->
                        scanAccepted()
                        username = code
                        password = ""
                        passwordVisible = false
                        consent = false
                        saveApproved = false
                        legacySaveApproved = false
                    })
            }
            if (usePassword) {
                OutlinedTextField(value = password, onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !busy,
                    label = { Text(stringResource(R.string.login_password)) },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { passwordVisible = !passwordVisible }, enabled = !busy) {
                            Text(stringResource(if (passwordVisible) R.string.password_hide else R.string.password_show))
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = !validPassword)
                if (!validPassword) Text(stringResource(R.string.login_invalid_input))
            }
            Text(stringResource(R.string.login_protocol), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.login_protocol_help), style = MaterialTheme.typography.bodySmall)
            Column(Modifier.selectableGroup()) {
                for (mode in LoginProtocol.entries) {
                    RadioChoice(stringResource(when (mode) {
                        LoginProtocol.HTTPS_PAP -> R.string.login_https
                        LoginProtocol.HTTP_PAP -> R.string.login_http_pap
                        LoginProtocol.HTTP_CHAP -> R.string.login_http_chap
                    }), protocol == mode, !busy) { protocol = mode }
                }
            }
            if (protocol.requiresConsent) {
                Text(stringResource(if (protocol == LoginProtocol.HTTP_PAP) R.string.login_pap_warning
                    else R.string.login_chap_warning), color = MaterialTheme.colorScheme.error)
                CheckChoice(stringResource(R.string.login_consent), consent, !busy) { consent = it }
            }
            if (selectedProfile != null) {
                CheckChoice(stringResource(R.string.profile_save_card), saveApproved,
                    !busy && !galleryActive) { saveApproved = it }
                if (saveApproved && protocol.requiresConsent) {
                    CheckChoice(stringResource(R.string.profile_save_legacy), legacySaveApproved,
                        !busy && !galleryActive) { legacySaveApproved = it }
                }
            }
            Button(onClick = {
                val choice = if (saveApproved) selectedProfile?.let { SaveChoice(it, legacySaveApproved) } else null
                val approved = consent
                passwordVisible = false
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
            if (message != null) Surface(shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant) {
                StatusNotice(stringResource(message), Modifier.fillMaxWidth().padding(12.dp))
            }
            ProfileNoticeText(profileState.notice)
                }
                NajimSection.SETTINGS -> SettingsScreen(appearance, appearanceReady,
                    appearanceSaving, appearanceFailed, !busy && !galleryActive, changeAppearance,
                    feedbackEnabled, feedbackReady, feedbackSaving, feedbackFailed, changeFeedback,
                    speechEnabled, speechAvailable, speechSaving, speechFailed, changeSpeech)
            }
        }
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
