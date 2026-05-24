// AdminTutorialActivity — yönetici için 9-sayfa detaylı tur.
//
// MainActivity.onCreate (login sonrası) ya da SettingsFragment'teki
// "Tanıtımı Tekrar Göster" tıklanınca başlar.
package com.unischeduler.ui.onboarding

import android.content.Context
import android.content.Intent
import com.unischeduler.R
import com.unischeduler.util.TutorialPrefs

class AdminTutorialActivity : RoleTutorialActivity() {

    override val pages: List<TutorialPage> = listOf(
        TutorialPage(R.drawable.ic_intro_welcome,    R.string.admin_tut_p1_title, R.string.admin_tut_p1_body),
        TutorialPage(R.drawable.ic_tut_dashboard,    R.string.admin_tut_p2_title, R.string.admin_tut_p2_body),
        TutorialPage(R.drawable.ic_tut_dept,         R.string.admin_tut_p3_title, R.string.admin_tut_p3_body),
        TutorialPage(R.drawable.ic_empty_lecturers,  R.string.admin_tut_p4_title, R.string.admin_tut_p4_body),
        TutorialPage(R.drawable.ic_tut_excel,        R.string.admin_tut_p5_title, R.string.admin_tut_p5_body),
        TutorialPage(R.drawable.ic_tut_data,         R.string.admin_tut_p6_title, R.string.admin_tut_p6_body),
        TutorialPage(R.drawable.ic_tut_autoschedule, R.string.admin_tut_p7_title, R.string.admin_tut_p7_body),
        TutorialPage(R.drawable.ic_tut_pdf,          R.string.admin_tut_p8_title, R.string.admin_tut_p8_body),
        TutorialPage(R.drawable.ic_tut_done,         R.string.admin_tut_p9_title, R.string.admin_tut_p9_body)
    )

    override fun onTutorialCompleted() {
        TutorialPrefs(this).adminTutorialDone = true
    }

    companion object {
        fun isPending(context: Context): Boolean = !TutorialPrefs(context).adminTutorialDone
        fun start(context: Context) {
            context.startActivity(Intent(context, AdminTutorialActivity::class.java))
        }
    }
}
