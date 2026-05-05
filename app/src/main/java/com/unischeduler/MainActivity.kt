package com.unischeduler

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.unischeduler.data.repository.AuthRepository
import com.unischeduler.databinding.ActivityMainBinding
import com.unischeduler.util.NetworkMonitor
import com.unischeduler.util.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

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
            invalidateOptionsMenu()
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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_overflow, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_logout)?.isVisible = session.isLoggedIn
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_logout) {
            logout()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun logout() {
        // Sign out from Supabase Auth
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { AuthRepository().signOut() }
        }
        session.clear()
        // Reset nav-setup flags so the next login re-wires the correct nav
        adminNavReady    = false
        lecturerNavReady = false
        // Hide navs immediately
        binding.bottomNavAdmin.visibility    = View.GONE
        binding.bottomNavLecturer.visibility = View.GONE
        // Clear the whole back stack and land on login
        navController.navigate(
            R.id.loginFragment,
            null,
            NavOptions.Builder()
                .setPopUpTo(navController.graph.id, true)
                .build()
        )
    }

    private fun resolveStartDestination(): Int = when {
        !session.isLoggedIn -> R.id.loginFragment
        session.isAdmin     -> R.id.adminHomeFragment
        else                -> R.id.lecturerHomeFragment
    }
}
