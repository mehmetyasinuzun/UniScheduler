package com.unischeduler.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Dashboard
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
import com.unischeduler.domain.model.UserRole

data class BottomNavItem(
    val label: String,
    val icon: ImageVector,
    val screen: Screen
)

fun getNavItems(role: UserRole): List<BottomNavItem> = when (role) {
    UserRole.ADMIN -> listOf(
        BottomNavItem("Ana Sayfa", Icons.Filled.Home, Screen.Home),
        BottomNavItem("Takvim", Icons.Filled.CalendarMonth, Screen.Calendar),
        BottomNavItem("Veri", Icons.Filled.Storage, Screen.Data),
        BottomNavItem("Ayarlar", Icons.Filled.Settings, Screen.Settings)
    )
    UserRole.DEPT_HEAD -> listOf(
        BottomNavItem("Ana Sayfa", Icons.Filled.Home, Screen.Home),
        BottomNavItem("Takvim", Icons.Filled.CalendarMonth, Screen.Calendar),
        BottomNavItem("Taslaklar", Icons.Filled.Description, Screen.Drafts),
        BottomNavItem("Ayarlar", Icons.Filled.Settings, Screen.Settings)
    )
    UserRole.LECTURER -> listOf(
        BottomNavItem("Ana Sayfa", Icons.Filled.Home, Screen.Home),
        BottomNavItem("Takvim", Icons.Filled.CalendarMonth, Screen.Calendar)
    )
    UserRole.STUDENT -> listOf(
        BottomNavItem("Ana Sayfa", Icons.Filled.Home, Screen.Home),
        BottomNavItem("Takvim", Icons.Filled.CalendarMonth, Screen.Calendar)
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
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) }
            )
        }
    }
}
