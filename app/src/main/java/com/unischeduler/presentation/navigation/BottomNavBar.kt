package com.unischeduler.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
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
    val labelRes: Int,
    val icon: ImageVector,
    val screen: Screen
)

fun getNavItems(role: UserRole): List<BottomNavItem> = when (role) {
    UserRole.ADMIN -> listOf(
        BottomNavItem(R.string.nav_home, Icons.Filled.Home, Screen.Home),
        BottomNavItem(R.string.nav_calendar, Icons.Filled.CalendarMonth, Screen.Calendar),
        BottomNavItem(R.string.nav_data, Icons.Filled.Storage, Screen.Data),
        BottomNavItem(R.string.nav_settings, Icons.Filled.Settings, Screen.Settings)
    )
    UserRole.DEPT_HEAD -> listOf(
        BottomNavItem(R.string.nav_home, Icons.Filled.Home, Screen.Home),
        BottomNavItem(R.string.nav_calendar, Icons.Filled.CalendarMonth, Screen.Calendar),
        BottomNavItem(R.string.nav_drafts, Icons.Filled.Description, Screen.Drafts),
        BottomNavItem(R.string.nav_settings, Icons.Filled.Settings, Screen.Settings)
    )
    UserRole.LECTURER -> listOf(
        BottomNavItem(R.string.nav_home, Icons.Filled.Home, Screen.Home),
        BottomNavItem(R.string.nav_calendar, Icons.Filled.CalendarMonth, Screen.Calendar),
        BottomNavItem(R.string.nav_requests, Icons.Filled.Description, Screen.Requests),
        BottomNavItem(R.string.nav_settings, Icons.Filled.Settings, Screen.Settings)
    )
    UserRole.STUDENT -> listOf(
        BottomNavItem(R.string.nav_home, Icons.Filled.Home, Screen.Home),
        BottomNavItem(R.string.nav_calendar, Icons.Filled.CalendarMonth, Screen.Calendar)
    )
}

@Composable
fun BottomNavBar(
    currentRoute: String?,
    role: UserRole,
    onNavigate: (Screen) -> Unit
) {
    val items = getNavItems(role)

    NavigationBar {
        items.forEach { item ->
            val selected = currentRoute == item.screen::class.qualifiedName
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(item.screen) },
                icon = { Icon(item.icon, contentDescription = null) },
                label = { Text(stringResource(item.labelRes)) }
            )
        }
    }
}
