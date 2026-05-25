package com.unischeduler

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.unischeduler.data.remote.SupabaseClient
import com.unischeduler.databinding.ActivityMainBinding
import com.unischeduler.util.NetworkMonitor
import com.unischeduler.util.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController
    private lateinit var session: SessionManager
    private lateinit var networkMonitor: NetworkMonitor

    // Track whether each nav has been wired so we don't call setupWithNavController twice
    private var adminNavReady    = false
    private var lecturerNavReady = false

    private val noNavDestinations = setOf(
        R.id.loginFragment,
        R.id.passwordChangeFragment
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = SessionManager(this)

        // First-launch onboarding (B14). Shown only once across the app's
        // lifetime, gated by a SharedPreferences flag — survives app
        // upgrades, cleared on data wipe.
        if (!session.isLoggedIn &&
            com.unischeduler.ui.onboarding.OnboardingActivity.isPending(this)
        ) {
            com.unischeduler.ui.onboarding.OnboardingActivity.start(this)
        }

        // SPOTLIGHT TOUR tetikleme — destination listener'da (aşağıda) yapılıyor.
        //
        // ESKİ KOD (kaldırıldı) onCreate içinden tek seferlik start() çağırıyordu.
        // BUG: kullanıcı LoginFragment'tan Home'a navigate olduğunda MainActivity
        // recreate olmuyor → onCreate tekrar çalışmıyor → tur hiç başlamıyor.
        // Kullanıcı bunu "ilk girişte göremedim" diye raporladı.
        //
        // YENİ: navController.addOnDestinationChangedListener içinde Home
        // destination'a varıldığında maybeStartTour() çağrılıyor. Hem cold
        // start (logged-in startDestination=Home) hem login submit sonrası
        // (LoginFragment → Home) tek noktadan kapsanır.

        // Honor a pending logout from a previous activity instance: if we got here
        // via the FORCE_LOGOUT extra, ignore any persisted session and land on login.
        val forceLogout = intent?.getBooleanExtra(EXTRA_FORCE_LOGOUT, false) == true
        if (forceLogout) {
            session.clear()
        }

        // Session sağlık kontrolü — eski APK'dan kalan yarı yazılı session
        // (userId var ama orgId yok / role boş / lecturerId eksik) varsa
        // temizle ve kullanıcıyı login'e yönlendir. Yoksa LecturerHome'da
        // "Bu hesap bir kuruma bağlı değil", PDF export'ta "Aktif oturum
        // bulunamadı" gibi hayalet hatalar yaşanıyordu.
        if (session.isLoggedIn && !session.isHealthy()) {
            android.util.Log.w(
                "MainActivity",
                "Unhealthy session detected — clearing. " +
                "userId='${session.userId.take(8)}…' orgId=${session.orgId} " +
                "role='${session.role}' lecturerId=${session.lecturerId}"
            )
            session.clear()
        }

        val navHost = supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as NavHostFragment
        navController = navHost.navController

        val graph = navController.navInflater.inflate(R.navigation.nav_graph)
        graph.setStartDestination(resolveStartDestination())
        navController.setGraph(graph, intent.extras)

        // Wire bottom navs for the current session (if already logged in)
        ensureNavSetup()

        // Login akışı tamamlandıktan sonra (veya zaten oturum açıkken)
        // birikmiş crash dosyalarını DB'ye gönder. App.onCreate'te bunu
        // yaparken JWT olmadığı için Postgrest insert kendi içinde NPE
        // atıyordu — destination listener login sonrası tetiklenince
        // burada tekrar deniyoruz.
        navController.addOnDestinationChangedListener { _, _, _ ->
            if (session.isLoggedIn) {
                com.unischeduler.util.CrashHandler.flushPendingCrashes(application)
            }
        }

        navController.addOnDestinationChangedListener { _, destination, _ ->
            // After login the session is populated; wire the nav lazily the first time
            ensureNavSetup()
            val isAuthScreen = destination.id in noNavDestinations
            updateNavVisibility(isAuthScreen)
            // Spotlight tour — Home destination'a ilk varışta (cold-start veya
            // login submit sonrası) flag'e bakarak başlatır. Idempotent.
            maybeStartTour(destination.id)
        }

        // ROTATION FIX — Activity recreate (configuration change) sonrası
        // addOnDestinationChangedListener attach'ta otomatik tetiklenmediği
        // için bottom nav görünürlüğü "stale" XML değerinde kalıyordu
        // (bottomNavAdmin XML'de varsayılan android:visibility="gone"). Bu
        // yüzden telefonu yan çevirip dik döndürünce alt bar kayboluyordu.
        //
        // Mevcut destination'a göre manuel olarak visibility'yi hesaplayıp
        // uygulamak rotation senaryosunu kalıcı çözer. İlk açılışta da
        // (savedInstanceState null) zararsız — destination=startDestination
        // değerini doğru günceller.
        navController.currentDestination?.let { dest ->
            updateNavVisibility(dest.id in noNavDestinations)
        }

        // Network monitor — offline banner
        networkMonitor = NetworkMonitor(this)
        networkMonitor.start()
        lifecycleScope.launch {
            networkMonitor.isOnline.collectLatest { online ->
                binding.offlineBanner.visibility = if (online) View.GONE else View.VISIBLE
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        networkMonitor.stop()
    }

    /**
     * Spotlight tour'u Home destination'a varıldığında — eğer kullanıcının
     * rolü için ilk kez ise — başlatır. Idempotent: aktif bir tur varsa
     * (selectedItemId değişimi sonrası tetiklenen destination change) erken
     * çıkar; flag (adminTutorialDone/lecturerTutorialDone) true ise atlanır.
     *
     * 600 ms gecikme: bottom-nav setupWithNavController + fragment ilk render
     * için marj. Daha kısa olursa TapTargetView "target view not laid out
     * yet" hatasıyla skip edebilir.
     */
    @androidx.annotation.UiThread
    private fun maybeStartTour(@androidx.annotation.IdRes destinationId: Int) {
        val running = com.unischeduler.util.TourCoordinator.isRunning
        val prefs = com.unischeduler.util.TutorialPrefs(this)
        android.util.Log.d(
            "MainActivity.tour",
            "maybeStartTour dest=$destinationId running=$running loggedIn=${session.isLoggedIn} " +
            "healthy=${session.isHealthy()} isAdmin=${session.isAdmin} isLect=${session.isLecturer} " +
            "adminDone=${prefs.adminTutorialDone} lectDone=${prefs.lecturerTutorialDone}"
        )
        if (running) return
        if (!session.isLoggedIn || !session.isHealthy()) return
        val isHomeDest = destinationId == R.id.adminHomeFragment ||
                         destinationId == R.id.lecturerHomeFragment
        if (!isHomeDest) return
        val tourType = when {
            session.isAdmin && !prefs.adminTutorialDone ->
                com.unischeduler.util.TourCoordinator.Type.ADMIN
            session.isLecturer && !prefs.lecturerTutorialDone ->
                com.unischeduler.util.TourCoordinator.Type.LECTURER
            else -> null
        } ?: return
        // ADMIN tour'da MockStore aktifleştirilir; fragment'lar mock veriyi
        // adapter'a union eder. LECTURER için mock yok (sade bilgilendirici).
        if (tourType == com.unischeduler.util.TourCoordinator.Type.ADMIN) {
            com.unischeduler.util.TourMockStore.clear()
        }
        binding.root.postDelayed({
            runCatching {
                if (isFinishing || isDestroyed) return@runCatching
                com.unischeduler.util.CrashHandler.flushPendingCrashes(application)
                com.unischeduler.util.TourCoordinator.start(
                    this, navController, tourType,
                    onCompleted = {
                        // Tur bitti — mock veriler temizlenir, fragment listener'ları
                        // adapter'ı gerçek liste ile yeniden submit eder.
                        if (tourType == com.unischeduler.util.TourCoordinator.Type.ADMIN) {
                            com.unischeduler.util.TourMockStore.clear()
                        }
                    }
                )
            }.onFailure { e ->
                android.util.Log.e("MainActivity", "Tour start failed: ${e.message}", e)
                runCatching {
                    com.unischeduler.util.CrashHandler.appendPendingCrash(
                        applicationContext, "MainActivity", "tour.start", e
                    )
                    com.unischeduler.util.CrashHandler.flushPendingCrashes(application)
                }
            }
        }, 600L)
    }

    /** Called on every destination change and after login/logout.
     *  Wires setupWithNavController exactly once per role per lifecycle. */
    private fun ensureNavSetup() {
        // Diagnostic — session.role bazen tema/dil değişimi sonrası boş kalıyor
        // gibi davranıyordu; Logcat'te bunu doğrulamak için. Filter:
        // adb logcat | grep "MainActivity.nav"
        android.util.Log.d(
            "MainActivity.nav",
            "ensureNavSetup — loggedIn=${session.isLoggedIn} role='${session.role}' " +
            "isAdmin=${session.isAdmin} isLecturer=${session.isLecturer} " +
            "adminReady=$adminNavReady lecturerReady=$lecturerNavReady"
        )
        if (session.isAdmin && !adminNavReady) {
            binding.bottomNavAdmin.setupWithNavController(navController)
            adminNavReady = true
        }
        if (session.isLecturer && !lecturerNavReady) {
            binding.bottomNavLecturer.setupWithNavController(navController)
            lecturerNavReady = true
        }
    }

    private fun updateNavVisibility(isAuthScreen: Boolean) {
        // Defensive: visibility hesaplamadan önce nav setup'ı bir kez daha
        // garantile. session.role login akışında apply() ile yazılıyor; nadir
        // bir senaryoda destination listener buradan ÖNCE tetiklenirse role
        // henüz okunmamış olabilir. ensureNavSetup idempotent — zaten ready
        // olanları atlar.
        ensureNavSetup()
        binding.bottomNavAdmin.visibility    = if (!isAuthScreen && session.isAdmin)    View.VISIBLE else View.GONE
        binding.bottomNavLecturer.visibility = if (!isAuthScreen && session.isLecturer) View.VISIBLE else View.GONE
    }

    // Logout is reachable through SettingsFragment (admin) and LecturerHomeFragment
    // (lecturer) buttons. The activity has no ActionBar, so no overflow menu.

    fun logout() {
        // Two-phase teardown so the next login starts from a clean slate:
        //   1. Tear down every Realtime channel and sign out from GoTrue so no
        //      cached JWT or live subscription leaks across accounts.
        //   2. Clear EncryptedSharedPreferences.
        //   3. Restart the activity stack — this destroys every ViewModel
        //      (including in-memory lists like allCourses/allLecturers) which
        //      otherwise survive a simple navigate-to-login.
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { SupabaseClient.resetForLogout() }
                // Cancel any pending reminders so the next user (possibly a
                // different lecturer on a shared device) doesn't inherit
                // alarms scheduled against the previous account's lecturer_id.
                runCatching { com.unischeduler.notif.ReminderScheduler.cancelAll(applicationContext) }
            }
            session.clear()
            adminNavReady    = false
            lecturerNavReady = false

            val restart = Intent(this@MainActivity, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                putExtra(EXTRA_FORCE_LOGOUT, true)
            }
            startActivity(restart)
            finish()
            // overridePendingTransition is deprecated on API 34+; the new
            // overrideActivityTransition provides equivalent fade-zero
            // animation without the lint warning.
            if (android.os.Build.VERSION.SDK_INT >= 34) {
                overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            } else {
                @Suppress("DEPRECATION")
                overridePendingTransition(0, 0)
            }
        }
    }

    private fun resolveStartDestination(): Int = when {
        !session.isLoggedIn -> R.id.loginFragment
        session.isAdmin     -> R.id.adminHomeFragment
        else                -> R.id.lecturerHomeFragment
    }

    companion object {
        private const val EXTRA_FORCE_LOGOUT = "force_logout"
    }
}
