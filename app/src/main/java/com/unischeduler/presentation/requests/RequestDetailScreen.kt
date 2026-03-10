package com.unischeduler.presentation.requests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.unischeduler.domain.model.RequestStatus
import com.unischeduler.domain.model.UserRole
import com.unischeduler.presentation.common.components.LoadingIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestDetailScreen(
    requestId: Int,
    userRole: UserRole,
    onDone: () -> Unit,
    viewModel: RequestViewModel = hiltViewModel()
) {
    val request by viewModel.detailState.collectAsState()
    var note by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(requestId) {
        viewModel.loadRequestDetail(requestId)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Talep Detayı") }) }
    ) { padding ->
        if (request == null) {
            LoadingIndicator()
        } else {
            val req = request!!

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = req.requestType,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                InfoCard("Mevcut Durum", req.currentData)
                InfoCard("Talep Edilen", req.requestedData)
                InfoCard("Gerekçe", req.reason)

                // Status Section
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Onay Durumu", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        StatusRow("Admin", req.adminStatus, req.adminNote)
                    }
                }

                // Admin Actions
                val canReview = userRole == UserRole.ADMIN && req.adminStatus == RequestStatus.PENDING

                if (canReview) {
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Not (opsiyonel)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                if (userRole == UserRole.ADMIN) {
                                    viewModel.approveAsAdmin(requestId, note.ifBlank { null })
                                }
                                onDone()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Onayla")
                        }
                        OutlinedButton(
                            onClick = {
                                if (userRole == UserRole.ADMIN) {
                                    viewModel.rejectAsAdmin(requestId, note.ifBlank { "Reddedildi" })
                                }
                                onDone()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Reddet")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, content: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = content, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun StatusRow(label: String, status: RequestStatus, note: String?) {
    val statusText = when (status) {
        RequestStatus.PENDING -> "Bekliyor"
        RequestStatus.APPROVED -> "Onayladı"
        RequestStatus.REJECTED -> "Reddetti"
    }
    val color = when (status) {
        RequestStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
        RequestStatus.APPROVED -> MaterialTheme.colorScheme.primary
        RequestStatus.REJECTED -> MaterialTheme.colorScheme.error
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall)
        Text(text = statusText, style = MaterialTheme.typography.bodySmall, color = color)
    }
    if (note != null) {
        Text(
            text = "  Not: $note",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
