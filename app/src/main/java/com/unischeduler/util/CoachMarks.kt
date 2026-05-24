// CoachMarks — TapTargetView wrapper'ı.
//
// Material Design "tap target" pattern: kritik bir UI elemanının üstüne
// daire + başlık + açıklama gösterilen full-screen overlay. Kullanıcı
// elemanı (veya başka yere) tıklayınca kapanır, bir daha çıkmaz
// (SharedPreferences flag).
//
// Kullanım:
//   CoachMarks.showOnce(
//     activity      = requireActivity(),
//     target        = binding.btnImportLecturers,
//     titleRes      = R.string.coach_excel_title,
//     bodyRes       = R.string.coach_excel_body,
//     prefKey       = CoachKey.EXCEL_IMPORT
//   )
//
// Etiket türleri (CoachKey) TutorialPrefs ile bire bir eşleşir.
package com.unischeduler.util

import android.view.View
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import com.getkeepsafe.taptargetview.TapTarget
import com.getkeepsafe.taptargetview.TapTargetView
import com.unischeduler.R

enum class CoachKey { EXCEL_IMPORT, AUTO_SCHEDULE, ADMIN_PDF, LECTURER_PDF }

object CoachMarks {

    private fun isPending(prefs: TutorialPrefs, key: CoachKey): Boolean = when (key) {
        CoachKey.EXCEL_IMPORT  -> !prefs.coachExcelImportDone
        CoachKey.AUTO_SCHEDULE -> !prefs.coachAutoScheduleDone
        CoachKey.ADMIN_PDF     -> !prefs.coachAdminPdfDone
        CoachKey.LECTURER_PDF  -> !prefs.coachLecturerPdfDone
    }

    private fun markDone(prefs: TutorialPrefs, key: CoachKey) {
        when (key) {
            CoachKey.EXCEL_IMPORT  -> prefs.coachExcelImportDone = true
            CoachKey.AUTO_SCHEDULE -> prefs.coachAutoScheduleDone = true
            CoachKey.ADMIN_PDF     -> prefs.coachAdminPdfDone = true
            CoachKey.LECTURER_PDF  -> prefs.coachLecturerPdfDone = true
        }
    }

    /**
     * Fragment için coach mark — view tree hazır olduğunda kullan.
     * Hedef view'ın global koordinatları lazım; view.post {} ile layout
     * pass'ı bekleyip ondan sonra göster.
     */
    fun showOnce(
        fragment: Fragment,
        target: View,
        @StringRes titleRes: Int,
        @StringRes bodyRes: Int,
        key: CoachKey
    ) {
        val ctx = fragment.context ?: return
        val prefs = TutorialPrefs(ctx)
        if (!isPending(prefs, key)) return

        target.post {
            // Fragment destroy olmuş olabilir (network gecikme vs)
            if (!fragment.isAdded || target.windowToken == null) return@post

            val activity = fragment.activity ?: return@post
            val tap = TapTarget.forView(target, ctx.getString(titleRes), ctx.getString(bodyRes))
                .outerCircleColorInt(ctx.getColor(R.color.colorPrimaryDark))
                .outerCircleAlpha(0.92f)
                .targetCircleColorInt(0xFFFFFFFF.toInt())
                .titleTextSize(20)
                .descriptionTextSize(14)
                .titleTextColorInt(0xFFFFFFFF.toInt())
                .descriptionTextColorInt(0xCCFFFFFF.toInt())
                .textTypeface(android.graphics.Typeface.SANS_SERIF)
                .dimColor(android.R.color.black)
                .drawShadow(true)
                .cancelable(true)         // dışarı tıklayınca da kapansın
                .tintTarget(true)
                .transparentTarget(false)
                .targetRadius(56)         // dp değil px (TapTarget kendisi dönüştürüyor)

            TapTargetView.showFor(activity, tap, object : TapTargetView.Listener() {
                override fun onTargetClick(view: TapTargetView) {
                    super.onTargetClick(view)
                    markDone(prefs, key)
                }
                override fun onOuterCircleClick(view: TapTargetView) {
                    super.onOuterCircleClick(view)
                    view.dismiss(true)
                    markDone(prefs, key)
                }
                override fun onTargetCancel(view: TapTargetView) {
                    super.onTargetCancel(view)
                    markDone(prefs, key)
                }
            })
        }
    }
}
