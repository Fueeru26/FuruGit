package com.furugit.app.ui.components

import android.content.Context
import android.view.ViewGroup

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT

const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

/** dp dalam bentuk Float (untuk radius, tebal garis, dll.). */
fun Context.dpf(v: Float): Float = v * resources.displayMetrics.density
