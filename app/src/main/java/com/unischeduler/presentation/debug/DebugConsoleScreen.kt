package com.unischeduler.presentation.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unischeduler.util.AppLogger
import com.unischeduler.util.LogEntry
import com.unischeduler.util.LogLevel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugConsoleScreen() {
    val logs by AppLogger.logs.collectAsState()
    var selectedLevel by remember { mutableStateOf<LogLevel?>(null) }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    val filteredLogs = logs.filter { entry ->
        (selectedLevel == null || entry.level == selectedLevel) &&
        (selectedTag == null || entry.tag == selectedTag)
    }

    val uniqueTags = logs.map { it.tag }.distinct().sorted()

    // Auto-scroll to bottom when new logs arrive
    LaunchedEffect(filteredLogs.size) {
        if (filteredLogs.isNotEmpty()) {
            listState.animateScrollToItem(filteredLogs.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Debug Console", fontFamily = FontFamily.Monospace)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "(${filteredLogs.size})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (filteredLogs.isNotEmpty()) {
                            // Scroll to bottom
                        }
                    }) {
                        Icon(Icons.Filled.KeyboardArrowDown, "Aşağı kaydır")
                    }
                    IconButton(onClick = { AppLogger.clearMemoryLogs() }) {
                        Icon(Icons.Filled.Delete, "Logları temizle")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1A2E),
                    titleContentColor = Color(0xFF00FF41)
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0D1117))
                .padding(padding)
        ) {
            // Filter chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FilterChip(
                    selected = selectedLevel == null,
                    onClick = { selectedLevel = null },
                    label = { Text("Tümü", fontSize = 11.sp) }
                )
                LogLevel.entries.forEach { level ->
                    FilterChip(
                        selected = selectedLevel == level,
                        onClick = { selectedLevel = if (selectedLevel == level) null else level },
                        label = { Text(level.name, fontSize = 11.sp) }
                    )
                }
            }

            // Tag filter
            if (uniqueTags.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    FilterChip(
                        selected = selectedTag == null,
                        onClick = { selectedTag = null },
                        label = { Text("Tüm Modüller", fontSize = 11.sp) }
                    )
                    uniqueTags.forEach { tag ->
                        FilterChip(
                            selected = selectedTag == tag,
                            onClick = { selectedTag = if (selectedTag == tag) null else tag },
                            label = { Text(tag, fontSize = 11.sp) }
                        )
                    }
                }
            }

            // Log entries
            if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Henüz log kaydı yok",
                        color = Color(0xFF8B949E),
                        fontFamily = FontFamily.Monospace
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    items(filteredLogs, key = { "${it.timestamp}_${it.message.hashCode()}" }) { entry ->
                        LogEntryRow(entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun LogEntryRow(entry: LogEntry) {
    val bgColor = when (entry.level) {
        LogLevel.ERROR -> Color(0xFF2D1117)
        LogLevel.WARN -> Color(0xFF2D2217)
        else -> Color.Transparent
    }
    val levelColor = when (entry.level) {
        LogLevel.ERROR -> Color(0xFFFF6B6B)
        LogLevel.WARN -> Color(0xFFFFD93D)
        LogLevel.INFO -> Color(0xFF6BCB77)
        LogLevel.DEBUG -> Color(0xFF8B949E)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Time
        Text(
            text = entry.formattedTime,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFF8B949E)
        )

        Spacer(modifier = Modifier.width(6.dp))

        // Level indicator dot
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(levelColor)
        )

        Spacer(modifier = Modifier.width(6.dp))

        // Tag
        Text(
            text = entry.tag,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF58A6FF)
        )

        Spacer(modifier = Modifier.width(6.dp))

        // Message
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.message,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFFC9D1D9)
            )
            if (entry.stackTrace != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = entry.stackTrace,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFFFF6B6B),
                    maxLines = 5
                )
            }
        }
    }
}
