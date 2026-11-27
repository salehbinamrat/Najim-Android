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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private val discoveryState = mutableStateOf<DiscoveryState>(DiscoveryState.Unavailable)
    private val attemptState = mutableStateOf<LoginAttemptState>(LoginAttemptState.Idle)
    private val inputGeneration = mutableStateOf(0)
    private lateinit var discovery: WifiDiscovery
    private lateinit var attempts: LoginAttemptController
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
        attempts = LoginAttemptController(
            worker = loginWorker,
            dispatch = { task -> main.post { task() } },
            login = transport::login,
            publish = { attemptState.value = it }
        )
        session = SessionRefreshController(loginWorker, { task -> main.post { task() } },
            transport::refreshStatus, { sessionState.value = it })
        discovery = WifiDiscovery(this) {
            attempts.networkChanged(it)
            session.networkChanged(it)
            discoveryState.value = it
        }
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    key(inputGeneration.value) {
                        FoundationScreen(discoveryState.value, discovery::refresh, attemptState.value,
                            lifecycleOwner = this@MainActivity,
                            sessionState = sessionState.value,
                            refreshSession = { legacy, consent ->
                                if (attemptState.value == LoginAttemptState.Running ||
                                    attemptState.value == LoginAttemptState.Cancelling ||
                                    galleryState.value != GalleryState.Idle) false
                                else session.refresh(discoveryState.value, legacy, consent)
                            }, clearSession = session::invalidate,
                            galleryState = galleryState.value,
                            chooseImage = { mode ->
                                if (gallery.beginPicker(mode)) {
                                    try { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                                    catch (_: RuntimeException) { gallery.pickerResult(null) }
                                }
                            }, confirmImage = gallery::confirm, cancelImage = gallery::cancel,
                            startLogin = { user, password, mode, consent ->
                                if (session.busy) false
                                else {
                                    session.invalidate()
                                    attempts.start(discoveryState.value, user, password, mode, consent)
                                }
                            }, cancelLogin = attempts::cancel)
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        discovery.start()
    }

    override fun onStop() {
        attempts.cancel()
        session.invalidate()
        gallery.stop()
        inputGeneration.value += 1
        discovery.stop()
        super.onStop()
    }

    override fun onDestroy() {
        attempts.dispose()
        session.dispose()
        gallery.dispose()
        super.onDestroy()
    }

    companion object {
        // Serialize requests even across Activity recreation. Never interrupt an uncertain POST and retry it.
        private val loginWorker = Executors.newSingleThreadExecutor()
        private val galleryWorker = Executors.newSingleThreadExecutor()
    }
}
