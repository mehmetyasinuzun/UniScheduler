package com.unischeduler.scheduler

data class SchedulePreferences(
    val studentCompactness: CompactnessMode = CompactnessMode.COMPACT,
    val lecturerMaxDailySlots: Int = 0,
    val dayBalancing: Boolean = true,
    val preferredStartTime: String = "",
    val preferredEndTime: String = "",
    val alternativeCount: Int = 5
) {
    enum class CompactnessMode(val label: String) {
        COMPACT("Dersleri birleştir (aralıksız/az aralıklı)"),
        SPREAD("Dersleri güne yay (aralar bırak)"),
        NONE("Tercih yok (serbest yerleştir)")
    }
}
