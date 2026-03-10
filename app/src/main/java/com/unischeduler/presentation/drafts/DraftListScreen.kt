package com.unischeduler.presentation.drafts

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.unischeduler.domain.model.DraftStatus
import com.unischeduler.domain.model.ScheduleDraft
import com.unischeduler.domain.model.UserRole
import com.unischeduler.presentation.common.components.LoadingIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DraftListScreen(
    viewModel: DraftViewModel,
    userRole: UserRole,
    onCreateDraft: () -> Unit,
    onEditDraft: (Int) -> Unit,
    onReviewDraft: (Int) -> Unit
) {
    val state by viewModel.listState.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Taslaklar") }) },
        floatingActionButton = {
            if (userRole == UserRole.ADMIN) {
                ExtendedFloatingActionButton(
                    onClick = onCreateDraft,
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text("Yeni Taslak") }
                )
            }
        }
    ) { padding ->
        if (state.isLoading) {
            LoadingIndicator()
        } else if (state.drafts.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Henüz taslak bulunmuyor.",
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
                items(state.drafts) { draft ->
                    DraftCard(
                        draft = draft,
                        userRole = userRole,
                        onEdit = { onEditDraft(draft.id) },
                        onReview = { onReviewDraft(draft.id) },
                        onSubmit = { viewModel.submitDraft(draft.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DraftCard(
    draft: ScheduleDraft,
    userRole: UserRole,
    onEdit: () -> Unit,
    onReview: () -> Unit,
    onSubmit: () -> Unit
) {
    val statusColor = when (draft.status) {
        DraftStatus.DRAFT -> MaterialTheme.colorScheme.surfaceVariant
        DraftStatus.PENDING -> MaterialTheme.colorScheme.tertiaryContainer
        DraftStatus.APPROVED -> MaterialTheme.colorScheme.primaryContainer
        DraftStatus.REJECTED -> MaterialTheme.colorScheme.errorContainer
    }

    val statusLabel = when (draft.status) {
        DraftStatus.DRAFT -> "Taslak"
        DraftStatus.PENDING -> "Onay Bekliyor"
        DraftStatus.APPROVED -> "Onaylandı"
        DraftStatus.REJECTED -> "Reddedildi"
    }

    Card(
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
                    text = draft.title.ifBlank { "İsimsiz Taslak" },
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
                text = "${draft.assignments.size} atama | Skor: %.1f".format(draft.softScore),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (draft.adminNote != null) {
                Text(
                    text = "Not: ${draft.adminNote}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row {
                when {
                    draft.status == DraftStatus.DRAFT && userRole == UserRole.ADMIN -> {
                        TextButton(onClick = onEdit) { Text("Düzenle") }
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(onClick = onSubmit) { Text("Gönder") }
                    }
                    draft.status == DraftStatus.PENDING && userRole == UserRole.ADMIN -> {
                        TextButton(onClick = onReview) { Text("İncele") }
                    }
                    else -> {
                        TextButton(onClick = onEdit) { Text("Görüntüle") }
                    }
                }
            }
        }
    }
}
