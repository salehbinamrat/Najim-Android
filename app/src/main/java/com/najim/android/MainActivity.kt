package com.najim.android

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.najim.android.ocr.GalleryImport
import com.najim.android.ocr.GalleryState
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.najim.android.auth.AndroidGatewayTransport
import com.najim.android.auth.LoginAttemptController
import com.najim.android.auth.LoginAttemptState
import com.najim.android.auth.SessionRefreshController
import com.najim.android.auth.SessionViewState
import com.najim.android.network.DiscoveryState
import com.najim.android.network.WifiDiscovery
import com.najim.android.ui.FoundationScreen
import com.najim.android.ui.NajimTheme
import com.najim.android.storage.AndroidVault
import com.najim.android.storage.ProfileController
import com.najim.android.storage.ProfileState
import java.util.concurrent.Executors
import com.najim.android.settings.AppearanceMode
import com.najim.android.settings.AppearancePreferences
import com.najim.android.feedback.AndroidTonePlayer
import com.najim.android.feedback.ArabicSpeech
import com.najim.android.feedback.GuidanceGate
import com.najim.android.feedback.SpeechPolicy
import com.najim.android.feedback.SpeechCue

class MainActivity : ComponentActivity() {
    private val tonePlayer = AndroidTonePlayer()
    private lateinit var speechPlayer: ArabicSpeech
    private val guidance = GuidanceGate({ cue ->
        tonePlayer.stop()
        speechPlayer.speak(cue)
    }, tonePlayer::play)
    private var resumed = false
    private var started = false
    private var startingDiscovery = false
    private var attemptHttpApproved = false
    private var audioAllowed = false
    private val speechEnabled = mutableStateOf(false)
    private val speechAvailable = mutableStateOf(false)
    private var pendingReadAloud: SessionViewState.Snapshot? = null
    private val speechSaving = mutableStateOf(false)
    private val speechFailed = mutableStateOf(false)
    private val feedbackEnabled = mutableStateOf(false)
    private val feedbackReady = mutableStateOf(false)
    private val feedbackSaving = mutableStateOf(false)
    private val feedbackFailed = mutableStateOf(false)
    private val appearance = mutableStateOf(AppearanceMode.SYSTEM)
    private val appearanceReady = mutableStateOf(false)
    private val appearanceSaving = mutableStateOf(false)
    private val appearanceFailed = mutableStateOf(false)
    private lateinit var appearancePreferences: AppearancePreferences
    private val discoveryState = mutableStateOf<DiscoveryState>(DiscoveryState.Unavailable)
    private val attemptState = mutableStateOf<LoginAttemptState>(LoginAttemptState.Idle)
    private val inputGeneration = mutableStateOf(0)
    private lateinit var discovery: WifiDiscovery
    private lateinit var attempts: LoginAttemptController
    private lateinit var profiles: ProfileController
    private val profileState = mutableStateOf(ProfileState())
    private lateinit var session: SessionRefreshController
    private val sessionState = mutableStateOf<SessionViewState>(SessionViewState.Idle)
    private val statusDiagnostics = mutableStateOf<List<com.najim.android.auth.GatewayDiagnostic>>(emptyList())
    private lateinit var gallery: GalleryImport
    private val galleryState = mutableStateOf<GalleryState>(GalleryState.Idle)
    private val picker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (::gallery.isInitialized) gallery.pickerResult(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Protect credentials, scanned images and subscriber session information from
        // ordinary screenshots, screen recording and recent-task previews.
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        gallery = GalleryImport(applicationContext.contentResolver, galleryWorker) { galleryState.value = it }
        val main = Handler(Looper.getMainLooper())
        val transport = AndroidGatewayTransport(applicationContext) { selected, report, cancelled ->
            main.post {
                if (started && !isDestroyed && discoveryState.value == selected && !cancelled()) {
                    statusDiagnostics.value = (statusDiagnostics.value + report).takeLast(2)
                }
            }
        }
        speechPlayer = ArabicSpeech(applicationContext) { available ->
            speechAvailable.value = available
            val requested = pendingReadAloud
            pendingReadAloud = null
            val active = requested?.status as? com.najim.android.auth.SessionStatus.Active
            if (available && resumed && requested != null && sessionState.value === requested && active != null) {
                speechPlayer.speakMetrics(active.account.metrics)
            }
        }
        appearancePreferences = AppearancePreferences(applicationContext)
        storageWorker.execute {
            val loaded = runCatching { appearancePreferences.read() }
            val audio = runCatching { appearancePreferences.readFeedbackEnabled() }
            val speech = runCatching { appearancePreferences.readSpeechEnabled() }
            main.post {
                if (!isDestroyed) {
                    appearance.value = loaded.getOrDefault(AppearanceMode.SYSTEM)
                    appearanceFailed.value = loaded.isFailure
                    appearanceReady.value = true
                    feedbackEnabled.value = audio.getOrDefault(false)
                    feedbackFailed.value = audio.isFailure
                    feedbackReady.value = true
                    audioAllowed = feedbackEnabled.value && audio.isSuccess
                    speechEnabled.value = speech.getOrDefault(false)
                    speechFailed.value = speech.isFailure
                    syncGuidance()
                }
            }
        }
        profiles = ProfileController(storageWorker, { task -> main.post { task() } },
            AndroidVault.create(applicationContext), { profileState.value = it })
        attempts = LoginAttemptController(
            worker = loginWorker,
            dispatch = { task -> main.post { task() } },
            login = transport::login,
            publish = { next ->
                val previous = attemptState.value
                attemptState.value = next
                if (previous != next) guidance.emit(SpeechPolicy.login(next))
                // The explicit login approval covers verification/status reads of
                // that same successful attempt only, never a later automatic downgrade.
                if (started && next is LoginAttemptState.Finished && ::session.isInitialized) {
                    when (next.result) {
                        com.najim.android.auth.GatewayLoginResult.MATCHING_SESSION -> refreshInitialSession(false)
                        com.najim.android.auth.GatewayLoginResult.LEGACY_MATCHING_SESSION -> refreshInitialSession(true)
                        com.najim.android.auth.GatewayLoginResult.ALREADY_CONNECTED,
                        com.najim.android.auth.GatewayLoginResult.SESSION_UNVERIFIED -> refreshInitialSession(attemptHttpApproved)
                        else -> Unit
                    }
                }
                if (next is LoginAttemptState.Finished || next == LoginAttemptState.Cancelled)
                    attemptHttpApproved = false
                // Discovery may have changed while the old login worker was cancelling.
                // Once it drains, read only the current network, never replay credentials.
                if (next == LoginAttemptState.Cancelled && started && ::session.isInitialized)
                    refreshInitialSession()
            },
            saveConfirmed = { profiles.save(it) }
        )
        session = SessionRefreshController(loginWorker, { task -> main.post { task() } },
            transport::refreshStatus, { next ->
                val previous = sessionState.value
                sessionState.value = next
                if (next == SessionViewState.Loading || next == SessionViewState.Idle ||
                    next == SessionViewState.Cancelling || next == SessionViewState.LoggingOut)
                    statusDiagnostics.value = emptyList()
                if (previous != next) {
                    pendingReadAloud = null
                    speechPlayer.silence()
                    guidance.emit(SpeechPolicy.session(next))
                }
            }, transport::logout)
        discovery = WifiDiscovery(this) {
            attempts.networkChanged(it)
            session.networkChanged(it)
            val changed = discoveryState.value != it
            discoveryState.value = it
            if (changed && started && !startingDiscovery) {
                gallery.cancel()
                profiles.reload()
                refreshInitialSession()
            }
        }
        enableEdgeToEdge()
        setContent {
            NajimTheme(appearance.value) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    key(inputGeneration.value) {
                        FoundationScreen(discoveryState.value, {
                            val before = discoveryState.value
                            discovery.refresh()
                            if (before == discoveryState.value) refreshInitialSession()
                        }, attemptState.value,
                            lifecycleOwner = this@MainActivity,
                            appearance = appearance.value,
                            appearanceReady = appearanceReady.value,
                            appearanceSaving = appearanceSaving.value,
                            appearanceFailed = appearanceFailed.value,
                            changeAppearance = ::changeAppearance,
                            feedbackEnabled = feedbackEnabled.value,
                            feedbackReady = feedbackReady.value,
                            feedbackSaving = feedbackSaving.value,
                            feedbackFailed = feedbackFailed.value,
                            changeFeedback = ::changeFeedback,
                            speechEnabled = speechEnabled.value,
                            speechAvailable = speechAvailable.value,
                            speechSaving = speechSaving.value,
                            speechFailed = speechFailed.value,
                            changeSpeech = ::changeSpeech,
                            readSessionAloud = { expected ->
                                val active = expected.status as? com.najim.android.auth.SessionStatus.Active
                                if (resumed && sessionState.value === expected && active != null &&
                                    !session.busy) {
                                    tonePlayer.stop()
                                    if (!speechAvailable.value) {
                                        // Initialize on first explicit tap and read only this still-current snapshot.
                                        pendingReadAloud = null
                                        speechPlayer.stop()
                                        pendingReadAloud = expected
                                        speechPlayer.prepare()
                                        false
                                    } else speechPlayer.speakMetrics(active.account.metrics)
                                } else false
                            },
                            silenceAudio = { pendingReadAloud = null; tonePlayer.stop(); speechPlayer.silence() },
                            scanAccepted = { guidance.emit(SpeechCue.SCAN_ACCEPTED) },
                            profileState = profileState.value,
                            refreshProfiles = { profiles.refresh() },
                            createProfile = { name -> profiles.create(discoveryState.value, name) },
                            setRetention = { profile, mode ->
                                if (session.busy || attemptState.value == LoginAttemptState.Running ||
                                    attemptState.value == LoginAttemptState.Cancelling || galleryState.value != GalleryState.Idle) false
                                else profiles.setRetention(discoveryState.value, profile, mode)
                            },
                            loadCard = { profile, card, fill ->
                                if (session.busy || attemptState.value == LoginAttemptState.Running ||
                                    attemptState.value == LoginAttemptState.Cancelling || galleryState.value != GalleryState.Idle) false
                                else profiles.loadCard(discoveryState.value, profile, card, fill)
                            },
                            deleteCard = { profile, card ->
                                if (session.busy || attemptState.value == LoginAttemptState.Running ||
                                    attemptState.value == LoginAttemptState.Cancelling || galleryState.value != GalleryState.Idle) false
                                else profiles.deleteCard(discoveryState.value, profile, card)
                            },
                            sessionState = sessionState.value,
                            statusDiagnostics = statusDiagnostics.value,
                            refreshSession = { legacy, consent ->
                                if (attemptState.value == LoginAttemptState.Running ||
                                    attemptState.value == LoginAttemptState.Cancelling ||
                                    galleryState.value != GalleryState.Idle) false
                                else session.refresh(discoveryState.value, legacy, consent)
                            }, clearSession = session::invalidate,
                            logoutSession = { expected, consent ->
                                if (attemptState.value == LoginAttemptState.Running ||
                                    attemptState.value == LoginAttemptState.Cancelling ||
                                    galleryState.value != GalleryState.Idle) false
                                else session.logout(discoveryState.value, expected, consent)
                            },
                            galleryState = galleryState.value,
                            chooseImage = { mode ->
                                if (gallery.beginPicker(mode)) {
                                    try { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                                    catch (_: RuntimeException) { gallery.pickerResult(null) }
                                }
                            }, confirmImage = gallery::confirm, cancelImage = gallery::cancel,
                            startLogin = { user, password, mode, consent, saveChoice ->
                                if (session.busy || profiles.busy ||
                                    (saveChoice != null && !profiles.accepts(saveChoice.profile))) false
                                else {
                                    session.invalidate()
                                    attemptHttpApproved = mode.requiresConsent && consent
                                    attempts.start(discoveryState.value, user, password, mode, consent, saveChoice).also {
                                        if (!it) attemptHttpApproved = false
                                    }
                                }
                            }, cancelLogin = attempts::cancel)
                    }
                }
            }
        }
    }

    private fun changeAppearance(mode: AppearanceMode) {
        if (!appearanceReady.value || appearanceSaving.value || mode == appearance.value) return
        appearanceSaving.value = true
        appearanceFailed.value = false
        val main = Handler(Looper.getMainLooper())
        storageWorker.execute {
            val saved = runCatching { appearancePreferences.write(mode) }.getOrDefault(false)
            main.post {
                if (!isDestroyed) {
                    if (saved) appearance.value = mode
                    appearanceFailed.value = !saved
                    appearanceSaving.value = false
                }
            }
        }
    }

    private fun changeFeedback(enabled: Boolean) {
        if (!feedbackReady.value || feedbackSaving.value || speechSaving.value ||
            (enabled == feedbackEnabled.value && !feedbackFailed.value)) return
        feedbackSaving.value = true
        feedbackFailed.value = false
        if (!enabled) {
            audioAllowed = false
            syncGuidance()
        }
        val main = Handler(Looper.getMainLooper())
        storageWorker.execute {
            val saved = runCatching { appearancePreferences.writeFeedbackEnabled(enabled) }.getOrDefault(false)
            main.post {
                if (!isDestroyed) {
                    if (saved) feedbackEnabled.value = enabled
                    feedbackFailed.value = !saved
                    // Fail silent if disabling could not be persisted, until explicit retry.
                    audioAllowed = saved && enabled
                    syncGuidance()
                    feedbackSaving.value = false
                }
            }
        }
    }

    private fun syncGuidance() {
        val speech = speechEnabled.value && !speechFailed.value && !speechSaving.value
        guidance.configure(audioAllowed, speech)
        guidance.foreground(resumed)
        if (resumed && audioAllowed && speech) speechPlayer.prepare() else speechPlayer.stop()
        if (!resumed || !audioAllowed || speech) tonePlayer.stop()
    }

    private fun changeSpeech(enabled: Boolean) {
        if (!feedbackReady.value || !audioAllowed || feedbackSaving.value || speechSaving.value ||
            (enabled == speechEnabled.value && !speechFailed.value)) return
        speechSaving.value = true
        speechFailed.value = false
        syncGuidance()
        val main = Handler(Looper.getMainLooper())
        storageWorker.execute {
            val saved = runCatching { appearancePreferences.writeSpeechEnabled(enabled) }.getOrDefault(false)
            main.post {
                if (!isDestroyed) {
                    if (saved) speechEnabled.value = enabled
                    speechFailed.value = !saved
                    speechSaving.value = false
                    syncGuidance()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        syncGuidance()
    }

    override fun onPause() {
        resumed = false
        pendingReadAloud = null
        syncGuidance()
        super.onPause()
    }

    override fun onStart() {
        super.onStart()
        started = true
        startingDiscovery = true
        try { discovery.start() } finally { startingDiscovery = false }
        profiles.reload()
        refreshInitialSession()
    }

    private fun refreshInitialSession(legacy: Boolean? = null) {
        if (!started || !::session.isInitialized ||
            attemptState.value == LoginAttemptState.Running ||
            attemptState.value == LoginAttemptState.Cancelling ||
            galleryState.value != GalleryState.Idle) return
        if (legacy == null) session.refreshAutomatic(discoveryState.value)
        else session.refresh(discoveryState.value, legacy, legacy)
    }

    override fun onStop() {
        started = false
        attemptHttpApproved = false
        attempts.cancel()
        profiles.clear()
        session.invalidate()
        gallery.stop()
        inputGeneration.value += 1
        discovery.stop()
        super.onStop()
    }

    override fun onDestroy() {
        resumed = false
        syncGuidance()
        attempts.dispose()
        profiles.dispose()
        session.dispose()
        gallery.dispose()
        super.onDestroy()
    }

    companion object {
        // Serialize requests even across Activity recreation. Never interrupt an uncertain POST and retry it.
        private val loginWorker = java.util.concurrent.ThreadPoolExecutor(
            1, 1, 0L, java.util.concurrent.TimeUnit.MILLISECONDS,
            java.util.concurrent.ArrayBlockingQueue<Runnable>(1),
            java.util.concurrent.ThreadPoolExecutor.AbortPolicy())
        private val storageWorker = Executors.newSingleThreadExecutor()
        private val galleryWorker = java.util.concurrent.ThreadPoolExecutor(
            1, 1, 0L, java.util.concurrent.TimeUnit.MILLISECONDS,
            java.util.concurrent.ArrayBlockingQueue<Runnable>(1),
            java.util.concurrent.ThreadPoolExecutor.AbortPolicy())
    }
}
