// PdfExporter — renders a weekly schedule into a single-page A4-landscape PDF.
//
// Design notes:
//  • Uses android.graphics.pdf.PdfDocument (no external dependency, AOSP API
//    since API 19) instead of PrintManager. PrintManager is system-mediated
//    and forces a print preview UI, which is overkill when the user just
//    wants to "Save as PDF" or share via WhatsApp.
//  • The renderer redraws the schedule directly onto the page Canvas — we do
//    NOT bitmap-snapshot WeeklyScheduleView, because a snapshot would (a)
//    render at screen DPI (blurry on print) and (b) leak the on-screen
//    pinch-zoom factor into the export. Drawing fresh at PDF DPI gives crisp
//    text and predictable layout regardless of what the user did on screen.
//  • A4 landscape at 72 DPI = 842 × 595 pt — matches PDF's native point unit
//    so we don't have to do any unit conversion.
//  • Caller is responsible for opening the OutputStream (typically via
//    ActivityResultContracts.CreateDocument so the user picks the location).
package com.unischeduler.util

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.unischeduler.data.model.OrgSettings
import com.unischeduler.data.model.ScheduleEntry
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfExporter {

    // A4 landscape in PostScript points (the unit PdfDocument uses natively).
    private const val PAGE_WIDTH  = 842
    private const val PAGE_HEIGHT = 595
    private const val MARGIN      = 32f

    private const val HEADER_HEIGHT     = 52f
    private const val DAY_HEADER_HEIGHT = 28f
    private const val TIME_COL_WIDTH    = 56f

    /**
     * Write a one-page PDF of [entries] into [out].
     *
     * @param title        Header text, e.g. "Programım — Dr. Ayşe Yılmaz" or
     *                     "Bilgisayar Mühendisliği — Haftalık Program".
     * @param entries      Rows to render. Pre-filtered by caller (e.g. the
     *                     lecturer's own entries vs. department-wide).
     * @param settings     Drives the day window and active-day list. Falls
     *                     back to Mon–Fri 08:00–18:00 if missing.
     */
    fun exportSchedule(
        out: OutputStream,
        title: String,
        entries: List<ScheduleEntry>,
        settings: OrgSettings
    ) {
        val doc = PdfDocument()
        try {
            val pageInfo = PdfDocument.PageInfo
                .Builder(PAGE_WIDTH, PAGE_HEIGHT, 1)
                .create()
            val page = doc.startPage(pageInfo)
            drawPage(page.canvas, title, entries, settings)
            doc.finishPage(page)
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }

    // ── Drawing primitives ──────────────────────────────────────────────────

    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#212121")
        textSize = 18f
        typeface = Typeface.DEFAULT_BOLD
    }
    private val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#757575")
        textSize = 10f
    }
    private val dayHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3F51B5")
    }
    private val dayHeaderTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 11f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#616161")
        textSize = 9f
        textAlign = Paint.Align.CENTER
    }
    private val gridPaint = Paint().apply {
        color = Color.parseColor("#E0E0E0")
        strokeWidth = 0.5f
        style = Paint.Style.STROKE
    }
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 0.7f
    }
    private val cardTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 8.5f
        typeface = Typeface.DEFAULT_BOLD
    }
    private val cardSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F5F5F5")
        textSize = 7f
    }
    private val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9E9E9E")
        textSize = 8f
    }

    // Shared color palette mirrors WeeklyScheduleView so on-screen and
    // exported view look identical. Stable hash → same course always gets
    // the same color across exports.
    private val courseColors = listOf(
        "#4CAF50", "#2196F3", "#FF9800", "#9C27B0",
        "#00BCD4", "#E91E63", "#795548", "#607D8B",
        "#F44336", "#3F51B5", "#009688", "#FF5722",
        "#673AB7", "#8BC34A", "#FFC107", "#03A9F4"
    )

    private fun drawPage(
        canvas: Canvas,
        title: String,
        entries: List<ScheduleEntry>,
        settings: OrgSettings
    ) {
        val activeDays = settings.activeDays.ifEmpty {
            listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday")
        }
        val dayStart = toMinutes(settings.dayStart.ifBlank { "08:00" })
        val dayEnd   = toMinutes(settings.dayEnd.ifBlank   { "18:00" })

        // ── Header ──
        canvas.drawText(title, MARGIN, MARGIN + 6, titlePaint)
        val timestamp = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("tr", "TR"))
            .format(Date())
        canvas.drawText("Oluşturulma: $timestamp", MARGIN, MARGIN + 22, subtitlePaint)

        // ── Grid geometry ──
        val gridTop    = MARGIN + HEADER_HEIGHT
        val gridBottom = PAGE_HEIGHT - MARGIN - 16f  // leave room for footer
        val gridLeft   = MARGIN + TIME_COL_WIDTH
        val gridRight  = PAGE_WIDTH - MARGIN
        val dayWidth   = (gridRight - gridLeft) / activeDays.size.coerceAtLeast(1)

        // ── Day header row ──
        canvas.drawRect(gridLeft, gridTop, gridRight, gridTop + DAY_HEADER_HEIGHT, dayHeaderPaint)
        activeDays.forEachIndexed { i, day ->
            val cx = gridLeft + i * dayWidth + dayWidth / 2
            val cy = gridTop + DAY_HEADER_HEIGHT / 2 + 4
            canvas.drawText(abbreviateDay(day), cx, cy, dayHeaderTextPaint)
        }

        // ── Time column + horizontal grid lines ──
        val gridContentTop = gridTop + DAY_HEADER_HEIGHT
        val totalMin = (dayEnd - dayStart).coerceAtLeast(60)
        val pxPerMin = (gridBottom - gridContentTop) / totalMin

        var minute = dayStart
        while (minute <= dayEnd) {
            val y = gridContentTop + (minute - dayStart) * pxPerMin
            canvas.drawText(formatTime(minute), MARGIN + TIME_COL_WIDTH / 2, y + 3, timePaint)
            canvas.drawLine(gridLeft, y, gridRight, y, gridPaint)
            minute += 60
        }

        // ── Vertical day separators ──
        for (i in 0..activeDays.size) {
            val x = gridLeft + i * dayWidth
            canvas.drawLine(x, gridTop, x, gridBottom, gridPaint)
        }

        // ── Entries ──
        val grouped = entries.groupBy { it.day }
        for ((dayIdx, day) in activeDays.withIndex()) {
            val dayEntries = grouped[day] ?: continue
            val laidOut = layoutOverlapping(dayEntries)
            for (le in laidOut) {
                drawEntryCard(canvas, le, dayIdx, dayWidth, gridLeft, gridContentTop, pxPerMin, dayStart)
            }
        }

        // ── Footer ──
        canvas.drawText(
            "UniScheduler • ${entries.size} ders kaydı",
            MARGIN,
            PAGE_HEIGHT - 10f,
            footerPaint
        )
    }

    private data class LaidOutEntry(
        val entry: ScheduleEntry,
        val column: Int,
        val totalColumns: Int
    )

    private fun layoutOverlapping(entries: List<ScheduleEntry>): List<LaidOutEntry> {
        if (entries.isEmpty()) return emptyList()
        val sorted = entries.sortedWith(compareBy({ toMinutes(it.startTime) }, { toMinutes(it.endTime) }))
        val columns = mutableListOf<MutableList<ScheduleEntry>>()
        val map = HashMap<Int, Int>()
        for (e in sorted) {
            val s = toMinutes(e.startTime)
            var placed = false
            for ((idx, col) in columns.withIndex()) {
                if (toMinutes(col.last().endTime) <= s) {
                    col.add(e); map[e.id] = idx; placed = true; break
                }
            }
            if (!placed) { columns.add(mutableListOf(e)); map[e.id] = columns.size - 1 }
        }
        val total = columns.size
        return sorted.map { LaidOutEntry(it, map[it.id] ?: 0, total) }
    }

    private fun drawEntryCard(
        canvas: Canvas,
        e: LaidOutEntry,
        dayIndex: Int,
        dayWidth: Float,
        gridLeft: Float,
        gridContentTop: Float,
        pxPerMin: Float,
        dayStart: Int
    ) {
        val entry = e.entry
        val s = toMinutes(entry.startTime)
        val n = toMinutes(entry.endTime)
        if (s >= n) return

        val colWidth = dayWidth / e.totalColumns
        val left   = gridLeft + dayIndex * dayWidth + e.column * colWidth + 2f
        val right  = left + colWidth - 4f
        val top    = gridContentTop + (s - dayStart) * pxPerMin + 2f
        val bottom = (gridContentTop + (n - dayStart) * pxPerMin - 2f).coerceAtLeast(top + 12f)

        val rect = RectF(left, top, right, bottom)
        val color = colorForCourse(entry)
        cardPaint.color = color
        canvas.drawRoundRect(rect, 4f, 4f, cardPaint)
        cardBorderPaint.color = darken(color, 0.25f)
        canvas.drawRoundRect(rect, 4f, 4f, cardBorderPaint)

        val textLeft = left + 4f
        val textWidth = right - textLeft - 4f
        val courseLine = ellipsize("${entry.courseCode} ${entry.courseName}".trim(), cardTitlePaint, textWidth)
        canvas.drawText(courseLine, textLeft, top + 11, cardTitlePaint)

        if (bottom - top > 22) {
            val timeLine = ellipsize(entry.timeRange, cardSubPaint, textWidth)
            canvas.drawText(timeLine, textLeft, top + 21, cardSubPaint)
        }
        if (bottom - top > 32) {
            val roomLine = ellipsize(entry.classroomCode, cardSubPaint, textWidth)
            canvas.drawText(roomLine, textLeft, top + 30, cardSubPaint)
        }
        if (bottom - top > 42) {
            val lectLine = ellipsize(entry.lecturerName, cardSubPaint, textWidth)
            canvas.drawText(lectLine, textLeft, top + 39, cardSubPaint)
        }
    }

    private fun colorForCourse(entry: ScheduleEntry): Int {
        val key = entry.offerings?.courseId ?: entry.offeringId
        val hex = courseColors[(key.hashCode() and 0x7FFFFFFF) % courseColors.size]
        return Color.parseColor(hex)
    }

    private fun darken(color: Int, factor: Float): Int {
        val r = ((Color.red(color)   * (1 - factor)).toInt()).coerceIn(0, 255)
        val g = ((Color.green(color) * (1 - factor)).toInt()).coerceIn(0, 255)
        val b = ((Color.blue(color)  * (1 - factor)).toInt()).coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        for (i in text.length downTo 1) {
            val t = text.substring(0, i) + "…"
            if (paint.measureText(t) <= maxWidth) return t
        }
        return "…"
    }

    private fun abbreviateDay(day: String): String = when (day) {
        "Monday"    -> "Pzt"
        "Tuesday"   -> "Sal"
        "Wednesday" -> "Çar"
        "Thursday"  -> "Per"
        "Friday"    -> "Cum"
        "Saturday"  -> "Cmt"
        "Sunday"    -> "Paz"
        else        -> day.take(3)
    }

    private fun toMinutes(time: String): Int {
        val parts = time.split(":")
        return (parts.getOrNull(0)?.toIntOrNull() ?: 0) * 60 +
               (parts.getOrNull(1)?.toIntOrNull() ?: 0)
    }

    private fun formatTime(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return "%02d:%02d".format(h, m)
    }
}
