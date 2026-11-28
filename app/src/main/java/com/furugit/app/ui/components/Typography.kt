package com.furugit.app.ui.components

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.widget.TextViewCompat
import com.furugit.app.R

object Fonts {
    private val cache = HashMap<Int, Typeface>()

    private fun load(ctx: Context, id: Int): Typeface {
        cache[id]?.let { return it }
        val t = try {
            ResourcesCompat.getFont(ctx, id) ?: Typeface.DEFAULT
        } catch (e: Exception) {
            Typeface.DEFAULT
        }
        cache[id] = t
        return t
    }

    fun regular(ctx: Context) = load(ctx, R.font.qanelas_regular)
    fun semibold(ctx: Context) = load(ctx, R.font.qanelas_semibold)
    fun bold(ctx: Context) = load(ctx, R.font.qanelas_bold)
}

fun Context.color(id: Int): Int = Pal.resolve(this, id)

/** Tinggi baris teks dalam sp (lineHeight di Compose). */
fun TextView.lineH(sp: Float) {
    TextViewCompat.setLineHeight(this, TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics).toInt())
}
