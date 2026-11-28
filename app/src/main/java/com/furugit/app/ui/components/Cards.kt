package com.furugit.app.ui.components

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.furugit.app.core.common.tx

/** Kartu kaca dengan isi kolom 16dp. [deco] = bunga kecil di sudut kanan bawah (kartu menu). */
private fun LinearLayout.glassCard(
    deco: Boolean,
    onClick: (() -> Unit)?,
    onLong: (() -> Unit)?,
    block: LinearLayout.() -> Unit
): LinearLayout {
    val c = GlassCard(context, deco)
    val inner = LinearLayout(context)
    inner.orientation = LinearLayout.VERTICAL
    inner.clipChildren = false
    inner.setPadding(context.dp(16), context.dp(16), context.dp(16), context.dp(16))
    inner.block()
    c.addView(inner, FrameLayout.LayoutParams(MATCH, WRAP))
    if (onClick != null) c.makeClickable(onClick)
    if (onLong != null) {
        c.isLongClickable = true
        c.setOnLongClickListener { onLong(); true }
    }
    addView(c, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = gap(16) })
    return inner
}

/** Kartu kaca gaya Blossom FuruArc (AppCard): radius 10dp, isi bergradien, tepi terang, cahaya biru. */
fun LinearLayout.card(
    onClick: (() -> Unit)? = null,
    onLong: (() -> Unit)? = null,
    block: LinearLayout.() -> Unit
): LinearLayout = glassCard(false, onClick, onLong, block)

/**
 * Kartu menu (BlossomMenuRow): kotak ikon bergradien 48dp, garis tipis, judul 16sp + keterangan 14sp,
 * dengan bunga kecil di sudut. Mengembalikan kolom teks.
 */
fun LinearLayout.rowCard(
    iconRes: Int,
    title: String,
    sub: String? = null,
    onClick: (() -> Unit)? = null,
    onLong: (() -> Unit)? = null,
    badge: Badge? = null,
    titleBadge: Badge? = null
): LinearLayout {
    var col: LinearLayout? = null
    glassCard(true, onClick, onLong) {
        val pal = Pal.of(context)
        val row = LinearLayout(context)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        val box = FrameLayout(context)
        val bg = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(pal.gradA, pal.gradB))
        bg.cornerRadius = context.dpf(8f)
        box.background = bg
        val iv = ImageView(context)
        iv.setImageResource(iconRes)
        iv.imageTintList = ColorStateList.valueOf(pal.onGrad)
        box.addView(iv, FrameLayout.LayoutParams(context.dp(24), context.dp(24), Gravity.CENTER))
        row.addView(box, LinearLayout.LayoutParams(context.dp(48), context.dp(48)))
        val divider = View(context)
        divider.setBackgroundColor(pal.divider)
        row.addView(divider, LinearLayout.LayoutParams(context.dp(1), context.dp(44)).apply {
            marginStart = context.dp(10)
            marginEnd = context.dp(10)
        })
        val c = LinearLayout(context)
        c.orientation = LinearLayout.VERTICAL
        row.addView(c, LinearLayout.LayoutParams(0, WRAP, 1f))
        if (titleBadge != null) {
            titleBadge.textView.textSize = 16f
            titleBadge.textView.lineH(24f)
            c.addView(titleBadge.view, LinearLayout.LayoutParams(MATCH, WRAP))
        } else {
            val t = TextView(context)
            t.text = tx(title)
            t.textSize = 16f
            t.lineH(24f)
            t.typeface = Fonts.semibold(context)
            t.setTextColor(pal.text)
            c.addView(t, LinearLayout.LayoutParams(MATCH, WRAP))
        }
        if (sub != null) {
            val s = TextView(context)
            s.text = tx(sub)
            s.textSize = 14f
            s.lineH(20f)
            s.typeface = Fonts.regular(context)
            s.setTextColor(pal.muted)
            c.addView(s, LinearLayout.LayoutParams(MATCH, WRAP))
        }
        if (badge != null) {
            // penanda di ujung kanan kartu
            row.addView(badge.view, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = context.dp(8) })
        }
        addView(row, LinearLayout.LayoutParams(MATCH, WRAP))
        col = c
    }
    return col!!
}

/** Satu kotak menu pada grid. [dot] diisi [tileGrid]: titik merah di pojok kanan atas ikon (disembunyikan sampai dibutuhkan). */
class MenuTile(val icon: Int, val label: String, val onClick: () -> Unit) {
    var dot: View? = null
}

/**
 * Grid kotak menu: [columns] kotak per baris, tiap kotak kartu kaca dengan kotak ikon bergradien di atas
 * dan label tanpa keterangan di bawahnya (maksimal 2 baris, rata atas, tinggi semua kotak sama).
 */
fun LinearLayout.tileGrid(tiles: List<MenuTile>, columns: Int = 3) {
    val ctx = context
    val pal = Pal.of(ctx)
    var row: LinearLayout? = null
    tiles.forEachIndexed { i, t ->
        if (i % columns == 0) {
            val r = LinearLayout(ctx)
            r.orientation = LinearLayout.HORIZONTAL
            r.clipChildren = false
            r.clipToPadding = false
            addView(r, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = gap(18) })
            row = r
        }
        val card = GlassCard(ctx, true)
        val inner = LinearLayout(ctx)
        inner.orientation = LinearLayout.VERTICAL
        inner.gravity = Gravity.CENTER_HORIZONTAL
        inner.clipChildren = false
        inner.setPadding(ctx.dp(6), ctx.dp(16), ctx.dp(6), ctx.dp(14))

        val box = FrameLayout(ctx)
        val bg = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(pal.gradA, pal.gradB))
        bg.cornerRadius = ctx.dpf(12f)
        box.background = bg
        val iv = ImageView(ctx)
        iv.setImageResource(t.icon)
        iv.imageTintList = ColorStateList.valueOf(pal.onGrad)
        box.addView(iv, FrameLayout.LayoutParams(ctx.dp(28), ctx.dp(28), Gravity.CENTER))
        val dot = ctx.redDot(10)
        (dot.layoutParams as FrameLayout.LayoutParams).apply {
            gravity = Gravity.TOP or Gravity.END
            topMargin = ctx.dp(4)
            marginEnd = ctx.dp(4)
        }
        dot.visibility = View.GONE
        box.addView(dot)
        t.dot = dot
        inner.addView(box, LinearLayout.LayoutParams(ctx.dp(52), ctx.dp(52)))

        val label = TextView(ctx)
        label.text = tx(t.label)
        label.textSize = 13f
        label.typeface = Fonts.semibold(ctx)
        label.setTextColor(pal.text)
        label.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        label.setLineSpacing(0f, 1.1f)
        label.minLines = 2
        label.maxLines = 2
        label.ellipsize = TextUtils.TruncateAt.END
        inner.addView(label, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = ctx.dp(12) })

        card.addView(inner, FrameLayout.LayoutParams(MATCH, MATCH))
        card.makeClickable(t.onClick)
        row!!.addView(card, LinearLayout.LayoutParams(0, MATCH, 1f).apply {
            if (i % columns != 0) marginStart = ctx.dp(14)
        })
    }
}
