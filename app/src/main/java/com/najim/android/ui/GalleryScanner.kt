package com.najim.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.najim.android.R
import com.najim.android.ocr.GalleryState

@Composable
internal fun GalleryScanner(state: GalleryState, enabled: Boolean, choose: () -> Unit,
                            confirm: () -> Unit, cancel: () -> Unit) {
    if (state == GalleryState.Idle) {
        OutlinedButton(onClick = choose, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.gallery_open))
        }
        return
    }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.gallery_title), style = MaterialTheme.typography.titleMedium)
            when (state) {
                GalleryState.Picking -> Text(stringResource(R.string.gallery_picking))
                GalleryState.Loading -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(stringResource(R.string.gallery_loading))
                }
                GalleryState.Failed -> Text(stringResource(R.string.gallery_failed))
                GalleryState.NoNumber -> Text(stringResource(R.string.gallery_no_number))
                GalleryState.Ambiguous -> Text(stringResource(R.string.scan_ambiguous))
                GalleryState.Unsupported -> Text(stringResource(R.string.qr_unsupported))
                is GalleryState.Candidate -> {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        PrivateScanText(state.value)
                    }
                    Text(stringResource(R.string.scan_confirm_hint))
                    Button(onClick = confirm, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.scan_use))
                    }
                }
                GalleryState.Idle -> Unit
            }
            if (state != GalleryState.Picking && state != GalleryState.Loading) {
                OutlinedButton(onClick = choose, enabled = enabled) { Text(stringResource(R.string.gallery_open)) }
            }
            OutlinedButton(onClick = cancel, enabled = state != GalleryState.Picking) {
                Text(stringResource(R.string.gallery_close))
            }
        }
    }
}
