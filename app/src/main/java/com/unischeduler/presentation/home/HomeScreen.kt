package com.unischeduler.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.unischeduler.domain.model.UserRole
import com.unischeduler.presentation.common.components.LoadingIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    userRole: UserRole,
    onNavigateToCalendar: () -> Unit,
    onNavigateToRequests: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("UniScheduler") },
            actions = {
                IconButton(onClick = { viewModel.refresh() }) {
                    Icon(Icons.Filled.Refresh, "Yenile")
                }
                IconButton(onClick = onNavigateToSettings) {
                    Icon(Icons.Filled.Settings, "Ayarlar")
                }
            }
        )

        if (state.isLoading) {
            LoadingIndicator()
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Welcome Section
                Text(
                    text = "Hoş geldiniz,",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${state.user?.name ?: ""} ${state.user?.surname ?: ""}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = when (state.user?.role) {
                        UserRole.ADMIN -> "Sistem Yöneticisi"
                        UserRole.DEPT_HEAD -> "Bölüm Başkanı"
                        UserRole.LECTURER -> "Öğretim Üyesi"
                        UserRole.STUDENT -> "Öğrenci"
                        null -> ""
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                // ADMIN / DEPT_HEAD: istatistik kartları
                if (state.user?.role == UserRole.ADMIN || state.user?.role == UserRole.DEPT_HEAD) {
                    QuickStatCard(
                        icon = Icons.AutoMirrored.Filled.Assignment,
                        title = "Bekleyen Taslaklar",
                        count = state.pendingDraftCount,
                        onClick = {}
                    )
                    QuickStatCard(
                        icon = Icons.Filled.SwapHoriz,
                        title = "Bekleyen Değişiklik Talepleri",
                        count = state.pendingRequestCount,
                        onClick = onNavigateToRequests
                    )
                }

                // LECTURER: ders programı + talepler
                if (state.user?.role == UserRole.LECTURER) {
                    QuickStatCard(
                        icon = Icons.Filled.CalendarMonth,
                        title = "Ders Programım",
                        count = null,
                        onClick = onNavigateToCalendar
                    )
                    QuickStatCard(
                        icon = Icons.Filled.SwapHoriz,
                        title = "Değişiklik Taleplerim",
                        count = null,
                        onClick = onNavigateToRequests
                    )
                }

                // STUDENT: bilgi kartı
                if (state.user?.role == UserRole.STUDENT) {
                    Card(
                        onClick = onNavigateToCalendar,
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Bölüm Ders Programı",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Açık dersleri Takvim sekmesinden görüntüleyebilirsiniz.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickStatCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    count: Int?,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall
                )
            }
            if (count != null && count > 0) {
                Badge(containerColor = MaterialTheme.colorScheme.error) {
                    Text(count.toString())
                }
            }
        }
    }
}
