package com.unischeduler.presentation.data

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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.unischeduler.domain.model.Lecturer
import com.unischeduler.domain.model.UserRole
import com.unischeduler.presentation.common.components.LoadingIndicator
import com.unischeduler.presentation.data.components.LecturerAccordion
import com.unischeduler.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MAX_EXCEL_SIZE_BYTES = 20L * 1024 * 1024

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

    var showAddDialog by remember { mutableStateOf(false) }
    var editingLecturer by remember { mutableStateOf<Lecturer?>(null) }
    var showDeleteConfirm by remember { mutableStateOf<Int?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult

        scope.launch {
            try {
                val mime = context.contentResolver.getType(uri).orEmpty()
                val isAllowedMime = mime == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" ||
                    mime == "application/vnd.ms-excel" || mime.isBlank()
                if (!isAllowedMime) {
                    snackbarHostState.showSnackbar("Sadece Excel dosyaları (.xlsx/.xls) desteklenir")
                    return@launch
                }

                val declaredSize = queryFileSize(context, uri)
                if (declaredSize != null && declaredSize > MAX_EXCEL_SIZE_BYTES) {
                    snackbarHostState.showSnackbar("Dosya çok büyük. En fazla 20 MB desteklenir.")
                    return@launch
                }

                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.readBytes()
                    } ?: throw IllegalStateException("Dosya acilamadi")
                }

                if (bytes.isEmpty()) {
                    snackbarHostState.showSnackbar("Dosya boş görünüyor")
                    return@launch
                }

                if (bytes.size > MAX_EXCEL_SIZE_BYTES) {
                    snackbarHostState.showSnackbar("Dosya çok büyük. En fazla 20 MB desteklenir.")
                    return@launch
                }

                val fileName = queryFileName(context, uri) ?: uri.lastPathSegment ?: "import.xlsx"
                AppLogger.i("DataScreen", "Excel dosyasi secildi: $fileName (${bytes.size} byte)")
                viewModel.parseExcelFile(bytes, fileName)
                onNavigateToImport()
            } catch (e: Exception) {
                AppLogger.e("DataScreen", "Excel dosyasi okunamadi: ${e.message}", e)
                snackbarHostState.showSnackbar("Dosya acilamadi. Lutfen farkli bir Excel dosyasi secin.")
            }
        }
    }

    // Add/Edit Dialog
    if (showAddDialog || editingLecturer != null) {
        LecturerDialog(
            lecturer = editingLecturer,
            departments = state.departments,
            onDismiss = {
                showAddDialog = false
                editingLecturer = null
            },
            onSave = { fullName, title, deptId ->
                if (editingLecturer != null) {
                    viewModel.updateLecturer(
                        editingLecturer!!.copy(
                            fullName = fullName,
                            title = title,
                            departmentId = deptId
                        )
                    )
                } else {
                    viewModel.addLecturer(fullName, title, deptId)
                }
                showAddDialog = false
                editingLecturer = null
            }
        )
    }

    // Delete Confirmation
    if (showDeleteConfirm != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Öğretim Üyesi Sil") },
            text = { Text("Bu öğretim üyesini silmek istediğinize emin misiniz? Bu işlem geri alınamaz.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteLecturer(showDeleteConfirm!!)
                        showDeleteConfirm = null
                    }
                ) {
                    Text("Sil", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text("İptal")
                }
            }
        )
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
                if (state.user?.role == UserRole.ADMIN || state.user?.role == UserRole.ADMIN) {
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

                // Lecturer list header with Add button
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Öğretim Üyeleri",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (state.user?.role == UserRole.ADMIN || state.user?.role == UserRole.ADMIN) {
                            ElevatedButton(onClick = { showAddDialog = true }) {
                                Icon(Icons.Filled.PersonAdd, null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Hoca Ekle")
                            }
                        }
                    }
                }

                items(state.lecturers) { lecturer ->
                    val isAdmin = state.user?.role == UserRole.ADMIN || state.user?.role == UserRole.ADMIN
                    LecturerAccordion(
                        lecturer = lecturer,
                        courses = state.courses.filter { course ->
                            lecturer.courses.any { it.id == course.id }
                        },
                        onEdit = if (isAdmin) { l -> editingLecturer = l } else null,
                        onDelete = if (isAdmin) { id -> showDeleteConfirm = id } else null
                    )
                }
            }
        }
    }
}

private fun queryFileName(context: android.content.Context, uri: Uri): String? {
    return runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIdx >= 0 && cursor.moveToFirst()) cursor.getString(nameIdx) else null
        }
    }.getOrNull()
}

private fun queryFileSize(context: android.content.Context, uri: Uri): Long? {
    return runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val sizeIdx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
            if (sizeIdx >= 0 && cursor.moveToFirst()) cursor.getLong(sizeIdx) else null
        }
    }.getOrNull()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LecturerDialog(
    lecturer: Lecturer?,
    departments: List<com.unischeduler.domain.model.Department>,
    onDismiss: () -> Unit,
    onSave: (fullName: String, title: String, departmentId: Int) -> Unit
) {
    var fullName by remember { mutableStateOf(lecturer?.fullName ?: "") }
    var title by remember { mutableStateOf(lecturer?.title ?: "") }
    var selectedDeptId by remember { mutableStateOf(lecturer?.departmentId ?: departments.firstOrNull()?.id ?: 1) }
    var deptExpanded by remember { mutableStateOf(false) }

    val titleOptions = listOf(
        "", "Prof. Dr.", "Doç. Dr.", "Dr. Öğr. Üyesi",
        "Arş. Gör. Dr.", "Arş. Gör.", "Öğr. Gör. Dr.", "Öğr. Gör."
    )
    var titleExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (lecturer != null) "Öğretim Üyesi Düzenle" else "Yeni Öğretim Üyesi") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Title dropdown
                ExposedDropdownMenuBox(
                    expanded = titleExpanded,
                    onExpandedChange = { titleExpanded = it }
                ) {
                    OutlinedTextField(
                        value = title.ifEmpty { "Unvan seçin" },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Unvan") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = titleExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = titleExpanded,
                        onDismissRequest = { titleExpanded = false }
                    ) {
                        titleOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.ifEmpty { "(Unvan yok)" }) },
                                onClick = {
                                    title = option
                                    titleExpanded = false
                                }
                            )
                        }
                    }
                }

                // Full name
                OutlinedTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = { Text("Ad Soyad") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Department dropdown
                if (departments.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = deptExpanded,
                        onExpandedChange = { deptExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = departments.find { it.id == selectedDeptId }?.name ?: "Bölüm seçin",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Bölüm") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = deptExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = deptExpanded,
                            onDismissRequest = { deptExpanded = false }
                        ) {
                            departments.forEach { dept ->
                                DropdownMenuItem(
                                    text = { Text("${dept.code} - ${dept.name}") },
                                    onClick = {
                                        selectedDeptId = dept.id
                                        deptExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (fullName.isNotBlank()) {
                        onSave(fullName.trim(), title, selectedDeptId)
                    }
                },
                enabled = fullName.isNotBlank()
            ) {
                Text(if (lecturer != null) "Güncelle" else "Ekle")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("İptal")
            }
        }
    )
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
