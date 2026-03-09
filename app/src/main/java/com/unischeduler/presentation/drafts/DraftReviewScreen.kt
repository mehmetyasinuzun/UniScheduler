package com.unischeduler.presentation.drafts

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
import com.unischeduler.presentation.common.UiState
import com.unischeduler.presentation.common.components.LoadingIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DraftReviewScreen(
    viewModel: DraftViewModel,
    draftId: Int,
    onDone: () -> Unit
) {
    val editState by viewModel.editState.collectAsState()
    val assignments by viewModel.currentAssignments.collectAsState()
    var note by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(draftId) {
        viewModel.loadDraft(draftId)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Taslak İnceleme") }) }
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
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                }
            }
            is UiState.Success -> {
                val draft = state.data

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = draft.title.ifBlank { "İsimsiz Taslak" },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Skor: %.1f | Atama: ${assignments.size}".format(draft.softScore),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "Atama Detayları",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )

                    assignments.forEach { a ->
                        Text(
                            text = "${a.courseCode} ${a.courseName} → ${a.lecturerName} | G:${a.dayOfWeek} S:${a.slotIndex} ${a.classroom}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Not (opsiyonel)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.approveDraft(draftId, note.ifBlank { null })
                                onDone()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Onayla")
                        }
                        OutlinedButton(
                            onClick = {
                                viewModel.rejectDraft(draftId, note.ifBlank { null })
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
            is UiState.Empty -> {}
        }
    }
}
