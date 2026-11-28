package com.furugit.app.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.net.Uri
import android.widget.LinearLayout
import android.widget.TextView
import com.furugit.app.R
import com.furugit.app.core.common.tr
import com.furugit.app.core.common.tx

/** Link berwarna aksen dan bergaris bawah; hanya teksnya yang bisa diketuk. */
fun LinearLayout.linkText(text: String, onClick: () -> Unit): TextView {
    val t = TextView(context)
    t.text = tx(text)
    t.textSize = 15f
    t.typeface = Fonts.bold(context)
    t.paintFlags = t.paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
    t.setTextColor(context.color(R.color.furu_accent))
    t.setPadding(0, context.dp(6), 0, context.dp(6))
    t.setOnClickListener { onClick() }
    addView(t, LinearLayout.LayoutParams(WRAP, WRAP))
    return t
}

fun Context.openLink(url: String) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Dialogs.toast(this, tr("Tidak bisa membuka tautan", "Cannot open the link"))
    }
}
