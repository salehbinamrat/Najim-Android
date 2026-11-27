package com.najim.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleOwner
import com.najim.android.R
import com.najim.android.ocr.GalleryState
import com.najim.android.ocr.ScanMode

@Composable
internal fun ScanPanel(owner: LifecycleOwner, mode: ScanMode, selectMode: (ScanMode) -> Unit,
                       enabled: Boolean, galleryState: GalleryState,
                       chooseImage: (ScanMode) -> Unit, confirmImage: () -> String?,
                       cancelImage: () -> Unit, onCode: (String) -> Unit) {
    val galleryActive = galleryState != GalleryState.Idle
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.scan_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.scan_local_hint), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.scan_mode), style = MaterialTheme.typography.titleMedium)
            Column(Modifier.selectableGroup()) {
                for (item in ScanMode.entries) {
                    RadioChoice(stringResource(if (item == ScanMode.OCR) R.string.scan_mode_ocr else R.string.scan_mode_qr),
                        mode == item, enabled && !galleryActive) { selectMode(item) }
                }
            }
            key(mode) {
                VoucherScanner(owner, mode, enabled = enabled && !galleryActive, onConfirmed = onCode)
            }
            GalleryScanner(galleryState, enabled = enabled, choose = { chooseImage(mode) },
                confirm = { confirmImage()?.let(onCode) }, cancel = cancelImage)
        }
    }
}
