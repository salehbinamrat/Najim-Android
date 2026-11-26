package com.najim.android

import android.os.Bundle
import androidx.compose.runtime.mutableStateOf
import com.najim.android.network.WifiDiscovery
import com.najim.android.network.DiscoveryState
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.najim.android.ui.FoundationScreen

class MainActivity : ComponentActivity() {
    private val discoveryState = mutableStateOf<DiscoveryState>(DiscoveryState.Unavailable)
    private lateinit var discovery: WifiDiscovery

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        discovery = WifiDiscovery(this) { discoveryState.value = it }
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    FoundationScreen(discoveryState.value, discovery::refresh)
                }
            }
        }
    }
    override fun onStart() {
        super.onStart()
        discovery.start()
    }

    override fun onStop() {
        discovery.stop()
        super.onStop()
    }
}
