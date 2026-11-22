package com.aaideas.krossvord.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Settings block: follow the phone, always light, or always dark (easier on the eyes at night). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemePicker(current: ThemeMode, onPick: (ThemeMode) -> Unit, dynamic: Boolean, onDynamic: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Görünüş", style = MaterialTheme.typography.headlineSmall)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            ThemeMode.entries.forEachIndexed { i, mode ->
                SegmentedButton(
                    selected = current == mode,
                    onClick = { onPick(mode) },
                    shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                ) { Text(mode.label) }
            }
        }
        Text(
            "«Sistem» telefonun ayarına uyğun dəyişir. «Qaranlıq» axşam gözləri yormur.",
            style = MaterialTheme.typography.bodySmall,
        )
        if (supportsDynamicColor) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Telefonun rəngləri")
                    Text("Rənglər divar kağızından götürülür", style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = dynamic, onCheckedChange = onDynamic)
            }
        }
    }
}
