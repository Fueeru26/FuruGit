package com.furugit.app.ui

import android.graphics.Color
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun colorizeDiff(text: String): CharSequence {
    val sb = SpannableStringBuilder()
    for (line in text.lines()) {
        val start = sb.length
        sb.append(line).append('\n')
        val color = when {
            line.startsWith("+++") || line.startsWith("---") -> null
            line.startsWith("+") -> Color.rgb(46, 125, 50)
            line.startsWith("-") -> Color.rgb(198, 40, 40)
            line.startsWith("@@") -> Color.rgb(21, 101, 192)
            else -> null
        }
        if (color != null) {
            sb.setSpan(ForegroundColorSpan(color), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
    return sb
}

fun fmtTime(ms: Long): String = SimpleDateFormat("d MMM yyyy HH:mm", Locale("id")).format(Date(ms))
