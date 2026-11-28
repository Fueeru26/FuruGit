package com.furugit.app.ui.components

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.furugit.app.core.common.L

/**
 * Bilah navigasi kaca (GlassBar di FuruArc): kartu kaca setinggi 76dp melayang dengan jarak 12dp di sisi, pil di belakang
 * ikon tab yang dipilih (64 x 34dp, warnanya beranimasi 300 ms), label 12sp, efek tekan 0.9x.
 */
class GlassNav(
    context: Context,
    private val items: List<Item>,
    private val onSelect: (Int) -> Unit
) : FrameLayout(context) {

    class Item(val icon: Int, val label: String)

    private val pal = Pal.of(context)
    private val pills = ArrayList<GradientDrawable>()
    private val pillColors = ArrayList<Int>()
    private val pillAnims = HashMap<Int, ValueAnimator>()
    private val icons = ArrayList<ImageView>()
    private val labels = ArrayList<TextView>()
    private var selected = -1

    init {
        clipChildren = false
        clipToPadding = false
        setPadding(context.dp(12), context.dp(6), context.dp(12), context.dp(10))
        val bar = GlassCard(context, nav = true)
        val row = LinearLayout(context)
        row.orientation = LinearLayout.HORIZONTAL
        row.clipChildren = false
        row.setPadding(context.dp(8), 0, context.dp(8), 0)
        items.forEachIndexed { i, item ->
            val col = LinearLayout(context)
            col.orientation = LinearLayout.VERTICAL
            col.gravity = Gravity.CENTER
            col.clipChildren = false
            val pill = FrameLayout(context)
            val bg = GradientDrawable()
            bg.cornerRadius = context.dpf(10f)
            val clear = pal.navPill.withAlpha(0)
            bg.setColor(clear)
            pill.background = bg
            pills.add(bg)
            pillColors.add(clear)
            val iv = ImageView(context)
            iv.setImageResource(item.icon)
            icons.add(iv)
            pill.addView(iv, LayoutParams(context.dp(24), context.dp(24), Gravity.CENTER))
            col.addView(pill, LinearLayout.LayoutParams(context.dp(64), context.dp(34)))
            val tv = TextView(context)
            tv.text = item.label
            tv.textSize = 12f
            tv.gravity = Gravity.CENTER
            tv.maxLines = 1
            tv.ellipsize = TextUtils.TruncateAt.END
            tv.typeface = Fonts.regular(context)
            tv.setTextColor(pal.navUnsel)
            iv.imageTintList = ColorStateList.valueOf(pal.navUnsel)
            labels.add(tv)
            col.addView(tv, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = context.dp(3) })
            col.isClickable = true
            col.setOnClickListener { onSelect(i) }
            col.pressScale(0.9f)
            row.addView(col, LinearLayout.LayoutParams(0, MATCH, 1f))
        }
        bar.addView(row, LayoutParams(MATCH, MATCH))
        addView(bar, LayoutParams(MATCH, context.dp(76)))
    }

    private fun setPill(i: Int, target: Int, animate: Boolean) {
        pillAnims.remove(i)?.cancel()
        val bg = pills[i]
        if (!animate) {
            pillColors[i] = target
            bg.setColor(target)
            return
        }
        val a = ValueAnimator.ofObject(ArgbEvaluator(), pillColors[i], target)
        a.duration = 300L
        a.addUpdateListener {
            val c = it.animatedValue as Int
            pillColors[i] = c
            bg.setColor(c)
        }
        pillAnims[i] = a
        a.start()
    }

    /** Memilih tab [index]; [animate] = false untuk keadaan awal. */
    fun select(index: Int, animate: Boolean) {
        if (index == selected) return
        val old = selected
        selected = index
        for (i in items.indices) {
            val sel = i == index
            val tint = if (sel) pal.navSel else pal.navUnsel
            icons[i].imageTintList = ColorStateList.valueOf(tint)
            labels[i].setTextColor(tint)
            labels[i].typeface = if (sel) Fonts.bold(context) else Fonts.regular(context)
            if (i == index || i == old) {
                setPill(i, if (sel) pal.navPill else pal.navPill.withAlpha(0), animate && old >= 0)
            }
        }
    }
}
