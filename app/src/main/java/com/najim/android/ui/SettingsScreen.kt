package com.najim.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.najim.android.R
import com.najim.android.settings.AppearanceMode
import com.najim.android.storage.VaultLimits

@Composable
internal fun SettingsScreen(mode: AppearanceMode, ready: Boolean, saving: Boolean,
                            failed: Boolean, enabled: Boolean, change: (AppearanceMode) -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.settings_appearance), style = MaterialTheme.typography.titleLarge)
            for (item in AppearanceMode.entries) {
                Row {
                    RadioButton(selected = mode == item, enabled = ready && !saving && enabled,
                        onClick = { change(item) })
                    Text(stringResource(when (item) {
                        AppearanceMode.SYSTEM -> R.string.settings_system
                        AppearanceMode.LIGHT -> R.string.settings_light
                        AppearanceMode.DARK -> R.string.settings_dark
                    }), Modifier.padding(top = 12.dp))
                }
            }
            if (!ready || saving) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (failed) Text(stringResource(R.string.settings_save_failed),
                color = MaterialTheme.colorScheme.error)
        }
    }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.settings_privacy), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.settings_storage_info, VaultLimits.MAX_RECORDS))
            Text(stringResource(R.string.settings_http_info))
            Text(stringResource(R.string.settings_scan_info))
        }
    }
}
