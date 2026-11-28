package com.furugit.app.ui.components

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.furugit.app.core.common.tr

fun EditText.onChange(block: (String) -> Unit) {
    addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) {
            block(s?.toString() ?: "")
        }
    })
}

/**
 * Navigasi halaman FuruArc (PageNav): tombol ‹ , teks "Halaman x / y" di tengah (ketuk untuk "Pergi ke halaman"), tombol ›.
 * [page] dimulai dari 0.
 */
fun LinearLayout.pageNav(page: Int, pages: Int, onPage: (Int) -> Unit) {
    val ctx = context
    val pal = Pal.of(ctx)
    val row = LinearLayout(ctx)
    row.orientation = LinearLayout.HORIZONTAL
    row.gravity = Gravity.CENTER_VERTICAL
    row.clipChildren = false
    val prev = row.button("‹", true) { onPage(page - 1) }
    prev.isEnabled = page > 0
    val label = TextView(ctx)
    label.text = tr("Halaman", "Page") + " " + (page + 1) + " / " + pages
    label.textSize = 13f
    label.typeface = Fonts.semibold(ctx)
    label.setTextColor(pal.text)
    label.gravity = Gravity.CENTER
    label.setSingleLine(true)
    label.setPadding(0, ctx.dp(10), 0, ctx.dp(10))
    label.isClickable = true
    val mask = GradientDrawable()
    mask.cornerRadius = ctx.dpf(8f)
    mask.setColor(Color.WHITE)
    label.background = RippleDrawable(ColorStateList.valueOf(pal.text.withAlpha(30)), null, mask)
    label.setOnClickListener {
        val box = vbox(ctx, 0)
        val et = box.input("1 – $pages", (page + 1).toString())
        et.inputType = InputType.TYPE_CLASS_NUMBER
        et.setSelection(et.text?.length ?: 0)
        Dialogs.show(
            ctx, tr("Pergi ke halaman", "Go to page"), null, box,
            listOf(
                DBtn(tr("Batal", "Cancel")),
                DBtn(tr("Pergi", "Go"), true, keepOpen = true) { dlg ->
                    val n = et.text.toString().toIntOrNull()
                    if (n != null) {
                        dlg.dismiss()
                        onPage((n - 1).coerceIn(0, pages - 1))
                    }
                }
            )
        )
    }
    row.addView(label, LinearLayout.LayoutParams(0, WRAP, 2f).apply { marginEnd = ctx.dp(8) })
    val next = row.button("›", true) { onPage(page + 1) }
    next.isEnabled = page < pages - 1
    (next.layoutParams as LinearLayout.LayoutParams).marginEnd = 0
    addView(row, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = gap(12) })
}

/**
 * Dropdown FuruArc (AppDropdown): kolom isi warna field, tepi 1dp, radius 10dp, teks 16sp + "▾", tinggi minimal 52dp;
 * menu muncul tepat di bawahnya selebar kolom, maksimal 5 pilihan terlihat (lebih dari itu bisa digulir).
 */
fun LinearLayout.dropdown(options: List<String>, selected: Int, onPick: (Int) -> Unit): View {
    val ctx = context
    val pal = Pal.of(ctx)
    val box = FrameLayout(ctx)
    val bg = GradientDrawable()
    bg.cornerRadius = ctx.dpf(10f)
    bg.setColor(pal.field)
    bg.setStroke(ctx.dp(1), pal.fieldBorder)
    val mask = GradientDrawable()
    mask.cornerRadius = ctx.dpf(10f)
    mask.setColor(Color.WHITE)
    box.background = RippleDrawable(ColorStateList.valueOf(pal.text.withAlpha(30)), bg, mask)
    box.isClickable = true
    val row = LinearLayout(ctx)
    row.orientation = LinearLayout.HORIZONTAL
    row.gravity = Gravity.CENTER_VERTICAL
    row.minimumHeight = ctx.dp(52)
    row.setPadding(ctx.dp(16), 0, ctx.dp(16), 0)
    val tv = TextView(ctx)
    tv.text = options.getOrElse(selected) { "-" }
    tv.textSize = 16f
    tv.typeface = Fonts.regular(ctx)
    tv.setTextColor(pal.text)
    tv.setSingleLine(true)
    tv.ellipsize = TextUtils.TruncateAt.END
    row.addView(tv, LinearLayout.LayoutParams(0, WRAP, 1f))
    val arrow = TextView(ctx)
    arrow.text = "▾"
    arrow.textSize = 16f
    arrow.setTextColor(pal.muted)
    row.addView(arrow, LinearLayout.LayoutParams(WRAP, WRAP))
    box.addView(row, FrameLayout.LayoutParams(MATCH, WRAP))
    box.pressScale(0.98f)
    box.setOnClickListener {
        val list = LinearLayout(ctx)
        list.orientation = LinearLayout.VERTICAL
        list.setPadding(0, ctx.dp(4), 0, ctx.dp(4))
        val pop = android.widget.PopupWindow(ctx)
        options.forEachIndexed { i, text ->
            val item = TextView(ctx)
            item.text = text
            item.textSize = 15f
            item.typeface = Fonts.regular(ctx)
            item.gravity = Gravity.CENTER_VERTICAL
            item.setSingleLine(true)
            item.ellipsize = TextUtils.TruncateAt.END
            item.setPadding(ctx.dp(16), 0, ctx.dp(16), 0)
            item.setTextColor(if (i == selected) pal.accent else pal.text)
            item.setOnClickListener {
                pop.dismiss()
                if (i != selected) onPick(i)
            }
            list.addView(item, LinearLayout.LayoutParams(MATCH, ctx.dp(48)))
        }
        val sv = android.widget.ScrollView(ctx)
        sv.addView(list)
        val pbg = GradientDrawable()
        pbg.cornerRadius = ctx.dpf(10f)
        pbg.setColor(pal.field)
        pbg.setStroke(ctx.dp(1), pal.fieldBorder)
        pop.contentView = sv
        pop.width = box.width
        pop.height = minOf(ctx.dp(48 * 5 + 8), ctx.dp(48 * options.size + 8))
        pop.setBackgroundDrawable(pbg)
        pop.isFocusable = true
        pop.isOutsideTouchable = true
        pop.elevation = ctx.dp(4).toFloat()
        pop.showAsDropDown(box, 0, ctx.dp(4))
    }
    addView(box, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = gap(10) })
    return box
}
