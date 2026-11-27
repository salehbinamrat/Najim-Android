package com.najim.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.najim.android.R

internal enum class NajimSection(val label: Int) {
    HOME(R.string.nav_home), LOGIN(R.string.nav_login), CARDS(R.string.nav_cards)
}

/** Stable three-section shell. No new navigation dependency or retained secret back stack. */
@Composable
internal fun NajimNavigation(section: NajimSection, enabled: Boolean, select: (NajimSection) -> Unit) {
    Surface(tonalElevation = 3.dp) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (item in NajimSection.entries) {
                val active = section == item
                TextButton(onClick = { select(item) }, enabled = enabled && !active,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { selected = active },
                    colors = ButtonDefaults.textButtonColors(
                        containerColor = if (active) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        disabledContainerColor = if (active) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
                        disabledContentColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    ), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)) {
                    Text(stringResource(item.label), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
