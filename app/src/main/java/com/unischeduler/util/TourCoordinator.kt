// ╔══════════════════════════════════════════════════════════════════════════╗
// ║ TourCoordinator — Spotlight onboarding (mevcut MainActivity'de)         ║
// ║                                                                          ║
// ║ ADMIN: gerçek fragment'lar + TourMockStore mock data ile interaktif.   ║
// ║   Otofill form'a yazar, kullanıcı Ekle bas → overlay tap'i consume +   ║
// ║   onCutoutTapped → TourMockStore.addX() → fragment listener'ı adapter'a║
// ║   real+mock union submit. Veritabanına SIFIR yazma.                    ║
// ║                                                                          ║
// ║ LECTURER: saf bilgilendirici (mock data yok, sadece spotlight + bilgi).║
// ╚══════════════════════════════════════════════════════════════════════════╝
package com.unischeduler.util

import android.app.Activity
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.unischeduler.R

object TourCoordinator {

    private const val TAG = "TourCoordinator"

    enum class Type { ADMIN, LECTURER }

    class Step(
        val targetLocator: (FragmentActivity) -> View?,
        @StringRes val titleRes: Int,
        @StringRes val bodyRes: Int,
        @IdRes val accordionToOpen: Int? = null,
        @IdRes val navigateBeforeId: Int? = null,
        @IdRes val navigateAfterId: Int? = null,
        @IdRes val waitForTabId: Int? = null,
        val isFinal: Boolean = false,
        /** false: cutout içi tap'ler consume edilir (gerçek butona iletilmez).
         *  onTargetTapped != null ise otomatik olarak false yapılır. */
        val forwardTaps: Boolean = true,
        /** Step gösterilmeden önce form alanlarını programatik doldurur. */
        val autofillAction: ((FragmentActivity) -> Unit)? = null,
        /** Cutout içi tap (consume edilmiş) sonrası çağrılır — mock add +
         *  advance. forwardTaps otomatik false yapılır. */
        val onTargetTapped: ((FragmentActivity) -> Unit)? = null
    )

    // ── ADMIN — 15 adım, mevcut MainActivity fragment'larında ───────────
    // Mock data senin verdiğin: BM bölümü, Farhan Adl, PL101 Programming
    // Language + MP101 Mobile Programming, L101 derslik, Pzt 09:00-12:00.
    private val adminSteps: List<Step> = listOf(

        // 1: Home → Data tab
        Step(
            targetLocator = { it.findViewById<BottomNavigationView>(R.id.bottomNavAdmin)?.findViewById(R.id.dataFragment) },
            titleRes = R.string.tour_demo_s_data_title,
            bodyRes = R.string.tour_demo_s_data_body,
            waitForTabId = R.id.dataFragment
        ),

        // 2: Data → btnGoSettings (kullanıcı gerçek butona basar, navigate edilir)
        Step(
            targetLocator = { findFragmentView(it, R.id.btnGoSettings) },
            titleRes = R.string.tour_demo_s_settings_title,
            bodyRes = R.string.tour_demo_s_settings_body,
            waitForTabId = R.id.settingsFragment
        ),

        // 3: Settings — Bölüm Ekleme (autofill + mock add)
        Step(
            targetLocator = { findFragmentView(it, R.id.btnAddDept) },
            titleRes = R.string.tour_demo_s_dept_title,
            bodyRes = R.string.tour_demo_s_dept_body,
            autofillAction = { act ->
                findFragmentView(act, R.id.etDeptName)?.let { (it as? EditText)?.setText("Bilgisayar Mühendisliği") }
            },
            onTargetTapped = { act ->
                val orgId = SessionManager(act).orgId
                TourMockStore.addDepartment(orgId)
            }
        ),

        // 4: Settings → Data tab
        Step(
            targetLocator = { it.findViewById<BottomNavigationView>(R.id.bottomNavAdmin)?.findViewById(R.id.dataFragment) },
            titleRes = R.string.tour_demo_s_back_data_title,
            bodyRes = R.string.tour_demo_s_back_data_body,
            waitForTabId = R.id.dataFragment
        ),

        // 5: Hoca Ekleme — Farhan Adl
        Step(
            targetLocator = { findFragmentView(it, R.id.btnAddLecturer) },
            titleRes = R.string.tour_demo_s_lecturer_title,
            bodyRes = R.string.tour_demo_s_lecturer_body,
            accordionToOpen = R.id.headerLecturers,
            autofillAction = { act ->
                findFragmentView(act, R.id.etFirstName)?.let { (it as? EditText)?.setText("Farhan") }
                findFragmentView(act, R.id.etLastName)?.let { (it as? EditText)?.setText("Adl") }
            },
            onTargetTapped = { act ->
                val orgId = SessionManager(act).orgId
                TourMockStore.addLecturer(orgId)
            }
        ),

        // 6: Ders 1 — PL101 Programming Language
        Step(
            targetLocator = { findFragmentView(it, R.id.btnAddCourse) },
            titleRes = R.string.tour_demo_s_course1_title,
            bodyRes = R.string.tour_demo_s_course1_body,
            accordionToOpen = R.id.headerCourses,
            autofillAction = { act ->
                findFragmentView(act, R.id.etCourseCode)?.let { (it as? EditText)?.setText("PL101") }
                findFragmentView(act, R.id.etCourseName)?.let { (it as? EditText)?.setText("Programming Language") }
                findFragmentView(act, R.id.etTheoryHours)?.let { (it as? EditText)?.setText("3") }
                findFragmentView(act, R.id.etLabHours)?.let { (it as? EditText)?.setText("0") }
                findFragmentView(act, R.id.etCredits)?.let { (it as? EditText)?.setText("3") }
            },
            onTargetTapped = { act ->
                val orgId = SessionManager(act).orgId
                TourMockStore.addCourse1(orgId)
            }
        ),

        // 7: Ders 2 — MP101 Mobile Programming
        Step(
            targetLocator = { findFragmentView(it, R.id.btnAddCourse) },
            titleRes = R.string.tour_demo_s_course2_title,
            bodyRes = R.string.tour_demo_s_course2_body,
            autofillAction = { act ->
                findFragmentView(act, R.id.etCourseCode)?.let { (it as? EditText)?.setText("MP101") }
                findFragmentView(act, R.id.etCourseName)?.let { (it as? EditText)?.setText("Mobile Programming") }
                findFragmentView(act, R.id.etTheoryHours)?.let { (it as? EditText)?.setText("2") }
                findFragmentView(act, R.id.etLabHours)?.let { (it as? EditText)?.setText("2") }
                findFragmentView(act, R.id.etCredits)?.let { (it as? EditText)?.setText("4") }
            },
            onTargetTapped = { act ->
                val orgId = SessionManager(act).orgId
                TourMockStore.addCourse2(orgId)
            }
        ),

        // 8: Offering 1 — Farhan + PL101
        Step(
            targetLocator = { findFragmentView(it, R.id.btnAddOffering) },
            titleRes = R.string.tour_demo_s_offering1_title,
            bodyRes = R.string.tour_demo_s_offering1_body,
            accordionToOpen = R.id.headerOfferings,
            autofillAction = { act ->
                findFragmentView(act, R.id.etAcademicYear)?.let { (it as? EditText)?.setText("2024-2025") }
                findFragmentView(act, R.id.etSection)?.let { (it as? EditText)?.setText("A") }
                findFragmentView(act, R.id.etOfferingCapacity)?.let { (it as? EditText)?.setText("30") }
            },
            onTargetTapped = { act ->
                val orgId = SessionManager(act).orgId
                TourMockStore.addOffering1(orgId)
            }
        ),

        // 9: Offering 2 — Farhan + MP101
        Step(
            targetLocator = { findFragmentView(it, R.id.btnAddOffering) },
            titleRes = R.string.tour_demo_s_offering2_title,
            bodyRes = R.string.tour_demo_s_offering2_body,
            autofillAction = { act ->
                findFragmentView(act, R.id.etAcademicYear)?.let { (it as? EditText)?.setText("2024-2025") }
                findFragmentView(act, R.id.etSection)?.let { (it as? EditText)?.setText("A") }
                findFragmentView(act, R.id.etOfferingCapacity)?.let { (it as? EditText)?.setText("30") }
            },
            onTargetTapped = { act ->
                val orgId = SessionManager(act).orgId
                TourMockStore.addOffering2(orgId)
            }
        ),

        // 10: Classrooms sekmesi
        Step(
            targetLocator = { it.findViewById<BottomNavigationView>(R.id.bottomNavAdmin)?.findViewById(R.id.classroomsFragment) },
            titleRes = R.string.tour_demo_s_classrooms_title,
            bodyRes = R.string.tour_demo_s_classrooms_body,
            waitForTabId = R.id.classroomsFragment
        ),

        // 11: Derslik Ekleme — L101
        Step(
            targetLocator = { findFragmentView(it, R.id.btnAddClassroom) },
            titleRes = R.string.tour_demo_s_classroom_add_title,
            bodyRes = R.string.tour_demo_s_classroom_add_body,
            autofillAction = { act ->
                findFragmentView(act, R.id.etRoomCode)?.let { (it as? EditText)?.setText("L101") }
                findFragmentView(act, R.id.etCapacity)?.let { (it as? EditText)?.setText("30") }
            },
            onTargetTapped = { act ->
                val orgId = SessionManager(act).orgId
                TourMockStore.addClassroom(orgId)
            }
        ),

        // 12: Assign sekmesi
        Step(
            targetLocator = { it.findViewById<BottomNavigationView>(R.id.bottomNavAdmin)?.findViewById(R.id.assignmentFragment) },
            titleRes = R.string.tour_demo_s_assign_title,
            bodyRes = R.string.tour_demo_s_assign_body,
            waitForTabId = R.id.assignmentFragment
        ),

        // 13: Manuel Atama — Pazartesi 09:00-12:00
        Step(
            targetLocator = { findFragmentView(it, R.id.btnAssign) },
            titleRes = R.string.tour_demo_s_manual_title,
            bodyRes = R.string.tour_demo_s_manual_body,
            autofillAction = { act ->
                findFragmentView(act, R.id.etStartTime)?.let { (it as? EditText)?.setText("09:00") }
                findFragmentView(act, R.id.etEndTime)?.let { (it as? EditText)?.setText("12:00") }
            },
            onTargetTapped = { act ->
                val orgId = SessionManager(act).orgId
                TourMockStore.addSchedule(orgId)
            }
        ),

        // 14: Calendar sekmesi
        Step(
            targetLocator = { it.findViewById<BottomNavigationView>(R.id.bottomNavAdmin)?.findViewById(R.id.adminCalendarFragment) },
            titleRes = R.string.tour_demo_s_calendar_title,
            bodyRes = R.string.tour_demo_s_calendar_body,
            waitForTabId = R.id.adminCalendarFragment
        ),

        // 15: Eklediğin ders takvimde — FINAL
        Step(
            targetLocator = { findFragmentView(it, R.id.weeklySchedule) ?: findFragmentView(it, R.id.cardWeekly) },
            titleRes = R.string.tour_demo_s_done_title,
            bodyRes = R.string.tour_demo_s_done_body,
            isFinal = true,
            forwardTaps = false
        )
    )

    // ── LECTURER — 7 adım saf bilgilendirici (mock yok) ──────────────────
    private val lecturerSteps: List<Step> = listOf(
        Step(
            targetLocator = { it.findViewById<BottomNavigationView>(R.id.bottomNavLecturer)?.findViewById(R.id.availabilityFragment) },
            titleRes = R.string.tour_lect_s1_title,
            bodyRes = R.string.tour_lect_s1_body,
            waitForTabId = R.id.availabilityFragment
        ),
        Step(
            targetLocator = { findFragmentView(it, R.id.availabilityGrid) },
            titleRes = R.string.tour_lect_s2_title,
            bodyRes = R.string.tour_lect_s2_body
        ),
        Step(
            targetLocator = { findFragmentView(it, R.id.btnAdd) },
            titleRes = R.string.tour_lect_s3_title,
            bodyRes = R.string.tour_lect_s3_body
        ),
        Step(
            targetLocator = { it.findViewById<BottomNavigationView>(R.id.bottomNavLecturer)?.findViewById(R.id.lecturerHomeFragment) },
            titleRes = R.string.tour_lect_s4_title,
            bodyRes = R.string.tour_lect_s4_body,
            waitForTabId = R.id.lecturerHomeFragment
        ),
        Step(
            targetLocator = { findFragmentView(it, R.id.btnExportPdf) },
            titleRes = R.string.tour_lect_s5_title,
            bodyRes = R.string.tour_lect_s5_body
        ),
        Step(
            targetLocator = { findFragmentView(it, R.id.btnReplayTutorial) },
            titleRes = R.string.tour_lect_s6_title,
            bodyRes = R.string.tour_lect_s6_body
        ),
        Step(
            targetLocator = { findFragmentView(it, R.id.btnLogout) },
            titleRes = R.string.tour_lect_s7_title,
            bodyRes = R.string.tour_lect_s7_body,
            isFinal = true,
            forwardTaps = false
        )
    )

    // ── State ────────────────────────────────────────────────────────────
    @Volatile private var activeType: Type? = null
    @Volatile private var stepIndex: Int = 0
    @Volatile private var savedNavController: NavController? = null
    @Volatile private var savedActivity: FragmentActivity? = null
    @Volatile private var onCompletedCallback: (() -> Unit)? = null
    @Volatile private var overlayView: TourOverlayView? = null
    @Volatile private var bottomCard: View? = null
    @Volatile private var consecutiveFailures: Int = 0
    private const val MAX_CONSECUTIVE_FAILURES = 3

    val isRunning: Boolean get() = activeType != null

    private val navListener = NavController.OnDestinationChangedListener { _, dest, _ ->
        val type = activeType ?: return@OnDestinationChangedListener
        val steps = stepsFor(type)
        if (stepIndex >= steps.size) return@OnDestinationChangedListener
        val step = steps[stepIndex]
        if (step.waitForTabId != null && dest.id == step.waitForTabId) {
            Log.d(TAG, "auto-advance: waitForTab matched (${dest.id})")
            savedActivity?.let { activity ->
                Handler(Looper.getMainLooper()).postDelayed({
                    advance(activity)
                }, 350L)
            }
        }
    }

    fun start(
        activity: FragmentActivity,
        navController: NavController,
        type: Type,
        onCompleted: (() -> Unit)? = null
    ) {
        Log.d(TAG, "start(type=$type, currentDest=${navController.currentDestination?.id})")
        cleanupInternal(activity)
        activeType = type
        savedActivity = activity
        savedNavController = navController
        onCompletedCallback = onCompleted
        stepIndex = 0
        consecutiveFailures = 0
        navController.addOnDestinationChangedListener(navListener)

        val homeDest = homeDestFor(type)
        val onHome = navController.currentDestination?.id == homeDest
        if (!onHome) {
            runCatching { navController.navigate(homeDest) }
                .onFailure { logCrash(activity, "start.navigateHome", it) }
        }
        val delay = if (onHome) 250L else 500L
        Handler(Looper.getMainLooper()).postDelayed({
            runCurrentStep(activity)
        }, delay)
    }

    fun cancel(activity: FragmentActivity) {
        Log.d(TAG, "cancel(stepIndex=$stepIndex)")
        val type = activeType
        val callback = onCompletedCallback
        cleanupInternal(activity)
        type?.let { markDone(activity, it) }
        runCatching { callback?.invoke() }
    }

    private fun cleanupInternal(activity: FragmentActivity) {
        removeOverlayAndCard(activity)
        savedNavController?.removeOnDestinationChangedListener(navListener)
        activeType = null
        savedNavController = null
        savedActivity = null
        onCompletedCallback = null
        stepIndex = 0
    }

    private fun markDone(activity: FragmentActivity, type: Type) {
        runCatching {
            val prefs = TutorialPrefs(activity)
            when (type) {
                Type.ADMIN -> prefs.adminTutorialDone = true
                Type.LECTURER -> prefs.lecturerTutorialDone = true
            }
        }
    }

    private fun runCurrentStep(activity: FragmentActivity) {
        val type = activeType ?: return
        if (activity.isFinishing || activity.isDestroyed) {
            activeType = null
            return
        }
        val steps = stepsFor(type)
        if (stepIndex >= steps.size) {
            val callback = onCompletedCallback
            markDone(activity, type)
            cleanupInternal(activity)
            showCompletionSnackbar(activity)
            runCatching { callback?.invoke() }
            return
        }
        val step = steps[stepIndex]

        val nav = savedNavController
        if (step.navigateBeforeId != null && nav != null) {
            val cur = nav.currentDestination?.id
            if (cur != step.navigateBeforeId) {
                runCatching { nav.navigate(step.navigateBeforeId) }
                    .onFailure { logCrash(activity, "step.$stepIndex.navigateBefore", it) }
                Handler(Looper.getMainLooper()).postDelayed({
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        prepareAndShow(activity, step)
                    }
                }, 500L)
                return
            }
        }
        prepareAndShow(activity, step)
    }

    private fun prepareAndShow(activity: FragmentActivity, step: Step) {
        step.accordionToOpen?.let { headerId ->
            val header = findFragmentView(activity, headerId)
            if (header != null && !isAccordionOpen(activity, headerId)) {
                runCatching { header.performClick() }
            }
        }

        // Autofill — form alanlarına programatik veri yaz
        step.autofillAction?.let { fill ->
            runCatching { fill.invoke(activity) }
                .onFailure { Log.w(TAG, "autofill failed: ${it.message}", it) }
        }

        Handler(Looper.getMainLooper()).postDelayed({
            if (activity.isFinishing || activity.isDestroyed) return@postDelayed
            val target = step.targetLocator(activity)
            if (target == null) {
                Log.w(TAG, "target not found at step $stepIndex — skipping")
                consecutiveFailures++
                if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                    cancel(activity)
                    return@postDelayed
                }
                stepIndex++
                runCurrentStep(activity)
                return@postDelayed
            }
            consecutiveFailures = 0

            runCatching {
                val rect = Rect(0, 0, target.width.coerceAtLeast(1), target.height.coerceAtLeast(1))
                target.requestRectangleOnScreen(rect, false)
            }

            Handler(Looper.getMainLooper()).postDelayed({
                if (activity.isFinishing || activity.isDestroyed) return@postDelayed
                renderStep(activity, step, target)
            }, 300L)
        }, 350L)
    }

    private fun isAccordionOpen(activity: FragmentActivity, @IdRes headerId: Int): Boolean {
        val contentId = when (headerId) {
            R.id.headerLecturers -> R.id.contentLecturers
            R.id.headerCourses -> R.id.contentCourses
            R.id.headerOfferings -> R.id.contentOfferings
            else -> return false
        }
        val content = findFragmentView(activity, contentId) ?: return false
        return content.visibility == View.VISIBLE
    }

    private fun renderStep(activity: FragmentActivity, step: Step, target: View) {
        val mainRoot = activity.findViewById<androidx.constraintlayout.widget.ConstraintLayout>(R.id.mainRoot)
        val fallback = activity.findViewById<ViewGroup>(android.R.id.content)
        val container: ViewGroup = mainRoot ?: fallback ?: return
        val useCL = container === mainRoot

        val steps = stepsFor(activeType ?: return)
        val total = steps.size
        val current = stepIndex + 1

        removeOverlayAndCard(activity)

        // onTargetTapped != null ise overlay tap'i consume + callback tetikle
        val effectiveForwardTaps = step.forwardTaps && step.onTargetTapped == null

        val overlay = TourOverlayView(activity).apply {
            setTarget(target, forwardTaps = effectiveForwardTaps)
        }
        if (step.onTargetTapped != null) {
            overlay.onCutoutTapped = {
                Log.d(TAG, "onCutoutTapped at step $stepIndex — mock add + advance")
                runCatching { step.onTargetTapped.invoke(activity) }
                    .onFailure { logCrash(activity, "step.$stepIndex.onTargetTapped", it) }
                advance(activity)
            }
        }
        val overlayParams: ViewGroup.LayoutParams = if (useCL) {
            androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(0, 0).apply {
                topToTop = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
                bottomToBottom = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
                startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
                endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            }
        } else {
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        container.addView(overlay, overlayParams)
        overlay.post { overlay.refreshCutout() }
        overlayView = overlay

        val card = activity.layoutInflater.inflate(R.layout.view_tour_bottom_card, container, false)
        card.findViewById<TextView>(R.id.tvTourCounter).text =
            activity.getString(R.string.tour_counter_fmt, current, total)
        card.findViewById<TextView>(R.id.tvTourTitle).text = activity.getString(step.titleRes)
        card.findViewById<TextView>(R.id.tvTourBody).text = activity.getString(step.bodyRes)
        card.findViewById<LinearProgressIndicator>(R.id.tourProgress)
            .setProgressCompat((current * 100 / total).coerceIn(0, 100), true)

        val btnNext = card.findViewById<MaterialButton>(R.id.btnTourNext)
        val tvHint = card.findViewById<TextView>(R.id.tvTourTapHint)
        val btnSkip = card.findViewById<MaterialButton>(R.id.btnTourSkip)

        val waitsForTab = step.waitForTabId != null
        val waitsForCustomTap = step.onTargetTapped != null
        when {
            waitsForTab -> {
                btnNext.visibility = View.GONE
                tvHint.visibility = View.VISIBLE
                tvHint.text = activity.getString(R.string.tour_tap_hint_nav)
            }
            waitsForCustomTap -> {
                btnNext.visibility = View.GONE
                tvHint.visibility = View.VISIBLE
                tvHint.text = activity.getString(R.string.tour_tap_hint_add)
            }
            else -> {
                btnNext.visibility = View.VISIBLE
                tvHint.visibility = View.GONE
                btnNext.text = if (step.isFinal) activity.getString(R.string.tour_action_finish)
                               else activity.getString(R.string.onboarding_next)
                btnNext.setOnClickListener { onNextPressed(activity, step) }
            }
        }
        btnSkip.setOnClickListener { cancel(activity) }

        val cardParams: ViewGroup.LayoutParams = if (useCL) {
            androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(
                0,
                androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
                endToEnd = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
                bottomToTop = R.id.navBarContainer
            }
        } else {
            val navBar = activity.findViewById<View>(R.id.navBarContainer)
            val navH = if (navBar?.visibility == View.VISIBLE) navBar.height else 0
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply {
                    gravity = Gravity.BOTTOM
                    bottomMargin = navH
                }
        }
        container.addView(card, cardParams)
        bottomCard = card

        card.post {
            if (activity.isFinishing || activity.isDestroyed) return@post
            scrollTargetAboveCard(activity, target, card)
        }
    }

    private fun scrollTargetAboveCard(activity: FragmentActivity, target: View, card: View) {
        val cardHeight = card.height
        if (cardHeight <= 0) return
        val navBar = activity.findViewById<View>(R.id.navBarContainer)
        val navBarHeight = if (navBar?.visibility == View.VISIBLE) navBar.height else 0
        val screenHeight = activity.resources.displayMetrics.heightPixels
        val cardTop = screenHeight - cardHeight - navBarHeight
        val safetyMargin = (32f * activity.resources.displayMetrics.density).toInt()

        val targetLoc = IntArray(2)
        target.getLocationOnScreen(targetLoc)
        val targetBottom = targetLoc[1] + target.height
        if (targetBottom + safetyMargin <= cardTop) return

        val scrollAmount = (targetBottom + safetyMargin) - cardTop
        val scrollable = findScrollableParent(target)
        when (scrollable) {
            is androidx.core.widget.NestedScrollView -> scrollable.smoothScrollBy(0, scrollAmount)
            is android.widget.ScrollView -> scrollable.smoothScrollBy(0, scrollAmount)
            is androidx.recyclerview.widget.RecyclerView -> scrollable.smoothScrollBy(0, scrollAmount)
            else -> Log.w(TAG, "no scrollable parent")
        }
        Handler(Looper.getMainLooper()).postDelayed({
            overlayView?.refreshCutout()
        }, 400L)
    }

    private fun findScrollableParent(view: View): View? {
        var p: ViewParent? = view.parent
        while (p != null) {
            when (p) {
                is androidx.core.widget.NestedScrollView -> return p
                is android.widget.ScrollView -> return p
                is androidx.recyclerview.widget.RecyclerView -> return p
            }
            p = p.parent
        }
        return null
    }

    private fun onNextPressed(activity: FragmentActivity, step: Step) {
        val nav = savedNavController
        if (step.navigateAfterId != null && nav != null) {
            removeOverlayAndCard(activity)
            runCatching { nav.navigate(step.navigateAfterId) }
                .onFailure { logCrash(activity, "step.$stepIndex.navigateAfter", it) }
            Handler(Looper.getMainLooper()).postDelayed({
                advance(activity)
            }, 500L)
        } else {
            advance(activity)
        }
    }

    private fun advance(activity: FragmentActivity) {
        removeOverlayAndCard(activity)
        val type = activeType ?: return
        val steps = stepsFor(type)
        val current = steps.getOrNull(stepIndex)
        if (current?.isFinal == true) {
            val callback = onCompletedCallback
            markDone(activity, type)
            cleanupInternal(activity)
            showCompletionSnackbar(activity)
            runCatching { callback?.invoke() }
            return
        }
        stepIndex++
        Handler(Looper.getMainLooper()).postDelayed({
            runCurrentStep(activity)
        }, 250L)
    }

    private fun removeOverlayAndCard(activity: FragmentActivity) {
        overlayView?.let { ov -> (ov.parent as? ViewGroup)?.removeView(ov) }
        overlayView = null
        bottomCard?.let { c -> (c.parent as? ViewGroup)?.removeView(c) }
        bottomCard = null
    }

    private fun stepsFor(type: Type): List<Step> = when (type) {
        Type.ADMIN -> adminSteps
        Type.LECTURER -> lecturerSteps
    }

    private fun homeDestFor(type: Type): Int = when (type) {
        Type.ADMIN -> R.id.adminHomeFragment
        Type.LECTURER -> R.id.lecturerHomeFragment
    }

    private fun findFragmentView(activity: FragmentActivity, @IdRes viewId: Int): View? {
        val navHost = activity.supportFragmentManager.findFragmentById(R.id.navHostFragment) ?: return null
        val visible = navHost.childFragmentManager.fragments.firstOrNull { it.isVisible && it.isAdded }
            ?: return null
        return visible.view?.findViewById(viewId)
    }

    private fun showCompletionSnackbar(activity: Activity) {
        runCatching {
            val root = activity.findViewById<View>(android.R.id.content) ?: return
            Snackbar.make(root, R.string.tour_completed, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun logCrash(activity: FragmentActivity, action: String, e: Throwable) {
        runCatching {
            CrashHandler.appendPendingCrash(
                activity.applicationContext, "TourCoordinator", action, e
            )
            CrashHandler.flushPendingCrashes(activity.application)
        }
    }
}
