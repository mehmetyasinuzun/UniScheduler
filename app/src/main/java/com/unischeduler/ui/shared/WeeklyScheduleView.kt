package com.unischeduler.ui.shared

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.unischeduler.data.model.ScheduleEntry

data class ScheduleViewConfig(
    val dayStart: String = "08:00",
    val dayEnd: String = "18:00",
    val timeStepMinutes: Int = 60,
    val activeDays: List<String> = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday")
)

class WeeklyScheduleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var config = ScheduleViewConfig()
    private var entries: List<ScheduleEntry> = emptyList()
    private var entryClickListener: ((ScheduleEntry) -> Unit)? = null
    private var emptyCellClickListener: ((day: String, hour: Int) -> Unit)? = null

    private val cardRects = mutableListOf<Pair<RectF, ScheduleEntry>>()
    // onDraw allocation'larını engellemek için kullanılan reuse buffer'ları:
    private val reusableRect = RectF()
    private val parsedColorCache = HashMap<String, Int>()
    private val darkenedColorCache = HashMap<Int, Int>()
    // Layout sonucu setEntries'te bir kez hesaplanır; onDraw sadece okur.
    private var cachedLayout: Map<String, List<LayoutEntry>> = emptyMap()
    // Şimdi noktası için ayrı paint objesi (FILL) — nowLinePaint state mutation
    // edilmesin diye.
    private val nowDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val headerHeight = 48f.dp
    private val timeColumnWidth = 56f.dp
    private val cardCornerRadius = 8f.dp
    private val cardPadding = 3f.dp
    private val minCardHeight = 28f.dp

    private var scaleFactor = 1f
    private val minScale = 0.7f
    private val maxScale = 2.0f

    private val hourHeightBase = 72f.dp

    private val hourHeight: Float get() = hourHeightBase * scaleFactor

    private val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3F51B5")
        style = Paint.Style.FILL
    }

    private val headerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 12f.sp
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    private val timeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#616161")
        textSize = 11f.sp
        textAlign = Paint.Align.CENTER
    }

    private val gridLinePaint = Paint().apply {
        color = Color.parseColor("#E0E0E0")
        strokeWidth = 1f
        style = Paint.Style.STROKE
    }

    private val halfHourLinePaint = Paint().apply {
        color = Color.parseColor("#F0F0F0")
        strokeWidth = 0.5f
        style = Paint.Style.STROKE
    }

    private val cardTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 11f.sp
        typeface = Typeface.DEFAULT_BOLD
    }

    private val cardSubTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E0E0E0")
        textSize = 9.5f.sp
    }

    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }

    private val nowLinePaint = Paint().apply {
        color = Color.parseColor("#FF1744")
        strokeWidth = 2f.dp
        style = Paint.Style.STROKE
    }

    private val bgPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val courseColors = listOf(
        "#4CAF50", "#2196F3", "#FF9800", "#9C27B0",
        "#00BCD4", "#E91E63", "#795548", "#607D8B",
        "#F44336", "#3F51B5", "#009688", "#FF5722",
        "#673AB7", "#8BC34A", "#FFC107", "#03A9F4"
    )

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleFactor = (scaleFactor * detector.scaleFactor).coerceIn(minScale, maxScale)
            requestLayout()
            invalidate()
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            handleTap(e.x, e.y)
            return true
        }
    })

    fun setConfig(config: ScheduleViewConfig) {
        this.config = config
        cardRects.clear()
        requestLayout()
        invalidate()
    }

    fun setEntries(entries: List<ScheduleEntry>) {
        this.entries = entries
        cardRects.clear()
        cachedLayout = entries.groupBy { it.day }.mapValues { (_, dayEntries) ->
            layoutOverlapping(dayEntries)
        }
        requestLayout()
        invalidate()
    }

    fun setOnEntryClickListener(listener: (ScheduleEntry) -> Unit) {
        entryClickListener = listener
    }

    fun setOnEmptyCellClickListener(listener: (day: String, hour: Int) -> Unit) {
        emptyCellClickListener = listener
    }

    private val startMinutes: Int get() = toMinutes(config.dayStart)
    private val endMinutes: Int get() = toMinutes(config.dayEnd)
    private val totalMinutes: Int get() = endMinutes - startMinutes
    private val dayCount: Int get() = config.activeDays.size
    private val dayColumnWidth: Float get() {
        val available = width.toFloat() - timeColumnWidth
        return if (dayCount > 0) available / dayCount else available
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val hours = totalMinutes / 60f
        val h = (headerHeight + hours * hourHeight).toInt()
        setMeasuredDimension(w, h)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        cardRects.clear()

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        drawHeader(canvas)
        drawTimeColumn(canvas)
        drawGrid(canvas)
        drawEntries(canvas)
        drawNowLine(canvas)
    }

    private fun drawHeader(canvas: Canvas) {
        canvas.drawRect(0f, 0f, width.toFloat(), headerHeight, headerPaint)

        config.activeDays.forEachIndexed { i, day ->
            val x = timeColumnWidth + i * dayColumnWidth + dayColumnWidth / 2
            val y = headerHeight / 2 + headerTextPaint.textSize / 3
            canvas.drawText(abbreviateDay(day), x, y, headerTextPaint)
        }
    }

    private fun drawTimeColumn(canvas: Canvas) {
        val step = config.timeStepMinutes.coerceAtLeast(30)
        var minute = startMinutes
        while (minute <= endMinutes) {
            val y = minuteToY(minute)
            val label = formatTime(minute)
            canvas.drawText(label, timeColumnWidth / 2, y + timeTextPaint.textSize / 3, timeTextPaint)
            minute += step
        }
    }

    private fun drawGrid(canvas: Canvas) {
        val step = config.timeStepMinutes.coerceAtLeast(30)
        var minute = startMinutes

        while (minute <= endMinutes) {
            val y = minuteToY(minute)
            canvas.drawLine(timeColumnWidth, y, width.toFloat(), y, gridLinePaint)

            if (step >= 60 && minute + 30 <= endMinutes) {
                val halfY = minuteToY(minute + 30)
                canvas.drawLine(timeColumnWidth, halfY, width.toFloat(), halfY, halfHourLinePaint)
            }
            minute += step
        }

        for (i in 0..dayCount) {
            val x = timeColumnWidth + i * dayColumnWidth
            canvas.drawLine(x, headerHeight, x, height.toFloat(), gridLinePaint)
        }
    }

    private fun drawEntries(canvas: Canvas) {
        for ((dayIndex, dayName) in config.activeDays.withIndex()) {
            val layout = cachedLayout[dayName] ?: continue
            for ((entry, column, totalColumns) in layout) {
                drawEntryCard(canvas, entry, dayIndex, column, totalColumns)
            }
        }
    }

    private fun drawEntryCard(
        canvas: Canvas,
        entry: ScheduleEntry,
        dayIndex: Int,
        column: Int,
        totalColumns: Int
    ) {
        val entryStart = toMinutes(entry.startTime)
        val entryEnd = toMinutes(entry.endTime)
        if (entryStart >= entryEnd) return

        val colWidth = dayColumnWidth / totalColumns
        val left = timeColumnWidth + dayIndex * dayColumnWidth + column * colWidth + cardPadding
        val right = left + colWidth - cardPadding * 2
        val top = minuteToY(entryStart) + cardPadding
        val bottom = (minuteToY(entryEnd) - cardPadding).coerceAtLeast(top + minCardHeight)

        // Click hit-test için RectF tutuyoruz (her kart için tek allocation; layout
        // değişmediği sürece tekrar üretilmez). Çizimde reusableRect kullanılır.
        val rect = RectF(left, top, right, bottom)
        cardRects.add(rect to entry)
        reusableRect.set(left, top, right, bottom)

        val colorHex = getColorForEntry(entry)
        val color = parsedColorCache.getOrPut(colorHex) { Color.parseColor(colorHex) }
        cardPaint.color = color
        canvas.drawRoundRect(reusableRect, cardCornerRadius, cardCornerRadius, cardPaint)

        cardBorderPaint.color = darkenedColorCache.getOrPut(color) { darkenColor(color, 0.2f) }
        canvas.drawRoundRect(reusableRect, cardCornerRadius, cardCornerRadius, cardBorderPaint)

        val textLeft = left + 6f.dp
        val availableHeight = bottom - top
        val lineHeight = cardTextPaint.textSize * 1.3f

        if (availableHeight > lineHeight) {
            val courseText = ellipsize(entry.courseCode, cardTextPaint, right - textLeft - 4f.dp)
            canvas.drawText(courseText, textLeft, top + lineHeight, cardTextPaint)
        }

        if (availableHeight > lineHeight * 2) {
            val timeText = ellipsize(entry.timeRange, cardSubTextPaint, right - textLeft - 4f.dp)
            canvas.drawText(timeText, textLeft, top + lineHeight * 2, cardSubTextPaint)
        }

        if (availableHeight > lineHeight * 3) {
            val roomText = ellipsize(entry.classroomCode, cardSubTextPaint, right - textLeft - 4f.dp)
            canvas.drawText(roomText, textLeft, top + lineHeight * 3, cardSubTextPaint)
        }

        if (availableHeight > lineHeight * 4) {
            val lecturerText = ellipsize(entry.lecturerName, cardSubTextPaint, right - textLeft - 4f.dp)
            canvas.drawText(lecturerText, textLeft, top + lineHeight * 4, cardSubTextPaint)
        }
    }

    private fun drawNowLine(canvas: Canvas) {
        val cal = java.util.Calendar.getInstance()
        val nowMinutes = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)

        if (nowMinutes < startMinutes || nowMinutes > endMinutes) return

        val dayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK)
        val todayName = when (dayOfWeek) {
            java.util.Calendar.MONDAY -> "Monday"
            java.util.Calendar.TUESDAY -> "Tuesday"
            java.util.Calendar.WEDNESDAY -> "Wednesday"
            java.util.Calendar.THURSDAY -> "Thursday"
            java.util.Calendar.FRIDAY -> "Friday"
            else -> return
        }

        if (todayName !in config.activeDays) return

        val y = minuteToY(nowMinutes)
        canvas.drawLine(timeColumnWidth, y, width.toFloat(), y, nowLinePaint)

        // Ayrı FILL paint kullan — nowLinePaint'i çizim ortasında stylee'ini
        // değiştirmek HW renderer üzerinde sürpriz cache invalidasyonlarına
        // yol açabiliyordu.
        nowDotPaint.color = nowLinePaint.color
        canvas.drawCircle(timeColumnWidth, y, 4f.dp, nowDotPaint)
    }

    private fun handleTap(x: Float, y: Float) {
        for ((rect, entry) in cardRects.asReversed()) {
            if (rect.contains(x, y)) {
                entryClickListener?.invoke(entry)
                return
            }
        }

        if (x > timeColumnWidth && y > headerHeight) {
            val dayIndex = ((x - timeColumnWidth) / dayColumnWidth).toInt()
            if (dayIndex in config.activeDays.indices) {
                val minute = yToMinute(y)
                val hour = minute / 60
                emptyCellClickListener?.invoke(config.activeDays[dayIndex], hour)
            }
        }
    }

    private fun minuteToY(minute: Int): Float {
        val offset = minute - startMinutes
        return headerHeight + (offset.toFloat() / 60f) * hourHeight
    }

    private fun yToMinute(y: Float): Int {
        val offset = y - headerHeight
        return startMinutes + ((offset / hourHeight) * 60).toInt()
    }

    private data class LayoutEntry(
        val entry: ScheduleEntry,
        val column: Int,
        val totalColumns: Int
    )

    private fun layoutOverlapping(entries: List<ScheduleEntry>): List<LayoutEntry> {
        if (entries.isEmpty()) return emptyList()

        val sorted = entries.sortedWith(compareBy({ toMinutes(it.startTime) }, { toMinutes(it.endTime) }))
        val columns = mutableListOf<MutableList<ScheduleEntry>>()
        val entryColumns = mutableMapOf<Int, Int>()

        for (entry in sorted) {
            val entryStart = toMinutes(entry.startTime)
            var placed = false
            for ((colIdx, col) in columns.withIndex()) {
                val lastInCol = col.last()
                if (toMinutes(lastInCol.endTime) <= entryStart) {
                    col.add(entry)
                    entryColumns[entry.id] = colIdx
                    placed = true
                    break
                }
            }
            if (!placed) {
                columns.add(mutableListOf(entry))
                entryColumns[entry.id] = columns.size - 1
            }
        }

        val totalCols = columns.size
        return sorted.map { entry ->
            LayoutEntry(entry, entryColumns[entry.id] ?: 0, totalCols)
        }
    }

    private fun getColorForEntry(entry: ScheduleEntry): String {
        val key = entry.offerings?.courseId ?: entry.offeringId
        return courseColors[key.hashCode().and(0x7FFFFFFF) % courseColors.size]
    }

    private fun darkenColor(color: Int, factor: Float): Int {
        val r = ((Color.red(color) * (1 - factor)).toInt()).coerceIn(0, 255)
        val g = ((Color.green(color) * (1 - factor)).toInt()).coerceIn(0, 255)
        val b = ((Color.blue(color) * (1 - factor)).toInt()).coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    private fun abbreviateDay(day: String): String = when (day) {
        "Monday" -> "Pzt"
        "Tuesday" -> "Sal"
        "Wednesday" -> "Çar"
        "Thursday" -> "Per"
        "Friday" -> "Cum"
        "Saturday" -> "Cmt"
        "Sunday" -> "Paz"
        else -> day.take(3)
    }

    private fun formatTime(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return String.format(java.util.Locale.US, "%02d:%02d", h, m)
    }

    private fun toMinutes(time: String): Int {
        val parts = time.split(":")
        return (parts.getOrNull(0)?.toIntOrNull() ?: 0) * 60 +
               (parts.getOrNull(1)?.toIntOrNull() ?: 0)
    }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        for (i in text.length downTo 1) {
            val truncated = text.substring(0, i) + "…"
            if (paint.measureText(truncated) <= maxWidth) return truncated
        }
        return "…"
    }

    private val Float.dp: Float get() = this * resources.displayMetrics.density
    @Suppress("DEPRECATION")
    private val Float.sp: Float get() = this * resources.displayMetrics.scaledDensity
}
