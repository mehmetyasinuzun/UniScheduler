package com.unischeduler.presentation.calendar.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unischeduler.domain.model.ScheduleAssignment

@Composable
fun TimeSlotCell(
    assignment: ScheduleAssignment?,
    modifier: Modifier = Modifier,
    canEdit: Boolean,
    onToggleLock: (Int) -> Unit
) {
    val borderColor = MaterialTheme.colorScheme.outlineVariant

    if (assignment != null) {
        val bgColor = try {
            val hue = (assignment.courseCode.hashCode() and 0xFF) * 360f / 256f
            Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.3f, 0.95f)))
        } catch (_: Exception) {
            MaterialTheme.colorScheme.secondaryContainer
        }

        Box(
            modifier = modifier
                .border(0.5.dp, borderColor)
                .background(bgColor)
                .then(
                    if (canEdit) Modifier.clickable { onToggleLock(assignment.id) }
                    else Modifier
                )
                .padding(2.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(2.dp)
            ) {
                Text(
                    text = assignment.courseCode,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = assignment.courseName,
                    fontSize = 8.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = assignment.lecturerName,
                    fontSize = 7.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (assignment.classroom.isNotBlank()) {
                    Text(
                        text = assignment.classroom,
                        fontSize = 7.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (assignment.isLocked) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = "Kilitli",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(1.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    } else {
        Box(
            modifier = modifier
                .border(0.5.dp, borderColor)
                .background(MaterialTheme.colorScheme.surface)
        )
    }
}
