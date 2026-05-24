// TutorialPrefs — kullanıcının hangi tutorial'ları gördüğünü hatırlar.
//
// 3 ayrı flag:
//   1. welcome_done      → Login öncesi 3-sayfa genel hoş geldin
//   2. admin_tutorial_done    → Login sonrası admin için 9-sayfa rehber
//   3. lecturer_tutorial_done → Login sonrası lecturer için 5-sayfa rehber
//
// Coach marks için ayrı flag'ler (sayfa bazlı):
//   coach_excel_import_done, coach_auto_schedule_done, coach_pdf_done,
//   coach_lecturer_pdf_done
//
// Ayarlar'da "Tanıtımı Tekrar Göster" tıklandığında ilgili flag false'a
// çekilir → tutorial yeniden açılır.
//
// App.PREFS_NAME altında saklanır — login bilgilerinden farklı dosya
// (uni_scheduler_session), tutorial flag'leri encrypted olmayan basit
// SharedPreferences yeterli (gizli bilgi yok).
package com.unischeduler.util

import android.content.Context
import com.unischeduler.App

class TutorialPrefs(context: Context) {

    private val prefs = context.getSharedPreferences(App.PREFS_NAME, Context.MODE_PRIVATE)

    // ── Welcome (login öncesi) ──────────────────────────────────────────
    var welcomeDone: Boolean
        get() = prefs.getBoolean(KEY_WELCOME, false)
        set(v) { prefs.edit().putBoolean(KEY_WELCOME, v).apply() }

    // ── Rol-bazlı tutorial (login sonrası ilk kez) ──────────────────────
    var adminTutorialDone: Boolean
        get() = prefs.getBoolean(KEY_ADMIN_TUTORIAL, false)
        set(v) { prefs.edit().putBoolean(KEY_ADMIN_TUTORIAL, v).apply() }

    var lecturerTutorialDone: Boolean
        get() = prefs.getBoolean(KEY_LECTURER_TUTORIAL, false)
        set(v) { prefs.edit().putBoolean(KEY_LECTURER_TUTORIAL, v).apply() }

    // ── Coach marks (kritik sayfalarda ilk-açılış balonu) ──────────────
    var coachExcelImportDone: Boolean
        get() = prefs.getBoolean(KEY_COACH_EXCEL, false)
        set(v) { prefs.edit().putBoolean(KEY_COACH_EXCEL, v).apply() }

    var coachAutoScheduleDone: Boolean
        get() = prefs.getBoolean(KEY_COACH_AUTOSCHED, false)
        set(v) { prefs.edit().putBoolean(KEY_COACH_AUTOSCHED, v).apply() }

    var coachAdminPdfDone: Boolean
        get() = prefs.getBoolean(KEY_COACH_ADMIN_PDF, false)
        set(v) { prefs.edit().putBoolean(KEY_COACH_ADMIN_PDF, v).apply() }

    var coachLecturerPdfDone: Boolean
        get() = prefs.getBoolean(KEY_COACH_LECT_PDF, false)
        set(v) { prefs.edit().putBoolean(KEY_COACH_LECT_PDF, v).apply() }

    /** Settings'teki "Tanıtımı Tekrar Göster" butonu — admin için tüm
     *  tutorial + coach mark flag'lerini sıfırlar. */
    fun resetAdminTutorial() {
        prefs.edit()
            .putBoolean(KEY_ADMIN_TUTORIAL, false)
            .putBoolean(KEY_COACH_EXCEL, false)
            .putBoolean(KEY_COACH_AUTOSCHED, false)
            .putBoolean(KEY_COACH_ADMIN_PDF, false)
            .apply()
    }

    fun resetLecturerTutorial() {
        prefs.edit()
            .putBoolean(KEY_LECTURER_TUTORIAL, false)
            .putBoolean(KEY_COACH_LECT_PDF, false)
            .apply()
    }

    companion object {
        private const val KEY_WELCOME            = "tutorial_welcome_done_v1"
        private const val KEY_ADMIN_TUTORIAL     = "tutorial_admin_done_v1"
        private const val KEY_LECTURER_TUTORIAL  = "tutorial_lecturer_done_v1"
        private const val KEY_COACH_EXCEL        = "coach_excel_import_v1"
        private const val KEY_COACH_AUTOSCHED    = "coach_auto_schedule_v1"
        private const val KEY_COACH_ADMIN_PDF    = "coach_admin_pdf_v1"
        private const val KEY_COACH_LECT_PDF     = "coach_lecturer_pdf_v1"
    }
}
