package com.najim.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.najim.android.R
import com.najim.android.settings.AppearanceMode
import com.najim.android.storage.VaultLimits

@Composable
internal fun SettingsScreen(mode: AppearanceMode, ready: Boolean, saving: Boolean,
                            failed: Boolean, enabled: Boolean, change: (AppearanceMode) -> Unit,
                            feedbackEnabled: Boolean, feedbackReady: Boolean, feedbackSaving: Boolean,
                            feedbackFailed: Boolean, changeFeedback: (Boolean) -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.settings_appearance), Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleLarge)
            Column(Modifier.selectableGroup()) {
                for (item in AppearanceMode.entries) {
                    RadioChoice(stringResource(when (item) {
                        AppearanceMode.SYSTEM -> R.string.settings_system
                        AppearanceMode.LIGHT -> R.string.settings_light
                        AppearanceMode.DARK -> R.string.settings_dark
                    }), mode == item, ready && !saving && enabled) { change(item) }
                }
            }
            if (!ready || saving) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (failed) Text(stringResource(R.string.settings_save_failed),
                color = MaterialTheme.colorScheme.error)
        }
    }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.settings_accessibility), Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleLarge)
            CheckChoice(stringResource(R.string.settings_feedback), feedbackEnabled,
                feedbackReady && !feedbackSaving && enabled, changeFeedback)
            Text(stringResource(R.string.settings_feedback_info), style = MaterialTheme.typography.bodySmall)
            if (!feedbackReady || feedbackSaving) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (feedbackFailed) StatusNotice(stringResource(R.string.settings_feedback_failed))
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
