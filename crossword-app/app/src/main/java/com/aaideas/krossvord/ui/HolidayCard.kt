package com.aaideas.krossvord.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aaideas.krossvord.model.Holiday

/** Holiday greeting with a short fact; a button to the holiday pack while it is open. */
@Composable
fun HolidayCard(h: Holiday, vm: GameViewModel) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth().glassy(RoundedCornerShape(20.dp))) {
        Column(Modifier.padding(16.dp)) {
            Text("${h.icon} ${h.greeting}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("Bilirsinizmi? ${h.fact}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            val p = vm.packs.indexOfFirst { it.packId == h.packId }
            if (p >= 0 && vm.packOpen(p)) {
                TextButton(onClick = { vm.selectedPack = p }) { Text("Bayram paketi: ${vm.packs[p].title} ›") }
            }
        }
    }
}
