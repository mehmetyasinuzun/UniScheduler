package com.unischeduler.presentation.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.unischeduler.domain.model.UserRole
import com.unischeduler.presentation.auth.LoginScreen
import com.unischeduler.presentation.auth.LoginViewModel
import com.unischeduler.presentation.calendar.AlternativesScreen
import com.unischeduler.presentation.calendar.CalendarScreen
import com.unischeduler.presentation.calendar.CalendarViewModel
import com.unischeduler.presentation.calendar.ScheduleConfigScreen
import com.unischeduler.presentation.data.DataScreen
import com.unischeduler.presentation.data.DataViewModel
import com.unischeduler.presentation.data.ImportPreviewScreen
import com.unischeduler.presentation.drafts.DraftEditorScreen
import com.unischeduler.presentation.drafts.DraftListScreen
import com.unischeduler.presentation.drafts.DraftReviewScreen
import com.unischeduler.presentation.drafts.DraftViewModel
import com.unischeduler.presentation.home.HomeScreen
import com.unischeduler.presentation.home.HomeViewModel
import com.unischeduler.presentation.requests.CreateRequestScreen
import com.unischeduler.presentation.requests.RequestDetailScreen
import com.unischeduler.presentation.requests.RequestListScreen
import com.unischeduler.presentation.settings.SettingsScreen
import com.unischeduler.presentation.settings.SettingsViewModel
import com.unischeduler.presentation.splash.SplashScreen

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // FIX: splashVm yerine açık isimli authVm kullanılıyor, stale state riski yok
    val authVm: LoginViewModel = hiltViewModel()
    val currentUser by authVm.currentUser.collectAsState()
    val userRole = currentUser?.role ?: UserRole.STUDENT

    val showBottomBar = currentRoute != null &&
        currentRoute != Screen.Splash::class.qualifiedName &&
        currentRoute != Screen.Login::class.qualifiedName

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                BottomNavBar(
                    currentRoute = currentRoute,
                    role = userRole,
                    onNavigate = { screen ->
                        navController.navigate(screen) {
                            popUpTo(Screen.Home) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                )
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Splash,
            modifier = Modifier.padding(padding)
        ) {
            composable<Screen.Splash> {
                SplashScreen(
                    onNavigateToLogin = {
                        navController.navigate(Screen.Login) {
                            popUpTo(Screen.Splash) { inclusive = true }
                        }
                    },
                    onNavigateToHome = {
                        navController.navigate(Screen.Home) {
                            popUpTo(Screen.Splash) { inclusive = true }
                        }
                    }
                )
            }
            composable<Screen.Login> {
                val vm: LoginViewModel = hiltViewModel()
                LoginScreen(
                    viewModel = vm,
                    onLoginSuccess = {
                        navController.navigate(Screen.Home) {
                            popUpTo(Screen.Login) { inclusive = true }
                        }
                    }
                )
            }
            composable<Screen.Home> {
                val vm: HomeViewModel = hiltViewModel()
                HomeScreen(
                    viewModel = vm,
                    userRole = userRole,
                    onNavigateToRequests = { navController.navigate(Screen.Requests) },
                    onNavigateToSettings = { navController.navigate(Screen.Settings) }
                )
            }
            composable<Screen.Calendar> {
                val vm: CalendarViewModel = hiltViewModel()
                CalendarScreen(
                    viewModel = vm,
                    userRole = userRole,
                    onNavigateToConfig = { navController.navigate(Screen.ScheduleConfig) },
                    onNavigateToAlternatives = { navController.navigate(Screen.Alternatives) }
                )
            }
            composable<Screen.Data> {
                val vm: DataViewModel = hiltViewModel()
                DataScreen(
                    viewModel = vm,
                    onNavigateToImport = { navController.navigate(Screen.ImportPreview) },
                    onNavigateToCalendar = { navController.navigate(Screen.Calendar) }
                )
            }
            composable<Screen.ImportPreview> {
                val vm: DataViewModel = hiltViewModel()
                ImportPreviewScreen(
                    viewModel = vm,
                    onImportComplete = { navController.popBackStack() }
                )
            }
            composable<Screen.Settings> {
                val vm: SettingsViewModel = hiltViewModel()
                SettingsScreen(
                    viewModel = vm,
                    onLogout = {
                        navController.navigate(Screen.Login) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }
            composable<Screen.ScheduleConfig> {
                val vm: CalendarViewModel = hiltViewModel()
                ScheduleConfigScreen(
                    viewModel = vm,
                    onSaved = { navController.popBackStack() }
                )
            }
            composable<Screen.Alternatives> {
                val vm: CalendarViewModel = hiltViewModel()
                AlternativesScreen(
                    viewModel = vm,
                    onSelect = { navController.popBackStack() }
                )
            }
            composable<Screen.Drafts> {
                val vm: DraftViewModel = hiltViewModel()
                DraftListScreen(
                    viewModel = vm,
                    userRole = userRole,
                    onCreateDraft = { navController.navigate(Screen.DraftEditor()) },
                    onEditDraft = { navController.navigate(Screen.DraftEditor(it)) },
                    onReviewDraft = { navController.navigate(Screen.DraftReview(it)) }
                )
            }
            composable<Screen.DraftEditor> { backStackEntry ->
                val route = backStackEntry.toRoute<Screen.DraftEditor>()
                val vm: DraftViewModel = hiltViewModel()
                DraftEditorScreen(
                    viewModel = vm,
                    draftId = route.draftId,
                    onDone = { navController.popBackStack() }
                )
            }
            composable<Screen.DraftReview> { backStackEntry ->
                val route = backStackEntry.toRoute<Screen.DraftReview>()
                val vm: DraftViewModel = hiltViewModel()
                DraftReviewScreen(
                    viewModel = vm,
                    draftId = route.draftId,
                    onDone = { navController.popBackStack() }
                )
            }
            composable<Screen.Requests> {
                RequestListScreen(
                    userRole = userRole,
                    onCreateRequest = { navController.navigate(Screen.CreateRequest) },
                    onRequestDetail = { navController.navigate(Screen.RequestDetail(it)) }
                )
            }
            composable<Screen.CreateRequest> {
                CreateRequestScreen(
                    onDone = { navController.popBackStack() }
                )
            }
            composable<Screen.RequestDetail> { backStackEntry ->
                val route = backStackEntry.toRoute<Screen.RequestDetail>()
                RequestDetailScreen(
                    requestId = route.requestId,
                    userRole = userRole,
                    onDone = { navController.popBackStack() }
                )
            }
        }
    }
}
