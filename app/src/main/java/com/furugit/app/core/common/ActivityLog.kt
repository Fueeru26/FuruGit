package com.furugit.app.core.common

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ActivityLog {
    private val lines = ArrayList<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    @Synchronized
    fun add(msg: String) {
        lines.add("[" + fmt.format(Date()) + "] " + msg)
        while (lines.size > 400) lines.removeAt(0)
    }

    @Synchronized
    fun all(): List<String> = ArrayList(lines)

    @Synchronized
    fun clear() {
        lines.clear()
    }
}
