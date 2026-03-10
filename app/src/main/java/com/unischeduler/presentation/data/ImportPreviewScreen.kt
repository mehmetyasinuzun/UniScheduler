package com.unischeduler.presentation.data

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportPreviewScreen(
    viewModel: DataViewModel,
    onImportComplete: () -> Unit
) {
    val state by viewModel.importState.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("İçe Aktarma Önizleme") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            if (state.importResult != null) {
                // Import completed
                val result = state.importResult!!
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(16.dp)
                    )
                    Text(
                        text = "İçe Aktarma Tamamlandı",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text("${result.courses.size} ders, ${result.lecturers.size} öğretim üyesi eklendi.")
                    if (result.errors.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Uyarılar:", fontWeight = FontWeight.Bold)
                                result.errors.forEach { err ->
                                    Text("• $err", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = {
                        viewModel.clearImportState()
                        onImportComplete()
                    }) {
                        Text("Tamam")
                    }
                }
            } else {
                // Preview & Import
                if (state.isParsing) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                        Text("Excel dosyası analiz ediliyor...")
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                Text(
                    text = "Dosya: ${state.fileName}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${state.rows.size} satır bulundu",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (state.error != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Error, null, tint = MaterialTheme.colorScheme.error)
                            Text(
                                text = state.error!!,
                                modifier = Modifier.padding(start = 8.dp),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Ders Kodu", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text("Ders Adı", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f))
                            Text("Öğretim Üyesi", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        }
                    }
                    items(state.rows) { row ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                        ) {
                            Text(row.courseCode, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            Text(row.courseName, modifier = Modifier.weight(1.5f), style = MaterialTheme.typography.bodySmall)
                            Text(row.lecturerName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { viewModel.executeImport() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isImporting && !state.isParsing && state.rows.isNotEmpty()
                ) {
                    if (state.isImporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 8.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    Text(
                        when {
                            state.isParsing -> "Dosya analiz ediliyor..."
                            state.isImporting -> "İçe aktarılıyor..."
                            else -> "İçe Aktar"
                        }
                    )
                }
            }
        }
    }
}
