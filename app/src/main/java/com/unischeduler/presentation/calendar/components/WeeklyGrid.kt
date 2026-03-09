package com.unischeduler.presentation.calendar.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unischeduler.domain.model.ScheduleAssignment
import com.unischeduler.domain.model.ScheduleConfig

private val dayLabels = mapOf(
    1 to "Pzt", 2 to "Sal", 3 to "Çar",
    4 to "Per", 5 to "Cum", 6 to "Cmt", 7 to "Paz"
)

@Composable
fun WeeklyGrid(
    assignments: List<ScheduleAssignment>,
    config: ScheduleConfig,
    canEdit: Boolean,
    onToggleLock: (Int) -> Unit
) {
    val cellWidth = 110.dp
    val cellHeight = 64.dp
    val timeColumnWidth = 56.dp
    val days = config.activeDays.sorted()
    val slotCount = config.totalSlotsPerDay

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        // Header Row
        Row {
            Box(
                modifier = Modifier
                    .width(timeColumnWidth)
                    .height(36.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Saat", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            days.forEach { day ->
                Box(
                    modifier = Modifier
                        .width(cellWidth)
                        .height(36.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = dayLabels[day] ?: "$day",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        // Slot Rows
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
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                days.forEach { day ->
                    val cellAssignment = assignments.find { it.dayOfWeek == day && it.slotIndex == slot }

                    TimeSlotCell(
                        assignment = cellAssignment,
                        modifier = Modifier
                            .width(cellWidth)
                            .height(cellHeight),
                        canEdit = canEdit,
                        onToggleLock = onToggleLock
                    )
                }
            }
        }
    }
}
