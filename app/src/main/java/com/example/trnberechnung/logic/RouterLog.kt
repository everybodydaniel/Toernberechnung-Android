package com.example.trnberechnung.logic

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object RouterLog {

    private const val MAX_ENTRIES = 300
    private val tsFmt = DateTimeFormatter.ofPattern("HH:mm:ss")

    private val _logs = MutableStateFlow<List<String>>(emptyList())

    val logs: StateFlow<List<String>> = _logs

    fun d(tag: String, msg: String) { Log.d(tag, msg); add('D', tag, msg) }
    fun i(tag: String, msg: String) { Log.i(tag, msg); add('I', tag, msg) }
    fun w(tag: String, msg: String) { Log.w(tag, msg); add('W', tag, msg) }
    fun w(tag: String, msg: String, t: Throwable) {
        Log.w(tag, msg, t)
        add('W', tag, "$msg :: ${t.javaClass.simpleName} ${t.message ?: ""}")
    }
    fun e(tag: String, msg: String) { Log.e(tag, msg); add('E', tag, msg) }

    fun clear() {
        _logs.value = emptyList()
    }

    private fun add(level: Char, tag: String, msg: String) {
        val ts = LocalDateTime.now().format(tsFmt)
        val line = "$ts $level/$tag: $msg"
        val current = _logs.value
        _logs.value = if (current.size >= MAX_ENTRIES) {
            current.drop(current.size - MAX_ENTRIES + 1) + line
        } else {
            current + line
        }
    }
}
