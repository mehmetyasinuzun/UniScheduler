package com.unischeduler.presentation.data

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Card
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.unischeduler.domain.model.UserRole
import com.unischeduler.presentation.common.components.LoadingIndicator
import com.unischeduler.presentation.data.components.LecturerAccordion
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataScreen(
    viewModel: DataViewModel,
    onNavigateToImport: () -> Unit,
    onNavigateToCalendar: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.openInputStream(it)?.use { stream ->
                val bytes = stream.readBytes()
                val fileName = uri.lastPathSegment ?: "import.xlsx"
                viewModel.parseExcelFile(bytes, fileName)
                onNavigateToImport()
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Veri Yönetimi") },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, "Yenile")
                    }
                }
            )
        }
    ) { padding ->
        if (state.isLoading) {
            LoadingIndicator()
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Admin/Dept Head: Import Section
                if (state.user?.role == UserRole.ADMIN || state.user?.role == UserRole.DEPT_HEAD) {
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Excel İçe Aktarma",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Ders ve öğretim üyesi bilgilerini Excel dosyasından içe aktarın.",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                ElevatedButton(
                                    onClick = {
                                        filePickerLauncher.launch(
                                            arrayOf(
                                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                                "application/vnd.ms-excel"
                                            )
                                        )
                                    }
                                ) {
                                    Icon(Icons.Filled.UploadFile, null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Excel Dosyası Seç")
                                }
                            }
                        }
                    }

                    // Export Buttons
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Dışa Aktarma",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ElevatedButton(onClick = {
                                        scope.launch {
                                            val bytes = viewModel.exportSchedule()
                                            if (bytes != null) {
                                                snackbarHostState.showSnackbar("Program dışa aktarıldı")
                                            } else {
                                                snackbarHostState.showSnackbar("Dışa aktarma başarısız")
                                            }
                                        }
                                    }) {
                                        Icon(Icons.Filled.Download, null)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Program")
                                    }
                                    ElevatedButton(onClick = {
                                        scope.launch {
                                            val bytes = viewModel.exportCredentials()
                                            if (bytes != null) {
                                                snackbarHostState.showSnackbar("Hesap bilgileri dışa aktarıldı")
                                            } else {
                                                snackbarHostState.showSnackbar("Dışa aktarma başarısız")
                                            }
                                        }
                                    }) {
                                        Icon(Icons.Filled.Download, null)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Hesaplar")
                                    }
                                }
                            }
                        }
                    }
                }

                // Statistics
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Özet Bilgiler",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                StatItem("Dersler", state.courses.size.toString())
                                StatItem("Öğretim Üyeleri", state.lecturers.size.toString())
                                StatItem("Bölümler", state.departments.size.toString())
                            }
                        }
                    }
                }

                // Lecturer list
                item {
                    Text(
                        text = "Öğretim Üyeleri",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                items(state.lecturers) { lecturer ->
                    LecturerAccordion(
                        lecturer = lecturer,
                        courses = state.courses.filter { course ->
                            lecturer.courses.any { it.id == course.id }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
