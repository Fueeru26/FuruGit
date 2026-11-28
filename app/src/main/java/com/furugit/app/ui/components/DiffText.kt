package com.furugit.app.ui.components

import android.graphics.Color
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan

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
