package com.unischeduler.presentation.requests

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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.unischeduler.domain.model.ApprovalMode
import com.unischeduler.domain.model.ChangeRequest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateRequestScreen(
    onDone: () -> Unit,
    viewModel: RequestViewModel = hiltViewModel()
) {
    var requestType by rememberSaveable { mutableStateOf("") }
    var currentData by rememberSaveable { mutableStateOf("") }
    var requestedData by rememberSaveable { mutableStateOf("") }
    var reason by rememberSaveable { mutableStateOf("") }
    var typeExpanded by rememberSaveable { mutableStateOf(false) }

    val requestTypes = listOf(
        "ZAMAN_DEGISIKLIGI" to "Zaman Değişikliği",
        "SINIF_DEGISIKLIGI" to "Sınıf Değişikliği",
        "DERS_DEGISIKLIGI" to "Ders Değişikliği",
        "DIGER" to "Diğer"
    )

    Scaffold(
        topBar = { TopAppBar(title = { Text("Yeni Değişiklik Talebi") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ExposedDropdownMenuBox(
                expanded = typeExpanded,
                onExpandedChange = { typeExpanded = !typeExpanded }
            ) {
                OutlinedTextField(
                    value = requestTypes.find { it.first == requestType }?.second ?: "",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Talep Türü") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = typeExpanded,
                    onDismissRequest = { typeExpanded = false }
                ) {
                    requestTypes.forEach { (code, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                requestType = code
                                typeExpanded = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = currentData,
                onValueChange = { currentData = it },
                label = { Text("Mevcut Durum") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            OutlinedTextField(
                value = requestedData,
                onValueChange = { requestedData = it },
                label = { Text("Talep Edilen Değişiklik") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it },
                label = { Text("Gerekçe") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    val request = ChangeRequest(
                        lecturerId = 0, // Will be set from VM/backend
                        requestType = requestType,
                        currentData = currentData,
                        requestedData = requestedData,
                        reason = reason,
                        /* no approval mode eq direct admin */
                    )
                    viewModel.createRequest(request)
                    onDone()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = requestType.isNotBlank() && reason.isNotBlank()
            ) {
                Text("Talep Gönder")
            }
        }
    }
}
