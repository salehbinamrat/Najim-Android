package com.najim.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.najim.android.R
import com.najim.android.auth.LoginProtocol
import com.najim.android.storage.NetworkProfile
import com.najim.android.storage.SavedVoucher

@Composable
internal fun SavedVoucherCard(profile: NetworkProfile, cards: List<SavedVoucher>,
                              enabled: Boolean, load: (SavedVoucher) -> Boolean,
                              delete: (SavedVoucher) -> Boolean) {
    var pendingLoad by remember(profile, cards, enabled) { mutableStateOf<SavedVoucher?>(null) }
    var pendingDelete by remember(profile, cards, enabled) { mutableStateOf<SavedVoucher?>(null) }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.saved_title), style = MaterialTheme.typography.titleMedium)
            Text(profile.name)
            if (cards.isEmpty()) Text(stringResource(R.string.saved_empty))
            for ((index, card) in cards.withIndex()) {
                key(card.id) {
                    HorizontalDivider()
                    Text(stringResource(R.string.saved_index, index + 1))
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Text(card.maskedAccount, style = MaterialTheme.typography.titleMedium)
                    }
                    Text(stringResource(when (card.protocol) {
                        LoginProtocol.HTTPS_PAP -> R.string.login_https
                        LoginProtocol.HTTP_PAP -> R.string.login_http_pap
                        LoginProtocol.HTTP_CHAP -> R.string.login_http_chap
                    }))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { pendingLoad = card }, enabled = enabled,
                            modifier = Modifier.weight(1f)) { Text(stringResource(R.string.saved_use)) }
                        OutlinedButton(onClick = { pendingDelete = card }, enabled = enabled,
                            modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.saved_delete), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
    val toLoad = pendingLoad
    if (enabled && toLoad != null && cards.any { it === toLoad }) {
        AlertDialog(onDismissRequest = { pendingLoad = null },
            title = { Text(stringResource(R.string.saved_use)) },
            text = { Text(stringResource(R.string.saved_use_confirm, profile.name, toLoad.maskedAccount)) },
            confirmButton = { TextButton(onClick = { pendingLoad = null; load(toLoad) }) {
                Text(stringResource(R.string.saved_use))
            } },
            dismissButton = { TextButton(onClick = { pendingLoad = null }) { Text(stringResource(R.string.logout_back)) } })
    }
    val toDelete = pendingDelete
    if (enabled && toDelete != null && cards.any { it === toDelete }) {
        AlertDialog(onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.saved_delete)) },
            text = { Text(stringResource(R.string.saved_delete_confirm, profile.name, toDelete.maskedAccount)) },
            confirmButton = { TextButton(onClick = { pendingDelete = null; delete(toDelete) }) {
                Text(stringResource(R.string.saved_delete), color = MaterialTheme.colorScheme.error)
            } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.logout_back)) } })
    }
}
