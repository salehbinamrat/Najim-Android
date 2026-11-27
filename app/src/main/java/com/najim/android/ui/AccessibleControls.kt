package com.najim.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.najim.android.R

@Composable
internal fun RadioChoice(label: String, selected: Boolean, enabled: Boolean, choose: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
        .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = choose)
        .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        RadioButton(selected = selected, enabled = enabled, onClick = null)
        Text(label, Modifier.weight(1f))
    }
}

@Composable
internal fun CheckChoice(label: String, checked: Boolean, enabled: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
        .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = change)
        .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Checkbox(checked = checked, enabled = enabled, onCheckedChange = null)
        Text(label, Modifier.weight(1f))
    }
}

@Composable
internal fun StatusNotice(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.semantics { liveRegion = LiveRegionMode.Polite })
}

/** Visual review remains possible, but accessibility semantics expose no raw scanned value. */
@Composable
internal fun PrivateScanText(value: String) {
    val label = stringResource(R.string.scan_private_candidate)
    Text(value, Modifier.clearAndSetSemantics { contentDescription = label },
        style = MaterialTheme.typography.headlineSmall)
}
