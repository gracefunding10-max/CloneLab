package com.example.clonelab.camera

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

enum class LogLevel {
    INFO,
    HOOK,
    FRAME,
    CADENCE,
    NDK,
    SECURITY,
    WARN,
    ERROR
}

data class LogEntry(
    val id: Long,
    val timestamp: Long,
    val timeFormatted: String,
    val level: LogLevel,
    val tag: String,
    val message: String
)

object CameraRedirectLog {

    private var counter = 0L
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private val buffer = CopyOnWriteArrayList<LogEntry>()
    private const val MAX_LOG_SIZE = 250

    private val _logsFlow = MutableStateFlow<List<LogEntry>>(emptyList())
    val logsFlow: StateFlow<List<LogEntry>> = _logsFlow.asStateFlow()

    @Synchronized
    fun log(level: LogLevel, tag: String, message: String) {
        val now = System.currentTimeMillis()
        val entry = LogEntry(
            id = ++counter,
            timestamp = now,
            timeFormatted = timeFormat.format(Date(now)),
            level = level,
            tag = tag,
            message = message
        )
        buffer.add(0, entry)
        if (buffer.size > MAX_LOG_SIZE) {
            buffer.removeAt(buffer.lastIndex)
        }
        _logsFlow.value = buffer.toList()
    }

    fun info(tag: String, message: String) = log(LogLevel.INFO, tag, message)
    fun hook(tag: String, message: String) = log(LogLevel.HOOK, tag, message)
    fun frame(tag: String, message: String) = log(LogLevel.FRAME, tag, message)
    fun cadence(tag: String, message: String) = log(LogLevel.CADENCE, tag, message)
    fun ndk(tag: String, message: String) = log(LogLevel.NDK, tag, message)
    fun security(tag: String, message: String) = log(LogLevel.SECURITY, tag, message)
    fun warn(tag: String, message: String) = log(LogLevel.WARN, tag, message)
    fun error(tag: String, message: String) = log(LogLevel.ERROR, tag, message)

    fun clear() {
        buffer.clear()
        _logsFlow.value = emptyList()
    }
}
