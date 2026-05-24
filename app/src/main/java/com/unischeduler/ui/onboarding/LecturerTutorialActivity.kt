// LecturerTutorialActivity — hoca için 5-sayfa hızlı tur.
package com.unischeduler.ui.onboarding

import android.content.Context
import android.content.Intent
import com.unischeduler.R
import com.unischeduler.util.TutorialPrefs

class LecturerTutorialActivity : RoleTutorialActivity() {

    override val pages: List<TutorialPage> = listOf(
        TutorialPage(R.drawable.ic_intro_welcome,    R.string.lect_tut_p1_title, R.string.lect_tut_p1_body),
        TutorialPage(R.drawable.ic_empty_schedule,   R.string.lect_tut_p2_title, R.string.lect_tut_p2_body),
        TutorialPage(R.drawable.ic_tut_availability, R.string.lect_tut_p3_title, R.string.lect_tut_p3_body),
        TutorialPage(R.drawable.ic_tut_lecturer_pdf, R.string.lect_tut_p4_title, R.string.lect_tut_p4_body),
        TutorialPage(R.drawable.ic_tut_settings,     R.string.lect_tut_p5_title, R.string.lect_tut_p5_body)
    )

    override fun onTutorialCompleted() {
        TutorialPrefs(this).lecturerTutorialDone = true
    }

    companion object {
        fun isPending(context: Context): Boolean = !TutorialPrefs(context).lecturerTutorialDone
        fun start(context: Context) {
            context.startActivity(Intent(context, LecturerTutorialActivity::class.java))
        }
    }
}
