package com.unischeduler.presentation.calendar.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val cellWidth = 60.dp
    val cellHeight = 44.dp
    val timeColumnWidth = 56.dp
    val days = config.activeDays.sorted()
    val slotCount = config.totalSlotsPerDay
    val slots = remember(availability) {
        mutableStateListOf<AvailabilitySlot>().apply {
            addAll(buildFullSlotList(lecturerId, days, slotCount, availability))
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            Box(
                modifier = Modifier.width(timeColumnWidth).height(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Saat", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            days.forEach { day ->
                Box(
                    modifier = Modifier
                        .width(cellWidth)
                        .height(32.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(dayLabels[day] ?: "", fontSize = 11.sp)
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
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
                        Text(config.slotStartTime(slot), fontSize = 9.sp)
                    }
                    days.forEach { day ->
                        val idx = slots.indexOfFirst { it.dayOfWeek == day && it.slotIndex == slot }
                        val isAvailable = if (idx >= 0) slots[idx].isAvailable else true

                        val bgColor = if (isAvailable)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        else
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)

                        Box(
                            modifier = Modifier
                                .width(cellWidth)
                                .height(cellHeight)
                                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
                                .background(bgColor)
                                .clickable {
                                    if (idx >= 0) {
                                        slots[idx] = slots[idx].copy(isAvailable = !isAvailable)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isAvailable) "✓" else "✗",
                                fontSize = 14.sp,
                                color = if (isAvailable)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { onToggle(slots.toList()) },
            enabled = lecturerId > 0,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            Text(if (lecturerId > 0) "Müsaitlik Kaydet" else "Önce hoca profili eşleşmeli")
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
