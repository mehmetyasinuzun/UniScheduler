package com.unischeduler.scheduler

import android.content.Context
import androidx.annotation.StringRes
import com.unischeduler.R

data class SchedulePreferences(
    val studentCompactness: CompactnessMode = CompactnessMode.COMPACT,
    val lecturerMaxDailySlots: Int = 0,
    val dayBalancing: Boolean = true,
    val preferredStartTime: String = "",
    val preferredEndTime: String = "",
    val alternativeCount: Int = 5
) {
    // Enum sabit string'leri eskiden Türkçe hardcoded'du — uygulamanın geri kalanı
    // İngilizce'ye geçirildiğinde bu üç label dil değişimini takip etmiyordu.
    // Şimdi @StringRes ile values/strings.xml + values-en/strings.xml'den
    // dinamik çözülüyor. label(context) çağrılır.
    enum class CompactnessMode(@StringRes val labelRes: Int) {
        COMPACT(R.string.pref_compact),
        SPREAD(R.string.pref_spread),
        NONE(R.string.pref_none);

        fun label(ctx: Context): String = ctx.getString(labelRes)
    }
}
