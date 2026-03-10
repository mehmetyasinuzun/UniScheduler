package com.unischeduler.util

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

enum class LogLevel { DEBUG, INFO, WARN, ERROR }

data class LogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel,
    val tag: String,
    val message: String,
    val stackTrace: String? = null
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))

    val formattedDate: String
        get() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))

    override fun toString(): String {
        val trace = if (stackTrace != null) "\n$stackTrace" else ""
        return "${formattedDate} [${level.name}] $tag: $message$trace"
    }
}

object AppLogger {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val buffer = ConcurrentLinkedQueue<LogEntry>()
    private var logDir: File? = null
    private const val MAX_MEMORY_ENTRIES = 500
    private const val RETENTION_DAYS = 3

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    fun init(context: Context) {
        logDir = File(context.filesDir, "app_logs").also { it.mkdirs() }
        cleanOldLogs()
        loadTodayLogs()
    }

    fun d(tag: String, message: String) = log(LogLevel.DEBUG, tag, message)
    fun i(tag: String, message: String) = log(LogLevel.INFO, tag, message)
    fun w(tag: String, message: String) = log(LogLevel.WARN, tag, message)
    fun e(tag: String, message: String, throwable: Throwable? = null) =
        log(LogLevel.ERROR, tag, message, throwable?.stackTraceToString())

    private fun log(level: LogLevel, tag: String, message: String, stackTrace: String? = null) {
        val entry = LogEntry(
            level = level,
            tag = tag,
            message = message,
            stackTrace = stackTrace
        )
        buffer.add(entry)

        // Keep buffer bounded
        while (buffer.size > MAX_MEMORY_ENTRIES) buffer.poll()

        _logs.value = buffer.toList()

        // Write to file async
        scope.launch { writeToFile(entry) }
    }

    private fun writeToFile(entry: LogEntry) {
        val dir = logDir ?: return
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(entry.timestamp))
        val file = File(dir, "log_$dateStr.txt")
        file.appendText(entry.toString() + "\n")
    }

    private fun cleanOldLogs() {
        scope.launch {
            val dir = logDir ?: return@launch
            val cutoff = System.currentTimeMillis() - (RETENTION_DAYS * 24 * 60 * 60 * 1000L)
            dir.listFiles()?.forEach { file ->
                if (file.lastModified() < cutoff) {
                    file.delete()
                }
            }
        }
    }

    private fun loadTodayLogs() {
        scope.launch {
            val dir = logDir ?: return@launch
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val file = File(dir, "log_$dateStr.txt")
            if (file.exists()) {
                file.readLines().takeLast(MAX_MEMORY_ENTRIES).forEach { line ->
                    // Parse existing log lines for display
                    val entry = parseLogLine(line) ?: return@forEach
                    buffer.add(entry)
                }
                _logs.value = buffer.toList()
            }
        }
    }

    private fun parseLogLine(line: String): LogEntry? {
        if (line.isBlank()) return null
        return try {
            val levelMatch = Regex("\\[(DEBUG|INFO|WARN|ERROR)]").find(line)
            val level = levelMatch?.groupValues?.get(1)?.let { LogLevel.valueOf(it) } ?: LogLevel.INFO
            val afterLevel = line.substringAfter("] ", "")
            val tag = afterLevel.substringBefore(": ", "system")
            val message = afterLevel.substringAfter(": ", line)
            LogEntry(level = level, tag = tag, message = message)
        } catch (_: Exception) {
            LogEntry(level = LogLevel.INFO, tag = "system", message = line)
        }
    }

    fun getAllLogs(): List<LogEntry> = buffer.toList()

    fun getShareableLogs(maxEntries: Int = 300): String {
        val entries = buffer.toList().takeLast(maxEntries)
        if (entries.isEmpty()) return "[UniScheduler] Log kaydi bulunamadi"

        val header = buildString {
            append("UniScheduler Debug Logs\n")
            append("Entries: ${entries.size}\n")
            append("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n")
            append("----------------------------------------\n")
        }

        return header + entries.joinToString("\n") { it.toString() }
    }

    fun getLogsByLevel(level: LogLevel): List<LogEntry> = buffer.filter { it.level == level }

    fun getLogsByTag(tag: String): List<LogEntry> = buffer.filter { it.tag == tag }

    fun clearMemoryLogs() {
        buffer.clear()
        _logs.value = emptyList()
    }

    fun getLogFiles(): List<File> {
        val dir = logDir ?: return emptyList()
        return dir.listFiles()?.sortedByDescending { it.name }?.toList() ?: emptyList()
    }
}
