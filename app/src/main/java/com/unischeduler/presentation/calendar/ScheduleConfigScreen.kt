package com.unischeduler.presentation.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.unischeduler.domain.model.ScheduleConfig
import com.unischeduler.presentation.common.UiState
import com.unischeduler.presentation.common.components.LoadingIndicator

private val dayNames = listOf("Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleConfigScreen(
    viewModel: CalendarViewModel,
    onSaved: () -> Unit
) {
    val calUiState by viewModel.uiState.collectAsState()
    val departmentId = calUiState.user?.departmentId

    var slotDuration by rememberSaveable { mutableIntStateOf(60) }
    var dayStartTime by rememberSaveable { mutableStateOf("08:00") }
    var dayEndTime by rememberSaveable { mutableStateOf("17:00") }
    var activeDays by remember { mutableStateOf(listOf(1, 2, 3, 4, 5)) }
    var initialized by rememberSaveable { mutableStateOf(false) }

    val configState by viewModel.configState.collectAsState()

    if (!initialized && departmentId != null) {
        viewModel.loadConfig(departmentId)
        initialized = true
    }

    when (val s = configState) {
        is UiState.Success -> {
            if (!initialized) return
            val cfg = s.data
            slotDuration = cfg.slotDurationMinutes
            dayStartTime = cfg.dayStartTime
            dayEndTime = cfg.dayEndTime
            activeDays = cfg.activeDays
        }
        else -> {}
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Program Ayarları") }) }
    ) { padding ->
        if (configState is UiState.Loading) {
            LoadingIndicator()
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (departmentId == null) {
                    Text(
                        text = "Bolum bilgisi bulunamadi. Program ayarlari yuklenemiyor.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    return@Column
                }

                Text("Ders Süresi", style = MaterialTheme.typography.titleSmall)
                DurationDropdown(
                    selected = slotDuration,
                    onSelected = { slotDuration = it }
                )

                Text("Gün Başlangıcı", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = dayStartTime,
                    onValueChange = { dayStartTime = it },
                    label = { Text("Başlangıç (HH:mm)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Gün Bitişi", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = dayEndTime,
                    onValueChange = { dayEndTime = it },
                    label = { Text("Bitiş (HH:mm)") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Aktif Günler", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (day in 1..7) {
                        FilterChip(
                            selected = day in activeDays,
                            onClick = {
                                activeDays = if (day in activeDays) activeDays - day else activeDays + day
                            },
                            label = { Text(dayNames[day - 1].take(3)) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        val config = ScheduleConfig(
                            id = (configState as? UiState.Success)?.data?.id ?: 0,
                            departmentId = departmentId,
                            slotDurationMinutes = slotDuration,
                            dayStartTime = dayStartTime,
                            dayEndTime = dayEndTime,
                            activeDays = activeDays.sorted()
                        )
                        viewModel.saveConfig(config)
                        onSaved()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Kaydet")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DurationDropdown(selected: Int, onSelected: (Int) -> Unit) {
    val options = listOf(30, 45, 60, 90, 120)
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = "$selected dakika",
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text("$opt dakika") },
                    onClick = {
                        onSelected(opt)
                        expanded = false
                    }
                )
            }
        }
    }
}
