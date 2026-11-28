package com.furugit.app.ui.components

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.FrameLayout

/** Titik merah penanda pembaruan (BadgeRed di FuruArc). */
const val BADGE_RED = 0xFFFF2D2D.toInt()

fun Context.redDot(sizeDp: Int): View {
    val v = View(this)
    val bg = GradientDrawable()
    bg.shape = GradientDrawable.OVAL
    bg.setColor(BADGE_RED)
    v.background = bg
    v.layoutParams = FrameLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
    return v
}
