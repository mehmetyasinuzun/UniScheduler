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

        // Honor a pending logout from a previous activity instance: if we got here
        // via the FORCE_LOGOUT extra, ignore any persisted session and land on login.
        val forceLogout = intent?.getBooleanExtra(EXTRA_FORCE_LOGOUT, false) == true
        if (forceLogout) {
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

        navController.addOnDestinationChangedListener { _, destination, _ ->
            // After login the session is populated; wire the nav lazily the first time
            ensureNavSetup()
            val isAuthScreen = destination.id in noNavDestinations
            updateNavVisibility(isAuthScreen)
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

    /** Called on every destination change and after login/logout.
     *  Wires setupWithNavController exactly once per role per lifecycle. */
    private fun ensureNavSetup() {
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
            overridePendingTransition(0, 0)
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
