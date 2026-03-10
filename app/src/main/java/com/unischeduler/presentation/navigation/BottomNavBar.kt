package com.unischeduler.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.unischeduler.R
import com.unischeduler.domain.model.UserRole

data class BottomNavItem(
    val labelRes: Int? = null,
    val label: String? = null,
    val icon: ImageVector,
    val screen: Screen
)

fun getNavItems(role: UserRole, showDebug: Boolean = false): List<BottomNavItem> {
    val items = when (role) {
        UserRole.ADMIN -> listOf(
            BottomNavItem(labelRes = R.string.nav_home, icon = Icons.Filled.Home, screen = Screen.Home),
            BottomNavItem(labelRes = R.string.nav_calendar, icon = Icons.Filled.CalendarMonth, screen = Screen.Calendar),
            BottomNavItem(labelRes = R.string.nav_data, icon = Icons.Filled.Storage, screen = Screen.Data)
        )
        UserRole.LECTURER -> listOf(
            BottomNavItem(labelRes = R.string.nav_home, icon = Icons.Filled.Home, screen = Screen.Home),
            BottomNavItem(labelRes = R.string.nav_calendar, icon = Icons.Filled.CalendarMonth, screen = Screen.Calendar),
            BottomNavItem(labelRes = R.string.nav_requests, icon = Icons.Filled.Description, screen = Screen.Requests)
        )
    }

    return if (showDebug && role == UserRole.ADMIN) {
        items + BottomNavItem(label = "Debug", icon = Icons.Filled.BugReport, screen = Screen.DebugConsole)
    } else {
        items
    }
}

@Composable
fun BottomNavBar(
    currentRoute: String?,
    role: UserRole,
    showDebug: Boolean = false,
    onNavigate: (Screen) -> Unit
) {
    val items = getNavItems(role, showDebug)

    NavigationBar {
        items.forEach { item ->
            // Route comparing fixed to avoid crashes
            val itemRoute = item.screen::class.qualifiedName ?: ""
            val selected = currentRoute != null && (currentRoute == itemRoute || currentRoute.startsWith("$itemRoute/"))

            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(item.screen) },
                icon = { Icon(item.icon, contentDescription = null) },
                label = {
                    if (item.labelRes != null) {
                        Text(stringResource(item.labelRes))
                    } else {
                        Text(item.label ?: "")
                    }
                }
            )
        }
    }
}
