package com.unischeduler.presentation.requests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.unischeduler.domain.model.ChangeRequest
import com.unischeduler.domain.model.RequestStatus
import com.unischeduler.domain.model.UserRole
import com.unischeduler.presentation.common.components.LoadingIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestListScreen(
    userRole: UserRole,
    onCreateRequest: () -> Unit,
    onRequestDetail: (Int) -> Unit,
    viewModel: RequestViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadRequests() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Değişiklik Talepleri") }) },
        floatingActionButton = {
            if (userRole == UserRole.LECTURER) {
                ExtendedFloatingActionButton(
                    onClick = onCreateRequest,
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text("Yeni Talep") }
                )
            }
        }
    ) { padding ->
        if (state.isLoading) {
            LoadingIndicator()
        } else if (state.requests.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Henüz talep bulunmuyor.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.requests) { req ->
                    RequestCard(
                        request = req,
                        onClick = { onRequestDetail(req.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RequestCard(
    request: ChangeRequest,
    onClick: () -> Unit
) {
    val statusColor = when (request.status) {
        RequestStatus.PENDING -> MaterialTheme.colorScheme.tertiaryContainer
        RequestStatus.APPROVED -> MaterialTheme.colorScheme.primaryContainer
        RequestStatus.REJECTED -> MaterialTheme.colorScheme.errorContainer
    }
    val statusLabel = when (request.status) {
        RequestStatus.PENDING -> "Bekliyor"
        RequestStatus.APPROVED -> "Onaylandı"
        RequestStatus.REJECTED -> "Reddedildi"
    }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = statusColor)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = request.requestType,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = request.reason,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row {
                Text(
                    text = "Durum: ${request.adminStatus.name}",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
