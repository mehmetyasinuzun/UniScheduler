package com.unischeduler.presentation.drafts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import com.unischeduler.presentation.common.UiState
import com.unischeduler.presentation.common.components.LoadingIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DraftEditorScreen(
    viewModel: DraftViewModel,
    draftId: Int,
    onDone: () -> Unit
) {
    val editState by viewModel.editState.collectAsState()
    val assignments by viewModel.currentAssignments.collectAsState()
    var title by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(draftId) {
        viewModel.loadDraft(draftId)
    }

    LaunchedEffect(editState) {
        if (editState is UiState.Success) {
            val draft = (editState as UiState.Success).data
            if (title.isEmpty()) title = draft.title
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (draftId == 0) "Yeni Taslak" else "Taslak Düzenle") }
            )
        }
    ) { padding ->
        when (val state = editState) {
            is UiState.Loading -> LoadingIndicator()
            is UiState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp)
                ) {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            is UiState.Success -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Taslak Başlığı") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "Atamalar (${assignments.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    assignments.forEach { assignment ->
                        Text(
                            text = "${assignment.courseCode} - ${assignment.courseName} | ${assignment.lecturerName} | Gün: ${assignment.dayOfWeek} Slot: ${assignment.slotIndex}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            viewModel.saveDraft(title)
                            onDone()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = title.isNotBlank()
                    ) {
                        Text("Kaydet")
                    }
                }
            }
            is UiState.Empty -> {}
        }
    }
}
