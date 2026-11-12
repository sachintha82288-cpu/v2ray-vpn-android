package com.v2rayvpn.app.core.log

import com.v2rayvpn.app.core.util.Formatters
import com.v2rayvpn.app.core.util.Redactor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class LogLevel { DEBUG, INFO, WARN, ERROR }

data class LogEntry(
    val timestamp: Long,
    val level: LogLevel,
    val message: String
) {
    fun format(): String = "${Formatters.time(timestamp)} ${level.name.padEnd(5)} $message"
}

/**
 * In-memory, redacted log ring buffer.
 *
 * Nothing is written to disk automatically: the user decides when to export a diagnostic file.
 * Every message is redacted before it is stored, so a shared log cannot contain credentials.
 */
object LogBus {

    private const val MAX_ENTRIES = 500

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    fun add(level: LogLevel, message: String) {
        val safe = Redactor.redact(message).trim()
        if (safe.isEmpty()) return
        val entry = LogEntry(System.currentTimeMillis(), level, safe)
        _entries.update { current ->
            val next = if (current.size >= MAX_ENTRIES) current.drop(current.size - MAX_ENTRIES + 1) else current
            next + entry
        }
    }

    fun d(message: String) = add(LogLevel.DEBUG, message)
    fun i(message: String) = add(LogLevel.INFO, message)
    fun w(message: String) = add(LogLevel.WARN, message)
    fun e(message: String) = add(LogLevel.ERROR, message)

    fun clear() {
        _entries.value = emptyList()
    }

    fun exportText(): String = buildString {
        appendLine("V2RAY VPN diagnostic log")
        appendLine("Exported: ${Formatters.time(System.currentTimeMillis())}")
        appendLine("Credentials are redacted. No browsing history is recorded.")
        appendLine()
        _entries.value.forEach { appendLine(it.format()) }
    }
}
