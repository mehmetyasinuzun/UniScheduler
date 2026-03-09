package com.unischeduler.domain.model

data class ScheduleConfig(
    val id: Int = 0,
    val departmentId: Int,
    val slotDurationMinutes: Int = 60,
    val dayStartTime: String = "08:00",
    val dayEndTime: String = "17:00",
    val activeDays: List<Int> = listOf(1, 2, 3, 4, 5)
) {
    val totalSlotsPerDay: Int
        get() {
            val startMinutes = parseTimeToMinutes(dayStartTime)
            val endMinutes = parseTimeToMinutes(dayEndTime)
            return (endMinutes - startMinutes) / slotDurationMinutes
        }

    fun slotStartTime(slotIndex: Int): String {
        val startMinutes = parseTimeToMinutes(dayStartTime) + (slotIndex * slotDurationMinutes)
        return minutesToTime(startMinutes)
    }

    fun slotEndTime(slotIndex: Int): String {
        val startMinutes = parseTimeToMinutes(dayStartTime) + ((slotIndex + 1) * slotDurationMinutes)
        return minutesToTime(startMinutes)
    }

    private fun parseTimeToMinutes(time: String): Int {
        val parts = time.split(":")
        return parts[0].toInt() * 60 + parts[1].toInt()
    }

    private fun minutesToTime(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return "%02d:%02d".format(h, m)
    }
}
