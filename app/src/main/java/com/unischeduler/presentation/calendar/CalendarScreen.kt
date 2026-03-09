package com.unischeduler.presentation.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.unischeduler.domain.model.UserRole
import com.unischeduler.presentation.calendar.components.AvailabilityGrid
import com.unischeduler.presentation.calendar.components.WeeklyGrid
import com.unischeduler.presentation.common.components.LoadingIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    userRole: UserRole,
    onNavigateToConfig: () -> Unit,
    onNavigateToAlternatives: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    val isLecturer = userRole == UserRole.LECTURER
    val tabs = if (isLecturer) listOf("Program", "Müsaitlik") else listOf("Program")

    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Ders Programı") },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, "Yenile")
                    }
                    if (userRole == UserRole.ADMIN || userRole == UserRole.DEPT_HEAD) {
                        IconButton(onClick = onNavigateToConfig) {
                            Icon(Icons.Filled.Settings, "Program Ayarları")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if ((userRole == UserRole.ADMIN || userRole == UserRole.DEPT_HEAD) && selectedTab == 0) {
                ExtendedFloatingActionButton(
                    onClick = {
                        viewModel.generateAlternatives()
                        onNavigateToAlternatives()
                    },
                    icon = { Icon(Icons.Filled.AutoAwesome, null) },
                    text = { Text("Program Oluştur") }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (tabs.size > 1) {
                TabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) }
                        )
                    }
                }
            }

            if (state.isLoading) {
                LoadingIndicator()
            } else {
                when (selectedTab) {
                    0 -> ScheduleTab(state, viewModel, userRole)
                    1 -> AvailabilityTab(state, viewModel)
                }
            }
        }
    }
}

@Composable
private fun ScheduleTab(
    state: CalendarUiState,
    viewModel: CalendarViewModel,
    userRole: UserRole
) {
    val config = state.config

    if (config == null) {
        Box(
            modifier = Modifier.fillMaxSize().padding(16.dp)
        ) {
            Text(
                text = "Program ayarları henüz yapılandırılmamış. Ayarlar sekmesinden yapılandırın.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(8.dp)
        ) {
            WeeklyGrid(
                assignments = state.assignments,
                config = config,
                canEdit = userRole == UserRole.ADMIN || userRole == UserRole.DEPT_HEAD,
                onToggleLock = { viewModel.toggleAssignmentLock(it) }
            )
        }
    }
}

@Composable
private fun AvailabilityTab(
    state: CalendarUiState,
    viewModel: CalendarViewModel
) {
    val config = state.config

    if (config == null) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text("Program ayarları henüz yapılandırılmamış.")
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(8.dp)
        ) {
            Text(
                text = "Müsaitlik Durumunuz",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(8.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            AvailabilityGrid(
                availability = state.myAvailability,
                config = config,
                onToggle = { updatedSlots -> viewModel.updateMyAvailability(updatedSlots) }
            )
        }
    }
}
