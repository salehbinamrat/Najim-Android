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
import com.najim.android.feedback.FeedbackGate
import com.najim.android.feedback.FeedbackPolicy
import com.najim.android.feedback.FeedbackCue

class MainActivity : ComponentActivity() {
    private val tonePlayer = AndroidTonePlayer()
    private val feedback = FeedbackGate(tonePlayer::play, tonePlayer::stop)
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
    private lateinit var gallery: GalleryImport
    private val galleryState = mutableStateOf<GalleryState>(GalleryState.Idle)
    private val picker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (::gallery.isInitialized) gallery.pickerResult(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        gallery = GalleryImport(applicationContext.contentResolver, galleryWorker) { galleryState.value = it }
        val transport = AndroidGatewayTransport(applicationContext)
        val main = Handler(Looper.getMainLooper())
        appearancePreferences = AppearancePreferences(applicationContext)
        storageWorker.execute {
            val loaded = runCatching { appearancePreferences.read() }
            val audio = runCatching { appearancePreferences.readFeedbackEnabled() }
            main.post {
                if (!isDestroyed) {
                    appearance.value = loaded.getOrDefault(AppearanceMode.SYSTEM)
                    appearanceFailed.value = loaded.isFailure
                    appearanceReady.value = true
                    feedbackEnabled.value = audio.getOrDefault(false)
                    feedbackFailed.value = audio.isFailure
                    feedbackReady.value = true
                    feedback.setEnabled(feedbackEnabled.value)
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
                if (previous != next) feedback.emit(FeedbackPolicy.login(next))
            },
            saveConfirmed = { profiles.save(it) }
        )
        session = SessionRefreshController(loginWorker, { task -> main.post { task() } },
            transport::refreshStatus, { next ->
                val previous = sessionState.value
                sessionState.value = next
                if (previous != next) feedback.emit(FeedbackPolicy.session(next))
            }, transport::logout)
        discovery = WifiDiscovery(this) {
            attempts.networkChanged(it)
            session.networkChanged(it)
            if (discoveryState.value != it) {
                profiles.clear()
                profiles.refresh()
            }
            discoveryState.value = it
        }
        enableEdgeToEdge()
        setContent {
            NajimTheme(appearance.value) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    key(inputGeneration.value) {
                        FoundationScreen(discoveryState.value, discovery::refresh, attemptState.value,
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
                            scanAccepted = { feedback.emit(FeedbackCue.SCAN_ACCEPTED) },
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
                                    attempts.start(discoveryState.value, user, password, mode, consent, saveChoice)
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
        if (!feedbackReady.value || feedbackSaving.value || enabled == feedbackEnabled.value) return
        feedbackSaving.value = true
        feedbackFailed.value = false
        if (!enabled) feedback.setEnabled(false)
        val main = Handler(Looper.getMainLooper())
        storageWorker.execute {
            val saved = runCatching { appearancePreferences.writeFeedbackEnabled(enabled) }.getOrDefault(false)
            main.post {
                if (!isDestroyed) {
                    if (saved) feedbackEnabled.value = enabled
                    feedbackFailed.value = !saved
                    // Fail silent if disabling could not be persisted, until explicit retry.
                    feedback.setEnabled(saved && enabled)
                    feedbackSaving.value = false
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        feedback.resume()
    }

    override fun onPause() {
        feedback.pause()
        super.onPause()
    }

    override fun onStart() {
        super.onStart()
        discovery.start()
        profiles.refresh()
    }

    override fun onStop() {
        attempts.cancel()
        profiles.clear()
        session.invalidate()
        gallery.stop()
        inputGeneration.value += 1
        discovery.stop()
        super.onStop()
    }

    override fun onDestroy() {
        feedback.pause()
        attempts.dispose()
        profiles.dispose()
        session.dispose()
        gallery.dispose()
        super.onDestroy()
    }

    companion object {
        // Serialize requests even across Activity recreation. Never interrupt an uncertain POST and retry it.
        private val loginWorker = Executors.newSingleThreadExecutor()
        private val storageWorker = Executors.newSingleThreadExecutor()
        private val galleryWorker = Executors.newSingleThreadExecutor()
    }
}
