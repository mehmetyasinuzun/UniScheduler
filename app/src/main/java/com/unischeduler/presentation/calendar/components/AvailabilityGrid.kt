package com.unischeduler.presentation.calendar.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unischeduler.domain.model.AvailabilitySlot
import com.unischeduler.domain.model.ScheduleConfig

private val dayLabels = mapOf(
    1 to "Pzt", 2 to "Sal", 3 to "Çar",
    4 to "Per", 5 to "Cum", 6 to "Cmt", 7 to "Paz"
)

@Composable
fun AvailabilityGrid(
    availability: List<AvailabilitySlot>,
    lecturerId: Int,
    config: ScheduleConfig,
    onToggle: (List<AvailabilitySlot>) -> Unit
) {
    val cellWidth = 72.dp
    val cellHeight = 52.dp
    val timeColumnWidth = 78.dp
    val days = config.activeDays.sorted()
    val slotCount = config.totalSlotsPerDay
    var saveMessage by remember { mutableStateOf<String?>(null) }
    val slots = remember(availability, lecturerId, days, slotCount) {
        mutableStateListOf<AvailabilitySlot>().apply {
            addAll(buildFullSlotList(lecturerId, days, slotCount, availability))
        }
    }

    val totalCells = slots.size
    val availableCount = slots.count { it.isAvailable }
    val busyCount = totalCells - availableCount

    fun setAllAvailability(value: Boolean) {
        for (i in slots.indices) {
            val current = slots[i]
            if (current.isAvailable != value) {
                slots[i] = current.copy(isAvailable = value)
            }
        }
    }

    LaunchedEffect(availableCount, busyCount) {
        if (saveMessage != null) saveMessage = null
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                Text(
                    text = "Haftalik Musaitlik Plani",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Dokunarak saat hucrelerini Uygun/Meşgul olarak degistirebilirsiniz.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatChip(
                        title = "Uygun",
                        value = availableCount,
                        bg = MaterialTheme.colorScheme.primaryContainer,
                        fg = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    StatChip(
                        title = "Mesgul",
                        value = busyCount,
                        bg = MaterialTheme.colorScheme.errorContainer,
                        fg = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    StatChip(
                        title = "Toplam",
                        value = totalCells,
                        bg = MaterialTheme.colorScheme.secondaryContainer,
                        fg = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { setAllAvailability(true) },
                enabled = lecturerId > 0,
                modifier = Modifier.weight(1f)
            ) {
                Text("Tumunu Uygun")
            }
            OutlinedButton(
                onClick = { setAllAvailability(false) },
                enabled = lecturerId > 0,
                modifier = Modifier.weight(1f)
            ) {
                Text("Tumunu Mesgul")
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                .padding(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(timeColumnWidth)
                    .height(36.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Saat", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
            }
            days.forEach { day ->
                Box(
                    modifier = Modifier
                        .width(cellWidth)
                        .height(36.dp)
                        .padding(horizontal = 2.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(dayLabels[day] ?: "", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                .padding(6.dp)
        ) {
            for (slot in 0 until slotCount) {
                Row {
                    Box(
                        modifier = Modifier
                            .width(timeColumnWidth)
                            .height(cellHeight)
                            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = config.slotStartTime(slot),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    days.forEach { day ->
                        val idx = slots.indexOfFirst { it.dayOfWeek == day && it.slotIndex == slot }
                        val isAvailable = if (idx >= 0) slots[idx].isAvailable else true

                        val bgColor = if (isAvailable)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        else
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.58f)

                        val textColor = if (isAvailable) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        }

                        Box(
                            modifier = Modifier
                                .width(cellWidth)
                                .height(cellHeight)
                                .padding(horizontal = 2.dp, vertical = 2.dp)
                                .clip(MaterialTheme.shapes.small)
                                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                .background(bgColor)
                                .clickable {
                                    if (idx >= 0) {
                                        slots[idx] = slots[idx].copy(isAvailable = !isAvailable)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = if (isAvailable) "Uygun" else "Mesgul",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = textColor
                                )
                                Text(
                                    text = if (isAvailable) "✓" else "✗",
                                    fontSize = 13.sp,
                                    color = textColor
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (saveMessage != null) {
            Text(
                text = saveMessage!!,
                color = Color(0xFF2E7D32),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        Button(
            onClick = {
                onToggle(slots.toList())
                saveMessage = "Musaitlik plani kaydetme istegi gonderildi"
            },
            enabled = lecturerId > 0,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            Text(if (lecturerId > 0) "Musaitlik Planini Kaydet" else "Once hoca profili eslesmeli")
        }
    }
}

@Composable
private fun StatChip(
    title: String,
    value: Int,
    bg: Color,
    fg: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = bg),
        border = BorderStroke(1.dp, fg.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp)) {
            Text(
                text = value.toString(),
                fontWeight = FontWeight.Bold,
                color = fg,
                fontSize = 18.sp
            )
            Text(
                text = title,
                color = fg.copy(alpha = 0.92f),
                fontSize = 11.sp
            )
        }
    }
}

private fun buildFullSlotList(
    lecturerId: Int,
    days: List<Int>,
    slotCount: Int,
    existing: List<AvailabilitySlot>
): List<AvailabilitySlot> {
    val map = existing.associateBy { "${it.dayOfWeek}-${it.slotIndex}" }
    return buildList {
        for (day in days) {
            for (slot in 0 until slotCount) {
                val key = "$day-$slot"
                add(map[key] ?: AvailabilitySlot(
                    lecturerId = lecturerId,
                    dayOfWeek = day,
                    slotIndex = slot,
                    isAvailable = true
                ))
            }
        }
    }
}
