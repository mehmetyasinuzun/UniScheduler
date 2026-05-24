// ╔══════════════════════════════════════════════════════════════════════════╗
// ║ TourCoordinator — uygulama içi rehberli tur (in-app guided tour)        ║
// ║                                                                          ║
// ║ ÖNCEKİ ÇÖZÜM (kötü): RoleTutorialActivity 9 sayfa statik slayt          ║
// ║ swipe — kullanıcı uygulamadan kopuk metin okuyor, sonra "nerede şu      ║
// ║ buton" diye arıyor. Öğrenim etkisi düşük.                               ║
// ║                                                                          ║
// ║ YENİ ÇÖZÜM: Spotlight Tour pattern.                                     ║
// ║   1. Kullanıcı login olur                                                ║
// ║   2. Tour başlar: ilk sekmeye otomatik navigate                         ║
// ║   3. O sayfada BottomSheet kart belirir: "Burası Home, şunu yapıyor"   ║
// ║   4. Kullanıcı "İlerle" tıklar → otomatik sıradaki sekmeye navigate    ║
// ║   5. Tur bitince başarı snackbar'ı                                      ║
// ║                                                                          ║
// ║ Avantajlar:                                                             ║
// ║   • Kullanıcı gerçek butonları, gerçek sayfaları görerek öğrenir       ║
// ║   • Her adım uygulamanın canlı haline odaklı                           ║
// ║   • Atla istediği yerde, kaldığı yerden devam etmiyor (basit)         ║
// ║   • Lifecycle-safe: NavController + Handler.postDelayed ile fragment   ║
// ║     hazır olunca dialog açılır                                          ║
// ╚══════════════════════════════════════════════════════════════════════════╝
package com.unischeduler.util

import android.app.Activity
import android.os.Handler
import android.os.Looper
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import com.unischeduler.R

object TourCoordinator {

    enum class Type { ADMIN, LECTURER }

    data class Step(
        @IdRes val destinationId: Int,
        @StringRes val titleRes: Int,
        @StringRes val bodyRes: Int
    )

    /** ADMIN turu — 6 ana sekme. Sırası, kullanıcının kurulum akışıyla uyumlu:
     *  Home → Data (hocalar/dersler) → Classrooms → Assign → Calendar → Settings. */
    private val adminSteps = listOf(
        Step(R.id.adminHomeFragment,    R.string.tour_a_home_title,    R.string.tour_a_home_body),
        Step(R.id.dataFragment,         R.string.tour_a_data_title,    R.string.tour_a_data_body),
        Step(R.id.classroomsFragment,   R.string.tour_a_class_title,   R.string.tour_a_class_body),
        Step(R.id.assignmentFragment,   R.string.tour_a_assign_title,  R.string.tour_a_assign_body),
        Step(R.id.adminCalendarFragment,R.string.tour_a_cal_title,     R.string.tour_a_cal_body),
        Step(R.id.settingsFragment,     R.string.tour_a_set_title,     R.string.tour_a_set_body)
    )

    /** LECTURER turu — 2 ana sekme.
     *  Eski sürüm 3 adımdı (home → availability → home) ama 1. ve 3. aynı
     *  destination olunca "aynı ekran, yeni dialog" görünüyordu — UX kötüydü.
     *  Ayarlar bilgisi step 1'in body metnine eklendi (Home'da ayar kartı var). */
    private val lecturerSteps = listOf(
        Step(R.id.lecturerHomeFragment, R.string.tour_l_home_title,  R.string.tour_l_home_body),
        Step(R.id.availabilityFragment, R.string.tour_l_avail_title, R.string.tour_l_avail_body)
    )

    // ── State (process-wide; iki tour aynı anda olmaz) ───────────────────
    @Volatile private var activeType: Type? = null
    @Volatile private var stepIndex: Int = 0
    @Volatile private var activeDialog: BottomSheetDialog? = null
    // Audit bulgusu: navigate() peş peşe fail olursa sonsuz loop. Sayaç
    // ile 3 ardışık fail sonrası tour'u güvenli şekilde sonlandır.
    @Volatile private var consecutiveFailures: Int = 0
    private const val MAX_CONSECUTIVE_FAILURES: Int = 3

    val isRunning: Boolean get() = activeType != null

    private fun stepsFor(type: Type): List<Step> = when (type) {
        Type.ADMIN -> adminSteps
        Type.LECTURER -> lecturerSteps
    }

    /**
     * Tour'u başlatır. Activity + NavController referansı lazım — navigation
     * için. Caller (MainActivity) bunları sağlar.
     */
    fun start(activity: FragmentActivity, navController: NavController, type: Type) {
        // Önceden açık dialog varsa kapat (idempotent re-start)
        activeDialog?.dismiss()
        activeDialog = null
        activeType = type
        stepIndex = 0
        consecutiveFailures = 0   // her yeni tour temiz başlasın
        runCurrentStep(activity, navController)
    }

    /** Atla / dismiss — bayrakları sıfırla, "tamamlandı" flag set et. */
    fun cancel(activity: FragmentActivity) {
        val type = activeType
        activeDialog?.dismiss()
        activeDialog = null
        activeType = null
        stepIndex = 0
        type?.let { markDone(activity, it) }
    }

    private fun markDone(activity: FragmentActivity, type: Type) {
        val prefs = TutorialPrefs(activity)
        when (type) {
            Type.ADMIN -> prefs.adminTutorialDone = true
            Type.LECTURER -> prefs.lecturerTutorialDone = true
        }
    }

    private fun runCurrentStep(activity: FragmentActivity, navController: NavController) {
        val type = activeType ?: return
        val steps = stepsFor(type)
        if (stepIndex >= steps.size) {
            // Tur bitti
            markDone(activity, type)
            activeType = null
            activeDialog = null
            stepIndex = 0
            showCompletionSnackbar(activity)
            return
        }

        val step = steps[stepIndex]
        // 1) Hedef sekmeye navigate
        runCatching {
            // Aynı destination'a re-navigate yapmamak için kontrol
            if (navController.currentDestination?.id != step.destinationId) {
                navController.navigate(step.destinationId)
            }
        }.onFailure { e ->
            android.util.Log.e("TourCoordinator", "navigate fail step=$stepIndex: ${e.message}", e)
            CrashHandler.appendPendingCrash(
                activity.applicationContext, "TourCoordinator", "navigate.$stepIndex", e
            )
            // Sonsuz loop koruması: 3 ardışık fail sonrası tour'u sonlandır.
            consecutiveFailures++
            if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                android.util.Log.e("TourCoordinator", "abort: $consecutiveFailures consecutive failures")
                cancel(activity)
                return
            }
            stepIndex++
            runCurrentStep(activity, navController)
            return
        }
        // Adım başarılı navigate olduysa fail sayacını sıfırla.
        consecutiveFailures = 0

        // 2) Fragment render olsun diye küçük bekleme. 250ms hem güvenli
        //    (BottomNav transition tipik 200-300ms) hem kullanıcı sekme
        //    değişimini fark eder. Önceden 350ms idi — audit "ölü zaman"
        //    olarak işaretlemişti, hızlandırıldı.
        Handler(Looper.getMainLooper()).postDelayed({
            if (activity.isFinishing || activity.isDestroyed) return@postDelayed
            showStepDialog(activity, navController, step, stepIndex + 1, steps.size)
        }, 250L)
    }

    private fun showStepDialog(
        activity: FragmentActivity,
        navController: NavController,
        step: Step,
        currentNum: Int,
        total: Int
    ) {
        runCatching {
            val dialog = BottomSheetDialog(activity)
            val view = activity.layoutInflater.inflate(R.layout.dialog_tour_step, null)
            dialog.setContentView(view)
            dialog.behavior.skipCollapsed = true
            dialog.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
            // Dışarı tıkla iptal etmesin — kullanıcı yanlışlıkla turu kaybetmesin
            dialog.setCancelable(false)
            dialog.setCanceledOnTouchOutside(false)
            // Audit fix: back press handling — setCancelable(false) bottom sheet
            // back butonunu engelliyor AMA kullanıcı geri tuşuna basarsa hiçbir
            // şey olmazsa "donmuş" hisseder. Manuel key listener ile back =
            // "atla" davranışı uygulayalım — kullanıcıya kontrol geri verilir.
            dialog.setOnKeyListener { _, keyCode, event ->
                if (keyCode == android.view.KeyEvent.KEYCODE_BACK &&
                    event.action == android.view.KeyEvent.ACTION_UP) {
                    dialog.dismiss()
                    cancel(activity)
                    true
                } else false
            }

            view.findViewById<android.widget.TextView>(R.id.tvTourTitle)
                .setText(step.titleRes)
            view.findViewById<android.widget.TextView>(R.id.tvTourBody)
                .setText(step.bodyRes)
            view.findViewById<android.widget.TextView>(R.id.tvTourCounter)
                .text = activity.getString(R.string.tutorial_page_counter, currentNum, total)

            val progress = view.findViewById<com.google.android.material.progressindicator.LinearProgressIndicator>(R.id.progressTour)
            progress.setProgressCompat((currentNum * 100 / total).coerceIn(0, 100), true)

            // Son sayfa: "İlerle" → "Tamamla"
            val btnNext = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnTourNext)
            if (currentNum == total) {
                btnNext.setText(R.string.onboarding_finish)
                btnNext.icon = null
            }
            btnNext.setOnClickListener {
                dialog.dismiss()
                stepIndex++
                runCurrentStep(activity, navController)
            }

            view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnTourSkip)
                .setOnClickListener {
                    dialog.dismiss()
                    cancel(activity)
                }

            dialog.setOnDismissListener { activeDialog = null }
            dialog.show()
            activeDialog = dialog
        }.onFailure { e ->
            // Dialog inflation crash olursa: log, flag set, finish.
            android.util.Log.e("TourCoordinator", "dialog show fail: ${e.message}", e)
            CrashHandler.appendPendingCrash(
                activity.applicationContext, "TourCoordinator", "showStep.$stepIndex", e
            )
            // Devam etmeyi dene — bir sonraki adımda toparlanabilir
            stepIndex++
            runCurrentStep(activity, navController)
        }
    }

    private fun showCompletionSnackbar(activity: Activity) {
        runCatching {
            val root = activity.findViewById<android.view.View>(android.R.id.content) ?: return
            Snackbar.make(root, R.string.tour_completed, Snackbar.LENGTH_LONG).show()
        }
    }
}
