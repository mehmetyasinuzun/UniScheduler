// CalendarRenderer — builds a Mon–Fri × time-range grid from live schedule entries.
// Rows are derived from the actual entries in the data (free-time scheduling).
// Empty grid is shown when there are no entries yet.
package com.unischeduler.ui.lecturer

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.TextView
import androidx.gridlayout.widget.GridLayout
import com.unischeduler.data.model.DAYS
import com.unischeduler.data.model.ScheduleEntry

object CalendarRenderer {

    fun render(
        grid: GridLayout,
        entries: List<ScheduleEntry>,
        onCellClick: ((day: String, timeSlot: String, entries: List<ScheduleEntry>) -> Unit)? = null
    ) {
        grid.removeAllViews()
        val ctx = grid.context

        // Collect distinct, sorted time-ranges from the actual entries
        val timeSlots = entries
            .map { it.timeRange }
            .filter { it.isNotBlank() }
            .distinct()
            .sortedWith(compareBy { it.substringBefore("-") })

        if (timeSlots.isEmpty()) {
            val tv = TextView(ctx).apply {
                text = "No schedule entries yet."
                setPadding(16, 24, 16, 24)
                gravity = Gravity.CENTER
            }
            grid.columnCount = 1
            grid.rowCount    = 1
            val params = GridLayout.LayoutParams(
                GridLayout.spec(0), GridLayout.spec(0)
            )
            grid.addView(tv, params)
            return
        }

        grid.columnCount = DAYS.size + 1
        grid.rowCount    = timeSlots.size + 1

        val grouped = entries.groupBy { it.day to it.timeRange }

        // Header row: empty corner + day abbreviations
        addCell(grid, ctx, "", 0, 0, isHeader = true)
        DAYS.forEachIndexed { col, day ->
            addCell(grid, ctx, day.take(3), 0, col + 1, isHeader = true)
        }

        // One row per distinct time range
        timeSlots.forEachIndexed { row, slot ->
            addCell(grid, ctx, slot, row + 1, 0, isHeader = true)
            DAYS.forEachIndexed { col, day ->
                val slotEntries = grouped[day to slot].orEmpty()
                addCell(
                    grid, ctx,
                    buildCellText(slotEntries),
                    row + 1, col + 1,
                    occupied = slotEntries.isNotEmpty(),
                    onClick = if (slotEntries.isNotEmpty() && onCellClick != null) {
                        { onCellClick(day, slot, slotEntries) }
                    } else null
                )
            }
        }
    }

    private fun buildCellText(entries: List<ScheduleEntry>): String {
        if (entries.isEmpty()) return ""
        val maxVisible = 2
        val lines = entries.take(maxVisible)
            .map { "${it.courseCode}\n${it.classroomCode}" }
            .toMutableList()
        val remaining = entries.size - maxVisible
        if (remaining > 0) lines.add("+$remaining")
        return lines.joinToString("\n")
    }

    private fun addCell(
        grid: GridLayout,
        ctx: Context,
        text: String,
        row: Int,
        col: Int,
        isHeader: Boolean = false,
        occupied: Boolean = false,
        onClick: (() -> Unit)? = null
    ) {
        val tv = TextView(ctx).apply {
            this.text = text
            gravity   = Gravity.CENTER
            setPadding(6, 6, 6, 6)
            textSize  = if (isHeader) 10f else 9f
            if (isHeader) setTypeface(null, android.graphics.Typeface.BOLD)
            setBackgroundColor(
                when {
                    isHeader -> Color.parseColor("#E8EAF6")
                    occupied -> Color.parseColor("#C8E6C9")
                    else     -> Color.parseColor("#F5F5F5")
                }
            )
            if (onClick != null) {
                isClickable = true
                setOnClickListener { onClick() }
            }
        }

        val params = GridLayout.LayoutParams(
            GridLayout.spec(row, 1, 1f),
            GridLayout.spec(col, 1, 1f)
        ).apply {
            width  = 0
            height = GridLayout.LayoutParams.WRAP_CONTENT
            setMargins(1, 1, 1, 1)
        }
        grid.addView(tv, params)
    }
}
