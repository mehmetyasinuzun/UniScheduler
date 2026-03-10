package com.unischeduler.presentation.settings

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.unischeduler.R
import com.unischeduler.domain.model.AppLanguage
import com.unischeduler.domain.model.AppTheme
import com.unischeduler.domain.model.DeptHeadPermission
import com.unischeduler.domain.model.UserRole
import com.unischeduler.presentation.common.components.LoadingIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onLogout: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val permissionUpdatedMsg = stringResource(R.string.permission_updated)

    LaunchedEffect(state.message) {
        state.message?.let { msg ->
            val display = if (msg == "permission_updated") permissionUpdatedMsg else msg
            snackbarHostState.showSnackbar(display)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) }
    ) { padding ->
        if (state.isLoading) {
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
                // --- Hesap Bilgileri ---
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.user_info),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("${stringResource(R.string.account_name)}: ${state.user?.name ?: "-"} ${state.user?.surname ?: ""}")
                        Text("${stringResource(R.string.account_email)}: ${state.user?.email ?: "-"}")
                        Text(
                            "${stringResource(R.string.account_role)}: ${
                                when (state.user?.role) {
                                    UserRole.ADMIN -> stringResource(R.string.role_admin)
                                    UserRole.DEPT_HEAD -> stringResource(R.string.role_dept_head)
                                    UserRole.LECTURER -> stringResource(R.string.role_lecturer)
                                    UserRole.STUDENT -> stringResource(R.string.role_student)
                                    null -> "-"
                                }
                            }"
                        )
                    }
                }

                // --- Görünüm / Appearance ---
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.appearance_section),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(R.string.theme_label), style = MaterialTheme.typography.labelLarge)
                        Spacer(modifier = Modifier.height(4.dp))
                        ThemeDropdown(selected = state.settings.theme, onSelected = { viewModel.setTheme(it) })
                    }
                }

                // --- Dil / Language ---
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.language_section),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(stringResource(R.string.language_label), style = MaterialTheme.typography.labelLarge)
                        Spacer(modifier = Modifier.height(4.dp))
                        LanguageDropdown(selected = state.settings.language, onSelected = { viewModel.setLanguage(it) })
                    }
                }

                // --- Bildirimler / Notifications ---
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.notifications_section),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.notifications_enabled), style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    stringResource(R.string.notifications_enabled_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = state.settings.notificationsEnabled,
                                onCheckedChange = { viewModel.setNotificationsEnabled(it) }
                            )
                        }
                        if (state.settings.notificationsEnabled) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(stringResource(R.string.notification_advance_minutes), style = MaterialTheme.typography.labelLarge)
                            Spacer(modifier = Modifier.height(4.dp))
                            AdvanceMinutesDropdown(
                                selected = state.settings.notificationAdvanceMinutes,
                                onSelected = { viewModel.setNotificationAdvanceMinutes(it) }
                            )
                        }
                    }
                }

                // --- Admin: Bölüm Yetkileri ---
                if (state.user?.role == UserRole.ADMIN && state.departments.isNotEmpty()) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = stringResource(R.string.department_permissions),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.department_permissions_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            state.departments.forEach { dept ->
                                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                    Text("${dept.code} - ${dept.name}", style = MaterialTheme.typography.labelLarge)
                                    PermissionDropdown(
                                        selected = dept.deptHeadPermission,
                                        onSelected = { viewModel.updateDeptPermission(dept.id, it) }
                                    )
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }

                // --- Çıkış ---
                OutlinedButton(
                    onClick = { viewModel.logout(); onLogout() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, null)
                    Text(stringResource(R.string.logout), modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeDropdown(selected: AppTheme, onSelected: (AppTheme) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val labels = mapOf(
        AppTheme.SYSTEM to stringResource(R.string.theme_system),
        AppTheme.LIGHT to stringResource(R.string.theme_light),
        AppTheme.DARK to stringResource(R.string.theme_dark)
    )
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = labels[selected] ?: selected.name, onValueChange = {}, readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AppTheme.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(labels[option] ?: option.name) },
                    onClick = { onSelected(option); expanded = false }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageDropdown(selected: AppLanguage, onSelected: (AppLanguage) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val labels = mapOf(
        AppLanguage.SYSTEM to stringResource(R.string.theme_system),
        AppLanguage.TURKISH to stringResource(R.string.language_turkish),
        AppLanguage.ENGLISH to stringResource(R.string.language_english)
    )
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = labels[selected] ?: selected.name, onValueChange = {}, readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AppLanguage.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(labels[option] ?: option.name) },
                    onClick = { onSelected(option); expanded = false }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdvanceMinutesDropdown(selected: Int, onSelected: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(
        5 to stringResource(R.string.notification_advance_5),
        10 to stringResource(R.string.notification_advance_10),
        15 to stringResource(R.string.notification_advance_15),
        30 to stringResource(R.string.notification_advance_30)
    )
    val label = options.find { it.first == selected }?.second ?: "$selected dk"
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = label, onValueChange = {}, readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (minutes, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { onSelected(minutes); expanded = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PermissionDropdown(selected: DeptHeadPermission, onSelected: (DeptHeadPermission) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val labels = mapOf(
        DeptHeadPermission.FULL_ACCESS to stringResource(R.string.permission_full_access),
        DeptHeadPermission.APPROVAL_REQUIRED to stringResource(R.string.permission_approval_required),
        DeptHeadPermission.READ_ONLY to stringResource(R.string.permission_view_only)
    )
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = labels[selected] ?: selected.name, onValueChange = {}, readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DeptHeadPermission.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(labels[option] ?: option.name) },
                    onClick = { onSelected(option); expanded = false }
                )
            }
        }
    }
}
