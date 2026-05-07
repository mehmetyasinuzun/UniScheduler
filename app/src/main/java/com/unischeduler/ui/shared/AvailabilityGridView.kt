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
import android.view.View
import com.unischeduler.data.model.LecturerAvailability
import com.unischeduler.data.model.ScheduleEntry

data class AvailabilityGridConfig(
    val dayStart: String = "08:00",
    val dayEnd: String = "18:00",
    val timeStepMinutes: Int = 60,
    val activeDays: List<String> = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday")
)

class AvailabilityGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var config = AvailabilityGridConfig()
    private var busySlots: List<LecturerAvailability> = emptyList()
    private var scheduleEntries: List<ScheduleEntry> = emptyList()
    private var busyClickListener: ((LecturerAvailability) -> Unit)? = null
    private var emptyCellClickListener: ((day: String, startTime: String, endTime: String) -> Unit)? = null

    private val slotRects = mutableListOf<Pair<RectF, Any>>()

    private val headerHeight = 48f.dp
    private val timeColumnWidth = 56f.dp
    private val hourHeight = 72f.dp
    private val cornerRadius = 6f.dp
    private val cellPadding = 2f.dp

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
    }

    private val availablePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E8F5E9")
        style = Paint.Style.FILL
    }

    private val busyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFCDD2")
        style = Paint.Style.FILL
    }

    private val busyBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E53935")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }

    private val schedulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#BBDEFB")
        style = Paint.Style.FILL
    }

    private val scheduleBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1565C0")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }

    private val cardTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B71C1C")
        textSize = 10f.sp
        typeface = Typeface.DEFAULT_BOLD
    }

    private val scheduleTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0D47A1")
        textSize = 10f.sp
        typeface = Typeface.DEFAULT_BOLD
    }

    private val bgPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            handleTap(e.x, e.y)
            return true
        }
    })

    fun setConfig(config: AvailabilityGridConfig) {
        this.config = config
        requestLayout()
        invalidate()
    }

    fun setBusySlots(slots: List<LecturerAvailability>) {
        this.busySlots = slots
        slotRects.clear()
        invalidate()
    }

    fun setScheduleEntries(entries: List<ScheduleEntry>) {
        this.scheduleEntries = entries
        slotRects.clear()
        invalidate()
    }

    fun setOnBusySlotClickListener(listener: (LecturerAvailability) -> Unit) {
        busyClickListener = listener
    }

    fun setOnEmptyCellClickListener(listener: (day: String, startTime: String, endTime: String) -> Unit) {
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
        gestureDetector.onTouchEvent(event)
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        slotRects.clear()

        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        drawAvailableBackground(canvas)
        drawHeader(canvas)
        drawTimeColumn(canvas)
        drawGrid(canvas)
        drawBusySlots(canvas)
        drawScheduleEntries(canvas)
    }

    private fun drawAvailableBackground(canvas: Canvas) {
        for (i in 0 until dayCount) {
            val left = timeColumnWidth + i * dayColumnWidth
            val right = left + dayColumnWidth
            canvas.drawRect(left, headerHeight, right, height.toFloat(), availablePaint)
        }
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
            canvas.drawText(formatTime(minute), timeColumnWidth / 2, y + timeTextPaint.textSize / 3, timeTextPaint)
            minute += step
        }
    }

    private fun drawGrid(canvas: Canvas) {
        val step = config.timeStepMinutes.coerceAtLeast(30)
        var minute = startMinutes
        while (minute <= endMinutes) {
            val y = minuteToY(minute)
            canvas.drawLine(timeColumnWidth, y, width.toFloat(), y, gridLinePaint)
            minute += step
        }
        for (i in 0..dayCount) {
            val x = timeColumnWidth + i * dayColumnWidth
            canvas.drawLine(x, headerHeight, x, height.toFloat(), gridLinePaint)
        }
    }

    private fun drawBusySlots(canvas: Canvas) {
        for (slot in busySlots) {
            val dayIndex = config.activeDays.indexOf(slot.day)
            if (dayIndex < 0) continue

            val slotStart = toMinutes(slot.startTime)
            val slotEnd = toMinutes(slot.endTime)

            val left = timeColumnWidth + dayIndex * dayColumnWidth + cellPadding
            val right = left + dayColumnWidth - cellPadding * 2
            val top = minuteToY(slotStart) + cellPadding
            val bottom = minuteToY(slotEnd) - cellPadding

            val rect = RectF(left, top, right, bottom)
            slotRects.add(rect to slot)

            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, busyPaint)
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, busyBorderPaint)

            val textX = left + 6f.dp
            val lineHeight = cardTextPaint.textSize * 1.3f
            if (bottom - top > lineHeight) {
                canvas.drawText("Meşgul", textX, top + lineHeight, cardTextPaint)
            }
            if (bottom - top > lineHeight * 2) {
                canvas.drawText(slot.timeRange, textX, top + lineHeight * 2, cardTextPaint)
            }
        }
    }

    private fun drawScheduleEntries(canvas: Canvas) {
        for (entry in scheduleEntries) {
            val dayIndex = config.activeDays.indexOf(entry.day)
            if (dayIndex < 0) continue

            val entryStart = toMinutes(entry.startTime)
            val entryEnd = toMinutes(entry.endTime)

            val left = timeColumnWidth + dayIndex * dayColumnWidth + cellPadding
            val right = left + dayColumnWidth - cellPadding * 2
            val top = minuteToY(entryStart) + cellPadding
            val bottom = minuteToY(entryEnd) - cellPadding

            val rect = RectF(left, top, right, bottom)
            slotRects.add(rect to entry)

            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, schedulePaint)
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, scheduleBorderPaint)

            val textX = left + 6f.dp
            val lineHeight = scheduleTextPaint.textSize * 1.3f
            if (bottom - top > lineHeight) {
                canvas.drawText(entry.courseCode, textX, top + lineHeight, scheduleTextPaint)
            }
            if (bottom - top > lineHeight * 2) {
                canvas.drawText(entry.timeRange, textX, top + lineHeight * 2, scheduleTextPaint)
            }
        }
    }

    private fun handleTap(x: Float, y: Float) {
        for ((rect, data) in slotRects.asReversed()) {
            if (rect.contains(x, y)) {
                when (data) {
                    is LecturerAvailability -> busyClickListener?.invoke(data)
                    is ScheduleEntry -> {}
                }
                return
            }
        }

        if (x > timeColumnWidth && y > headerHeight) {
            val dayIndex = ((x - timeColumnWidth) / dayColumnWidth).toInt()
            if (dayIndex in config.activeDays.indices) {
                val minute = yToMinute(y)
                val hourStart = (minute / 60) * 60
                val startStr = formatTime(hourStart)
                val endStr = formatTime(hourStart + 60)
                emptyCellClickListener?.invoke(config.activeDays[dayIndex], startStr, endStr)
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

    private fun toMinutes(time: String): Int {
        val parts = time.split(":")
        return (parts.getOrNull(0)?.toIntOrNull() ?: 0) * 60 +
               (parts.getOrNull(1)?.toIntOrNull() ?: 0)
    }

    private fun formatTime(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return String.format("%02d:%02d", h, m)
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

    private val Float.dp: Float get() = this * resources.displayMetrics.density
    @Suppress("DEPRECATION")
    private val Float.sp: Float get() = this * resources.displayMetrics.scaledDensity
}
